from contextlib import closing
from datetime import datetime, timedelta, timezone as utc_timezone
from io import BytesIO
from tempfile import TemporaryDirectory
from unittest.mock import patch

from PIL import Image
from pypdf import PdfReader

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
        self.adoptante = User.objects.create_user(username="adoptante")
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

    def test_descarga_certificado_solo_del_expediente_cerrado_y_cierre_unico(self):
        actividad = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        expediente = f"/api/contratos/expedientes/{self.solicitud.pk}/"
        certificado = expediente + "certificado/"
        evaluar = {"calificacion_adaptacion": 4, "observaciones_cierre": "Bien adaptada"}
        ReporteBienestarPostAdopcion.objects.create(seguimiento=self.seguimiento, estado_bienestar="BUENO")

        self.client.force_authenticate(self.albergue_user)
        evaluacion = self.client.post(actividad + "evaluar-expediente/", evaluar)
        self.assertEqual(evaluacion.status_code, 200)
        self.assertEqual(self.client.get(certificado).status_code, 409)

        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.get(expediente).status_code, 404)
        self.assertEqual(self.client.post(actividad + "evaluar-expediente/", {"calificacion_adaptacion": 1}).status_code, 404)
        self.assertEqual(self.client.post(actividad + "cerrar-expediente/", {"confirmar": True}).status_code, 404)

        self.client.force_authenticate(self.albergue_user)
        primer_cierre = self.client.post(actividad + "cerrar-expediente/", {"confirmar": True})
        self.assertEqual(primer_cierre.status_code, 201)
        cerrado = primer_cierre.data["expediente_cierre"]
        self.assertEqual(cerrado["id"], evaluacion.data["id"])
        self.assertEqual(cerrado["calificacion_adaptacion"], 4)
        self.assertEqual(self.client.post(actividad + "evaluar-expediente/", evaluar).status_code, 409)
        self.assertEqual(self.client.post(actividad + "cerrar-expediente/", {"confirmar": True}).data, primer_cierre.data)
        self.assertEqual(self.client.get(expediente).data["evaluacion"], cerrado)

        for _ in range(2):
            with closing(self.client.get(certificado)) as descarga:
                self.assertEqual(descarga.status_code, 200)
                self.assertEqual(descarga["Content-Type"], "application/pdf")
                self.assertIn(f"certificado-seguimiento-{self.solicitud.pk}.pdf", descarga["Content-Disposition"])
                texto = "\n".join(p.extract_text() for p in PdfReader(BytesIO(b"".join(descarga.streaming_content))).pages)
                for dato in ("Luna", "Huellitas", "adoptante", f"#{self.solicitud.pk}", "4/5", "Bien adaptada"):
                    self.assertIn(dato, texto)

        for usuario in (self.adoptante, self.otro_albergue_user):
            self.client.force_authenticate(usuario)
            self.assertEqual(self.client.get(certificado).status_code, 404)
        self.assertEqual(ExpedientePostAdopcion.objects.filter(solicitud=self.solicitud).count(), 1)
        self.assertEqual(ExpedientePostAdopcion.objects.get(solicitud=self.solicitud).cerrado_por, self.albergue_user)

    @override_settings(INTERNAL_API_SECRET="solo-pruebas")
    def test_reseña_se_habilita_solo_tras_el_cierre_visible_para_el_adoptante(self):
        self.adoptante.usuario_spring_id = 321
        self.adoptante.save(update_fields=["usuario_spring_id"])
        actividad = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        historial = f"/api/acceso/solicitudes/{self.solicitud.pk}/historial-seguimiento/"
        elegibilidad = "/api/contratos/resenas/eligibilidad/"
        params = {"adoptante_id": 321, "albergue_id": self.albergue.pk}
        headers = {"HTTP_X_INTERNAL_SECRET": "solo-pruebas"}

        self.client.force_authenticate(self.adoptante)
        self.assertIsNone(self.client.get(historial).data["cerradoEn"])
        self.assertEqual(self.client.get(elegibilidad, params, **headers).status_code, 403)
        ReporteBienestarPostAdopcion.objects.create(seguimiento=self.seguimiento, estado_bienestar="BUENO")
        self.client.force_authenticate(self.albergue_user)
        self.assertEqual(self.client.post(actividad + "evaluar-expediente/", {
            "calificacion_adaptacion": 5,
        }).status_code, 200)
        self.assertEqual(self.client.get(elegibilidad, params, **headers).status_code, 403)
        cierre = self.client.post(actividad + "cerrar-expediente/", {"confirmar": True})
        self.assertEqual(cierre.status_code, 201)

        self.client.force_authenticate(self.adoptante)
        datos = self.client.get(historial).data
        self.assertEqual(datos["albergueId"], self.albergue.pk)
        self.assertEqual(datos["cerradoEn"], cierre.data["expediente_cierre"]["cerrado_en"])
        self.assertEqual(self.client.get(actividad).data["albergue_id"], self.albergue.pk)
        self.assertEqual(self.client.get(elegibilidad, params, **headers).data, {"elegible": True})
        self.assertEqual(self.client.get(
            elegibilidad, {**params, "albergue_id": self.otro_albergue.pk}, **headers,
        ).status_code, 403)

    @override_settings(INTERNAL_API_SECRET="solo-pruebas", RECORDATORIOS_ANTICIPACION_HORAS=72)
    def test_recordatorios_a_tres_y_un_dia_sin_duplicar_y_para_el_titular(self):
        self.adoptante.usuario_spring_id = 123
        self.adoptante.save(update_fields=["usuario_spring_id"])
        limite = datetime(2026, 12, 10, 17, tzinfo=utc_timezone.utc)
        proxima = self.crear_seguimiento(self.solicitud, fecha=limite)
        otra = self.crear_seguimiento(self.solicitud, fecha=limite)
        cancelada = self.crear_seguimiento(self.solicitud, fecha=limite)
        visita = self.crear_seguimiento(self.solicitud, fecha=limite)
        visita.requiere_reporte = False
        visita.save(update_fields=["requiere_reporte"])

        feed = "/api/contratos/reportes/feed-recordatorios/"
        self.assertEqual(self.client.post(feed).status_code, 401)
        self.client.force_authenticate(self.adoptante)
        bandeja = "/api/acceso/notificaciones/"

        def sincronizar(ahora):
            with patch("django.utils.timezone.now", return_value=ahora):
                for _ in range(2):
                    respuesta = self.client.post(feed, HTTP_X_INTERNAL_SECRET="solo-pruebas")
                    self.assertEqual(respuesta.status_code, 200)
                return self.client.get(bandeja).data["results"]

        self.assertEqual(sincronizar(limite - timedelta(days=3, seconds=1)), [])
        avisos = sincronizar(limite - timedelta(days=3))
        self.assertCountEqual([(a["seguimiento_id"], a["tipo"]) for a in avisos], [
            (proxima.pk, "RECORDATORIO_REPORTE"), (otra.pk, "RECORDATORIO_REPORTE"),
            (cancelada.pk, "RECORDATORIO_REPORTE"),
        ])
        self.assertTrue(all(a["mascota_nombre"] == "Luna" and a["fecha_limite"] for a in avisos))

        ReporteBienestarPostAdopcion.objects.create(seguimiento=proxima, estado_bienestar="BUENO")
        cancelada.estado = SeguimientoPostAdopcion.Estado.CANCELADO
        cancelada.save(update_fields=["estado"])
        tardia = self.crear_seguimiento(self.solicitud, fecha=limite)
        avisos = sincronizar(limite - timedelta(days=1))
        self.assertCountEqual([(a["seguimiento_id"], a["tipo"]) for a in avisos], [
            (otra.pk, "REPORTE_1_DIA"), (tardia.pk, "REPORTE_1_DIA"),
        ])
        self.assertFalse(SolicitudEvento.objects.filter(seguimiento=visita).exists())
        self.assertEqual(SolicitudEvento.objects.get(seguimiento=cancelada).tipo, "RECORDATORIO_REPORTE")
        self.assertEqual(SolicitudEvento.objects.filter(seguimiento=proxima).count(), 1)
        self.assertEqual(SolicitudEvento.objects.filter(seguimiento=otra).count(), 2)
        self.assertEqual(SolicitudEvento.objects.filter(seguimiento=tardia).count(), 1)
        self.assertEqual(sincronizar(limite), [])

        self.client.force_authenticate(User.objects.create_user(username="otro-titular"))
        self.assertEqual(sincronizar(limite - timedelta(days=1)), [])

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
