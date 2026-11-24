from datetime import date

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient

from apps.acceso.models import SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


User = get_user_model()
URL = "/api/albergues/mi-perfil/metricas/permanencia/"


class PermanenciaAlbergueApiTest(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(username="metricas-owner", rol="ALBERGUE")
        self.other_owner = User.objects.create_user(username="metricas-other", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(
            usuario=self.owner, nombre="Refugio propio", direccion="Lima"
        )
        self.other_albergue = Albergue.objects.create(
            usuario=self.other_owner, nombre="Otro refugio", direccion="Callao"
        )
        self.client = APIClient()
        self.client.force_authenticate(self.owner)

    def crear_mascota(self, albergue, codigo, ingreso):
        mascota = Mascota.objects.create(
            albergue=albergue,
            nombre=f"Mascota {codigo}",
            codigo=codigo,
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
        )
        Mascota.objects.filter(pk=mascota.pk).update(fecha_ingreso=ingreso)
        return mascota

    def crear_solicitud(self, albergue, mascota, estado, entrega):
        return SolicitudAdopcion.objects.create(
            albergue=albergue,
            mascota=mascota,
            estado=estado,
            fecha_entrega=entrega,
        )

    def test_calcula_promedio_de_entregas_unicas_y_descarta_fechas_no_validas(self):
        primera = self.crear_mascota(self.albergue, "MET-001", date(2026, 1, 1))
        segunda = self.crear_mascota(self.albergue, "MET-002", date(2026, 1, 1))
        sin_entrega = self.crear_mascota(self.albergue, "MET-003", date(2026, 1, 1))
        fecha_invalida = self.crear_mascota(self.albergue, "MET-004", date(2026, 1, 10))

        self.crear_solicitud(
            self.albergue, primera, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 1, 11)
        )  # 10 días
        self.crear_solicitud(
            self.albergue, primera, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 1, 13)
        )  # La misma mascota no se cuenta de nuevo.
        self.crear_solicitud(
            self.albergue, segunda, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 1, 22)
        )  # 21 días
        self.crear_solicitud(
            self.albergue, sin_entrega, SolicitudAdopcion.Estado.APROBADA, None
        )
        self.crear_solicitud(
            self.albergue, fecha_invalida, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 1, 9)
        )

        response = self.client.get(URL)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {"dias_promedio": 15.5, "mascotas_entregadas": 2})

    def test_sin_entregas_validas_devuelve_estado_vacio(self):
        mascota = self.crear_mascota(self.albergue, "MET-005", date(2026, 2, 1))
        self.crear_solicitud(
            self.albergue, mascota, SolicitudAdopcion.Estado.APROBADA, None
        )

        response = self.client.get(URL)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {"dias_promedio": None, "mascotas_entregadas": 0})

    def test_metricas_solo_incluyen_mascotas_y_entregas_del_albergue_autenticado(self):
        propia = self.crear_mascota(self.albergue, "MET-006", date(2026, 3, 1))
        ajena = self.crear_mascota(self.other_albergue, "MET-007", date(2026, 1, 1))
        self.crear_solicitud(
            self.albergue, propia, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 3, 11)
        )
        self.crear_solicitud(
            self.other_albergue, ajena, SolicitudAdopcion.Estado.ENTREGADA, date(2026, 4, 11)
        )

        response = self.client.get(URL)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, {"dias_promedio": 10.0, "mascotas_entregadas": 1})

    def test_requiere_un_albergue_autenticado_con_perfil(self):
        for user, expected_status in (
            (User.objects.create_user(username="metricas-adoptante", rol="ADOPTANTE"), 403),
            (User.objects.create_user(username="metricas-admin", rol="ADMIN"), 403),
            (None, 401),
        ):
            with self.subTest(user=user):
                self.client.force_authenticate(user)
                self.assertEqual(self.client.get(URL).status_code, expected_status)

        owner_sin_perfil = User.objects.create_user(
            username="metricas-sin-perfil", rol="ALBERGUE"
        )
        self.client.force_authenticate(owner_sin_perfil)
        self.assertEqual(self.client.get(URL).status_code, 404)
