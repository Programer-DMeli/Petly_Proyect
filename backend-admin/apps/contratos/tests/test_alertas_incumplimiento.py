from datetime import timedelta

from django.core.cache import cache
from django.utils import timezone
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, User
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.contratos.alertas_incumplimiento import generar_alertas_incumplimiento
from apps.contratos.models import (
    AlertaIncumplimientoSeguimiento,
    ExpedientePostAdopcion,
    ReporteBienestarPostAdopcion,
    SeguimientoPostAdopcion,
)


class AlertasIncumplimientoTest(APITestCase):
    seguimientos_url = "/api/contratos/seguimientos/"

    def setUp(self):
        cache.clear()
        self.adoptante = User.objects.create_user(username="adoptante")
        self.albergue_user = User.objects.create_user(username="albergue", rol="ALBERGUE")
        self.otro_albergue_user = User.objects.create_user(username="otro-albergue", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(
            usuario=self.albergue_user, nombre="Huellitas", direccion="Lima",
        )
        self.otro_albergue = Albergue.objects.create(
            usuario=self.otro_albergue_user, nombre="Otro hogar", direccion="Lima",
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre="Luna", codigo="ALERTA-1",
            especie="PERRO", sexo="HEMBRA",
        )
        self.solicitud = SolicitudAdopcion.objects.create(
            adoptante=self.adoptante, mascota=self.mascota, albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.ENTREGADA,
        )
        self.seguimiento = self.crear_seguimiento(self.solicitud)

    def tearDown(self):
        cache.clear()
        super().tearDown()

    @staticmethod
    def crear_seguimiento(solicitud, estado=SeguimientoPostAdopcion.Estado.PROGRAMADO, fecha=None):
        return SeguimientoPostAdopcion.objects.create(
            solicitud=solicitud,
            programado_para=fecha or timezone.now() - timedelta(hours=1),
            modalidad=SeguimientoPostAdopcion.Modalidad.VISITA,
            estado=estado,
        )

    def test_detecta_vencimientos_y_no_duplica_al_repetir_el_proceso(self):
        cancelado = self.crear_seguimiento(
            self.solicitud, estado=SeguimientoPostAdopcion.Estado.CANCELADO,
        )
        futuro = self.crear_seguimiento(
            self.solicitud, fecha=timezone.now() + timedelta(days=1),
        )

        creadas, resueltas = generar_alertas_incumplimiento()
        segunda_ejecucion = generar_alertas_incumplimiento()

        self.assertEqual((creadas, resueltas), (1, 0))
        self.assertEqual(segunda_ejecucion, (0, 0))
        self.assertEqual(AlertaIncumplimientoSeguimiento.objects.count(), 1)
        self.assertEqual(
            AlertaIncumplimientoSeguimiento.objects.get().seguimiento_id,
            self.seguimiento.pk,
        )
        self.assertFalse(AlertaIncumplimientoSeguimiento.objects.filter(
            seguimiento__in=(cancelado, futuro),
        ).exists())

    def test_reporte_o_cierre_resuelven_alerta_pendiente(self):
        alerta = AlertaIncumplimientoSeguimiento.objects.create(seguimiento=self.seguimiento)
        ReporteBienestarPostAdopcion.objects.create(
            seguimiento=self.seguimiento,
            estado_bienestar=ReporteBienestarPostAdopcion.EstadoBienestar.BUENO,
        )

        creadas, resueltas = generar_alertas_incumplimiento()

        alerta.refresh_from_db()
        self.assertEqual((creadas, resueltas), (0, 1))
        self.assertEqual(alerta.estado, AlertaIncumplimientoSeguimiento.Estado.RESUELTA)
        self.assertIsNotNone(alerta.resuelta_en)

    def test_cierre_del_expediente_no_genera_nueva_alerta(self):
        ExpedientePostAdopcion.objects.create(
            solicitud=self.solicitud,
            calificacion_adaptacion=4,
            cerrado_por=self.albergue_user,
        )

        self.assertEqual(generar_alertas_incumplimiento(), (0, 0))
        self.assertFalse(AlertaIncumplimientoSeguimiento.objects.exists())

    def test_panel_muestra_alerta_solo_al_albergue_responsable(self):
        self.client.force_authenticate(self.albergue_user)
        response = self.client.get(self.seguimientos_url)

        self.assertEqual(response.status_code, 200)
        rows = response.data["results"] if isinstance(response.data, dict) else response.data
        self.assertEqual(rows[0]["alerta_incumplimiento"]["estado"], "PENDIENTE")
        self.assertEqual(AlertaIncumplimientoSeguimiento.objects.count(), 1)

        self.client.force_authenticate(self.otro_albergue_user)
        otro_albergue = self.client.get(self.seguimientos_url)
        otra_lista = otro_albergue.data["results"] if isinstance(otro_albergue.data, dict) else otro_albergue.data
        self.assertEqual(otra_lista, [])

        self.client.force_authenticate(self.adoptante)
        adoptante = self.client.get(self.seguimientos_url)
        adoptante_rows = adoptante.data["results"] if isinstance(adoptante.data, dict) else adoptante.data
        self.assertIsNone(adoptante_rows[0]["alerta_incumplimiento"])
