import json
from datetime import timedelta
from unittest.mock import patch
from urllib.error import HTTPError

import jwt
from django.conf import settings
from django.core import mail
from django.db import connection, transaction
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase

from apps.acceso.dispositivos import huella
from apps.acceso.entregas import planificar, procesar_lote
from apps.acceso.models import AvisoExterno, DispositivoPush, EntregaExterna, SolicitudAdopcion, User
from apps.acceso.solicitudes import cambiar_estado_solicitud
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


@override_settings(
    EMAIL_BACKEND="django.core.mail.backends.locmem.EmailBackend",
    AVISOS_FCM_PROJECT="petly-test",
    AVISOS_WHATSAPP_URL="https://whatsapp.example.test/messages",
    AVISOS_WHATSAPP_TOKEN="sandbox",
)
class EntregasExternasTest(APITestCase):
    dispositivos = "/api/acceso/notificaciones/dispositivos/"
    clave_a = "a" * 64
    clave_b = "b" * 64

    @classmethod
    def setUpTestData(cls):
        # Esquema mínimo de las tablas que Flyway administra en la base compartida.
        with connection.cursor() as cursor:
            cursor.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY, correo VARCHAR(255), telefono VARCHAR(20), rol VARCHAR(20))")
            cursor.execute("CREATE TABLE preferencias_notificacion (usuario_id BIGINT PRIMARY KEY, email BOOLEAN, whatsapp BOOLEAN, push BOOLEAN)")
            cursor.execute(
                "INSERT INTO usuarios VALUES (%s, %s, %s, 'ADOPTANTE'), (%s, %s, %s, 'ADOPTANTE')",
                [901, "titular@example.test", "+51900111222", 902, "otra@example.test", "+51900333444"],
            )
        cls.titular = User.objects.create_user(username="titular", email="antiguo@example.test", usuario_spring_id=901)
        cls.otra = User.objects.create_user(username="otra", usuario_spring_id=902)
        cls.refugio = User.objects.create_user(username="refugio", rol=User.Rol.ALBERGUE)
        cls.albergue = Albergue.objects.create(usuario=cls.refugio, nombre="Huellitas", direccion="Lima")
        cls.mascota = Mascota.objects.create(
            albergue=cls.albergue, nombre="Luna", codigo="EXTERNO-01",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
        )
        cls.solicitud = SolicitudAdopcion.objects.create(adoptante=cls.titular, mascota=cls.mascota, albergue=cls.albergue)

    def autenticar(self, usuario=None):
        if usuario is None:
            self.client.credentials()
            return
        token = jwt.encode({
            "sub": str(usuario.usuario_spring_id),
            "rol": usuario.rol,
            "type": "access",
            "iss": "petly-spring",
            "aud": "petly-django",
            "exp": timezone.now() + timedelta(minutes=5),
        }, settings.SPRING_JWT_SECRET, algorithm="HS256")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def registrar(self, usuario, clave, token):
        self.autenticar(usuario)
        return self.client.put(self.dispositivos, {"clave": clave, "token": token}, format="json")

    def preferencias(self, email, whatsapp, push):
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM preferencias_notificacion")
            cursor.execute(
                "INSERT INTO preferencias_notificacion VALUES (%s, %s, %s, %s), (%s, %s, %s, %s)",
                [901, email, whatsapp, push, 902, not email, not whatsapp, not push],
            )

    def transicion(self):
        cambiar_estado_solicitud(self.solicitud.pk, "OBSERVADA", self.refugio, "Confirma tu horario")
        return AvisoExterno.objects.get()

    def test_canales_aislados_y_todos_apagados(self):
        self.assertNotEqual(self.titular.pk, self.titular.usuario_spring_id)
        self.assertEqual(self.registrar(self.titular, self.clave_a, "token-a").status_code, 204)
        self.assertEqual(self.registrar(self.otra, self.clave_b, "token-b").status_code, 204)
        for canal in ("email", "whatsapp", "push", None):
            with self.subTest(canal=canal):
                with transaction.atomic():
                    self.preferencias(canal == "email", canal == "whatsapp", canal == "push")
                    aviso = self.transicion()
                    mail.outbox = []
                    with patch("apps.acceso.entregas.build_opener") as gateway, patch(
                        "apps.acceso.entregas.credencial_fcm", return_value="test-fcm",
                    ):
                        gateway.return_value.open.return_value.__enter__.return_value.status = 202
                        procesar_lote()
                        procesar_lote()
                    aceptadas = list(EntregaExterna.objects.filter(estado="ACEPTADA").values_list("canal", flat=True))
                    self.assertEqual(aceptadas, [canal] if canal else [])
                    self.assertEqual(EntregaExterna.objects.filter(estado="OMITIDA").count(), 2 if canal else 3)
                    self.assertEqual(len(mail.outbox), int(canal == "email"))
                    if canal == "email":
                        self.assertEqual(mail.outbox[0].to, ["titular@example.test"])
                        self.assertIn(f"/adoptante/solicitudes/{self.solicitud.pk}", mail.outbox[0].body)
                    if canal in ("push", "whatsapp"):
                        gateway.return_value.open.assert_called_once()
                        request = gateway.return_value.open.call_args.args[0]
                        payload = json.loads(request.data)
                        if canal == "push":
                            self.assertEqual(payload["message"]["token"], "token-a")
                            self.assertEqual(payload["message"]["data"], {
                                "evento": str(aviso.evento_id), "vinculo": huella(self.clave_a),
                            })
                        else:
                            self.assertEqual(payload["to"], "51900111222")
                            self.assertEqual(payload["type"], "template")
                            parametros = payload["template"]["components"][0]["parameters"]
                            self.assertIn(f"/adoptante/solicitudes/{self.solicitud.pk}", parametros[1]["text"])
                            self.assertEqual(request.get_header("Authorization"), "Bearer sandbox")
                        self.assertTrue(request.get_header("Idempotency-key"))
                    else:
                        gateway.assert_not_called()
                    transaction.set_rollback(True)

    def test_rotacion_revocacion_y_titularidad_del_aviso(self):
        self.assertEqual(self.registrar(self.titular, self.clave_a, "token-a").status_code, 204)
        self.assertEqual(self.registrar(self.otra, self.clave_a, "token-b").status_code, 403)
        self.autenticar()
        self.assertEqual(self.client.post(self.dispositivos + "rotar/", {
            "clave": self.clave_a, "token": "nuevo",
        }, format="json").status_code, 204)
        self.assertEqual(DispositivoPush.objects.get().token, "nuevo")
        self.assertEqual(self.client.post(self.dispositivos + "revocar/", {"clave": self.clave_a}).status_code, 204)
        self.assertEqual(self.registrar(self.titular, self.clave_a, "tardio").status_code, 403)
        self.assertFalse(DispositivoPush.objects.exclude(token="").exists())
        aviso = self.transicion()
        ruta = f"/api/acceso/notificaciones/{aviso.evento_id}/leer/"
        self.autenticar(self.otra)
        self.assertEqual(self.client.post(ruta).status_code, 404)
        self.autenticar(self.titular)
        self.assertEqual(self.client.post(ruta).data["solicitud_id"], self.solicitud.pk)

    def test_fallo_reintentable_y_fallo_incierto_no_revierten_solicitud(self):
        self.preferencias(True, True, False)
        self.transicion()
        with patch("apps.acceso.entregas.enviar_json") as proveedor:
            proveedor.side_effect = [HTTPError("test", 429, "limit", {}, None), TimeoutError()]
            procesar_lote()
            entrega = EntregaExterna.objects.get(canal="whatsapp")
            self.assertEqual(entrega.estado, "REINTENTAR")
            EntregaExterna.objects.filter(pk=entrega.pk).update(proximo_intento=timezone.now() - timedelta(seconds=1))
            procesar_lote()
            procesar_lote()
            entrega.refresh_from_db()
            self.assertEqual(entrega.estado, "INCIERTA")
            self.assertEqual(entrega.resultado, "TimeoutError")
            self.assertEqual(proveedor.call_count, 2)
            self.assertEqual(proveedor.call_args_list[0].args[-1], proveedor.call_args_list[1].args[-1])
        self.solicitud.refresh_from_db()
        self.assertEqual(self.solicitud.estado, "OBSERVADA")
        self.assertEqual(len(mail.outbox), 1)

    def test_rollback_y_preferencia_cambiada_antes_del_envio(self):
        with transaction.atomic():
            self.transicion()
            transaction.set_rollback(True)
        self.assertFalse(AvisoExterno.objects.exists())
        self.preferencias(True, False, False)
        aviso = self.transicion()
        planificar(aviso.pk)
        self.preferencias(False, False, False)
        procesar_lote()
        self.assertFalse(mail.outbox)
        self.assertEqual(EntregaExterna.objects.get(canal="email").estado, "OMITIDA")
