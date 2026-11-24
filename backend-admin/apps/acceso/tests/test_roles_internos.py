from django.conf import settings
from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient


class RolesInternosTest(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.usuario = get_user_model().objects.create_user(
            username="adoptante-pruebas",
            email="adoptante@example.test",
            usuario_spring_id=821,
            rol="ADOPTANTE",
        )
        self.credencial = {"HTTP_X_INTERNAL_SECRET": settings.INTERNAL_API_SECRET}

    def test_solo_el_servicio_autorizado_sincroniza_un_rol_valido(self):
        ruta = "/api/acceso/interno/roles/"
        datos = {"usuario_spring_id": 821, "correo": "adoptante@example.test", "rol": "ALBERGUE"}
        self.assertEqual(self.client.put(ruta, datos, format="json").status_code, 401)
        respuesta = self.client.put(ruta, {**datos, "rol": "INVALIDO"}, format="json", **self.credencial)
        self.assertEqual(respuesta.status_code, 400)
        self.usuario.refresh_from_db()
        self.assertEqual(self.usuario.rol, "ADOPTANTE")

        respuesta = self.client.put(ruta, datos, format="json", **self.credencial)
        self.assertEqual(respuesta.status_code, 200)
        self.usuario.refresh_from_db()
        self.assertEqual(self.usuario.rol, "ALBERGUE")
