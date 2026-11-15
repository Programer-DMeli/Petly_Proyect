from datetime import timedelta
from io import BytesIO
from tempfile import TemporaryDirectory
from unittest.mock import patch

from PIL import Image

from django.core.cache import cache
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento, User
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.contratos.alertas_incumplimiento import generar_alertas_incumplimiento
from apps.contratos.models import (
    AlertaIncumplimientoSeguimiento,
    ExpedientePostAdopcion,
    FotoReporteBienestar,
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

    def test_evaluacion_exige_primer_reporte_y_cierre_exige_evaluacion_guardada(self):
        self.client.force_authenticate(self.albergue_user)
        url = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        evaluar = {"calificacion_adaptacion": 4, "observaciones_cierre": "Bien adaptada"}
        self.assertEqual(self.client.post(url + "evaluar-expediente/", evaluar).status_code, 409)
        self.assertEqual(self.client.post(url + "cerrar-expediente/", {"confirmar": True}).status_code, 409)

        ReporteBienestarPostAdopcion.objects.create(seguimiento=self.seguimiento, estado_bienestar="BUENO")
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.post(url + "evaluar-expediente/", evaluar).status_code, 404)
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.post(url + "evaluar-expediente/", evaluar).status_code, 403)

        self.client.force_authenticate(self.albergue_user)
        guardada = self.client.post(url + "evaluar-expediente/", evaluar)
        self.assertEqual(guardada.status_code, 200)
        self.assertIsNone(guardada.data["cerrado_en"])
        self.assertEqual(self.client.post(url + "cerrar-expediente/", {"confirmar": False}).status_code, 400)
        self.assertEqual(self.client.post(url + "cerrar-expediente/", {"confirmar": True}).status_code, 201)

        expediente = self.client.get(f"/api/contratos/expedientes/{self.solicitud.pk}/").data
        self.assertEqual(expediente["evaluacion"]["calificacion_adaptacion"], 4)
        self.assertIsNotNone(expediente["evaluacion"]["cerrado_en"])
        self.assertEqual(expediente["cronograma"][0]["estado_entrega"], "FUERA_DE_PLAZO")

    @override_settings(INTERNAL_API_SECRET="solo-pruebas", RECORDATORIOS_ANTICIPACION_HORAS=24)
    def test_recordatorios_solo_dentro_de_ventana_y_para_el_titular(self):
        self.adoptante.usuario_spring_id = 123
        self.adoptante.save(update_fields=["usuario_spring_id"])
        proxima = self.crear_seguimiento(self.solicitud, fecha=timezone.now() + timedelta(hours=3))
        self.crear_seguimiento(self.solicitud, fecha=timezone.now() + timedelta(days=2))
        visita = self.crear_seguimiento(self.solicitud, fecha=timezone.now() + timedelta(hours=3))
        visita.requiere_reporte = False
        visita.save(update_fields=["requiere_reporte"])

        feed = "/api/contratos/reportes/feed-recordatorios/"
        self.assertEqual(self.client.post(feed).status_code, 401)
        for _ in range(2):
            self.assertEqual(self.client.post(feed, HTTP_X_INTERNAL_SECRET="solo-pruebas").status_code, 200)
        self.assertEqual(SolicitudEvento.objects.get(tipo="RECORDATORIO_REPORTE").seguimiento_id, proxima.pk)

        self.client.force_authenticate(self.adoptante)
        bandeja = "/api/acceso/notificaciones/"
        aviso = self.client.get(bandeja).data["results"][0]
        self.assertEqual(aviso["seguimiento_id"], proxima.pk)
        self.assertEqual(aviso["mascota_nombre"], "Luna")
        self.assertIsNotNone(aviso["fecha_limite"])
        ReporteBienestarPostAdopcion.objects.create(seguimiento=proxima, estado_bienestar="BUENO")
        self.assertEqual(self.client.get(bandeja).data["results"], [])

    def test_emision_idempotente_y_reporte_tardio_con_foto_privada(self):
        url = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        futuro = self.crear_seguimiento(self.solicitud, fecha=timezone.now() + timedelta(days=1))
        otro_adoptante = User.objects.create_user(username="otro-adoptante")
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.post(url + "emitir-incumplimiento/").status_code, 404)
        self.client.force_authenticate(self.albergue_user)
        self.assertEqual(self.client.post(f"{self.seguimientos_url}{futuro.pk}/emitir-incumplimiento/").status_code, 409)
        for _ in range(2):
            self.assertEqual(self.client.post(url + "emitir-incumplimiento/").status_code, 200)
        self.assertEqual(SolicitudEvento.objects.filter(tipo="INCUMPLIMIENTO_REPORTE").count(), 1)

        self.client.force_authenticate(self.adoptante)
        aviso = self.client.get("/api/acceso/notificaciones/").data["results"][0]
        self.assertEqual(aviso["seguimiento_id"], self.seguimiento.pk)
        data = BytesIO()
        Image.new("RGB", (4, 4)).save(data, format="PNG")
        storage = FotoReporteBienestar._meta.get_field("archivo").storage
        with TemporaryDirectory() as directory, patch.object(storage, "location", directory):
            response = self.client.post("/api/contratos/reportes-seguimiento/", {
                "seguimiento_id": self.seguimiento.pk,
                "estado_bienestar": "BUENO",
                "fotos": SimpleUploadedFile("reporte.png", data.getvalue(), content_type="image/png"),
            }, format="multipart")
            self.assertEqual(response.status_code, 201, response.data)
            foto = response.data["fotos"][0]["descarga_url"]
            for user in (otro_adoptante, self.otro_albergue_user):
                self.client.force_authenticate(user)
                self.assertEqual(self.client.get(foto).status_code, 404)
            for user in (self.adoptante, self.albergue_user):
                self.client.force_authenticate(user)
                archivo = self.client.get(foto)
                self.assertEqual(archivo.status_code, 200)
                self.assertEqual(archivo["Cache-Control"], "private, no-store")
                self.assertEqual(b"".join(archivo.streaming_content), data.getvalue())
                archivo.close()

        seguimiento = self.client.get(url).data
        self.assertEqual(seguimiento["estado_entrega"], "FUERA_DE_PLAZO")
        self.assertEqual(seguimiento["alerta_incumplimiento"]["estado"], "RESUELTA")
        self.assertIsNotNone(seguimiento["alerta_incumplimiento"]["emitida_en"])

    def test_ocultar_entrega_es_personal_idempotente_y_conserva_el_reporte(self):
        reporte = ReporteBienestarPostAdopcion.objects.create(
            seguimiento=self.seguimiento, estado_bienestar="BUENO",
        )
        url = f"{self.seguimientos_url}{self.seguimiento.pk}/ocultar/"
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.post(url).status_code, 403)
        otro = User.objects.create_user(username="otro-titular")
        self.client.force_authenticate(otro)
        self.assertEqual(self.client.post(url).status_code, 404)
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.post(url).status_code, 204)
        self.assertEqual(self.client.post(url).status_code, 204)
        self.assertEqual(self.client.get(self.seguimientos_url).data["results"], [])

        self.client.force_authenticate(self.albergue_user)
        expediente = self.client.get(f"/api/contratos/expedientes/{self.solicitud.pk}/").data
        self.assertEqual(expediente["cronograma"][0]["reporte_bienestar"]["id"], reporte.pk)

    def test_programar_editar_cancelar_y_conservar_el_aviso_del_titular(self):
        self.seguimiento.delete()
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.get(self.seguimientos_url).data["results"], [])
        datos = {
            "solicitud_id": self.solicitud.pk,
            "modalidad": "LLAMADA",
            "programado_para": (timezone.now() + timedelta(days=1)).isoformat(),
            "requiere_reporte": False,
            "notas": "Llamada de adaptación.",
        }
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.post(self.seguimientos_url, datos).status_code, 400)
        self.client.force_authenticate(self.albergue_user)
        creada = self.client.post(self.seguimientos_url, datos)
        self.assertEqual(creada.status_code, 201, creada.data)
        seguimiento = creada.data["id"]
        detalle = f"{self.seguimientos_url}{seguimiento}/"
        avisos = "/api/acceso/notificaciones/"

        self.client.force_authenticate(self.adoptante)
        dato = self.client.get(avisos).data["results"][0]
        self.assertEqual(dato["tipo"], SolicitudEvento.Tipo.SEGUIMIENTO_PROGRAMADO)
        self.assertEqual(dato["seguimiento_id"], seguimiento)
        self.assertEqual(dato["mascota_nombre"], "Luna")
        self.assertEqual(dato["programado_para"], creada.data["programado_para"])
        self.assertEqual(self.client.get(detalle).status_code, 200)
        self.assertEqual(self.client.delete(detalle).status_code, 403)

        otro_adoptante = User.objects.create_user(username="adoptante-ajeno")
        self.client.force_authenticate(otro_adoptante)
        self.assertEqual(self.client.get(detalle).status_code, 404)
        self.assertEqual(self.client.get(avisos).data["results"], [])
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.get(detalle).status_code, 404)
        self.assertEqual(self.client.patch(detalle, {"notas": "Cambio ajeno"}).status_code, 404)
        self.assertEqual(self.client.delete(detalle).status_code, 404)

        self.client.force_authenticate(self.albergue_user)
        editada = self.client.patch(detalle, {
            "notas": "Horario actualizado.",
            "programado_para": (timezone.now() + timedelta(days=2)).isoformat(),
        })
        self.assertEqual(editada.status_code, 200)
        self.assertEqual(self.client.delete(detalle).status_code, 204)
        self.assertEqual(self.client.patch(detalle, {"notas": "Reabrir"}).status_code, 400)

        self.client.force_authenticate(self.adoptante)
        cancelada = self.client.get(detalle).data
        self.assertEqual(cancelada["notas"], "Horario actualizado.")
        self.assertEqual(cancelada["estado"], "CANCELADO")
        self.assertIsNotNone(cancelada["cancelado_en"])
        lista = self.client.get(avisos).data["results"]
        self.assertEqual(len(lista), 1)
        self.assertEqual(lista[0]["seguimiento_id"], seguimiento)
        self.assertEqual(lista[0]["programado_para"], editada.data["programado_para"])
