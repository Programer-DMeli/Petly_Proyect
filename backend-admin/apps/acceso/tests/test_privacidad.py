from uuid import uuid4

from django.conf import settings
from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient


class PrivacidadInternaTest(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.usuario = get_user_model().objects.create_user(
            username="adoptante-pruebas",
            email="adoptante@example.test",
            usuario_spring_id=821,
            rol="ADOPTANTE",
        )
        self.credencial = {"HTTP_X_INTERNAL_SECRET": settings.INTERNAL_API_SECRET}

    def test_portabilidad_y_baja_confirmada_del_adoptante(self):
        ruta_exportar = "/api/acceso/interno/privacidad/exportacion/"
        ruta_baja = "/api/acceso/interno/privacidad/baja/"
        datos = {"usuario_spring_id": 821}
        exportacion = self.client.post(ruta_exportar, datos, format="json", **self.credencial)
        self.assertEqual(exportacion.status_code, 200)
        self.assertEqual(exportacion.data["usuario"]["email"], "adoptante@example.test")
        self.assertNotIn("password", exportacion.data["usuario"])

        baja = {**datos, "operacion_id": str(uuid4()), "confirmar": False}
        self.assertEqual(self.client.post(ruta_baja, baja, format="json", **self.credencial).status_code, 400)
        self.usuario.refresh_from_db()
        self.assertTrue(self.usuario.is_active)

        baja["confirmar"] = True
        for _ in range(2):
            respuesta = self.client.post(ruta_baja, baja, format="json", **self.credencial)
            self.assertEqual(respuesta.status_code, 200, respuesta.data)
            self.assertEqual(respuesta.data["estado"], "COMPLETADA")
        self.usuario.refresh_from_db()
        self.assertFalse(self.usuario.is_active)
        self.assertEqual(self.usuario.email, "")
        self.assertIsNone(self.usuario.usuario_spring_id)
