from datetime import timedelta
from uuid import uuid4

import jwt
from django.conf import settings
from django.contrib.auth import get_user_model
from django.db import connection
from django.test import override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, VacunaProgramada
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota, RegistroClinico


@override_settings(CACHES={"default": {
    "BACKEND": "django.core.cache.backends.locmem.LocMemCache", "LOCATION": "us60-agenda-tests",
}})
class AgendaVacunasTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.titular = usuarios.create_user(username="agenda-titular", rol="ADOPTANTE")
        cls.otro_titular = usuarios.create_user(username="agenda-otro", rol="ADOPTANTE")
        cls.responsable = usuarios.create_user(username="agenda-refugio", rol="ALBERGUE")
        albergue = Albergue.objects.create(usuario=cls.responsable, nombre="Refugio", direccion="Lima")
        cls.hoy = timezone.localdate()

        def solicitud(nombre, codigo, adoptante):
            mascota = Mascota.objects.create(albergue=albergue, nombre=nombre, codigo=codigo, especie="GATO", sexo="HEMBRA")
            return SolicitudAdopcion.objects.create(
                mascota=mascota, albergue=albergue, adoptante=adoptante,
                estado=SolicitudAdopcion.Estado.ENTREGADA, fecha_entrega=cls.hoy,
            )

        cls.solicitud = solicitud("Nube", "VAC-060", cls.titular)
        cls.otra_mascota = solicitud("Luna", "VAC-061", cls.titular)
        cls.ajena = solicitud("Sol", "VAC-062", cls.otro_titular)

    def setUp(self):
        self.client.force_authenticate(self.titular)

    def lista(self, solicitud=None):
        return reverse("solicitud-vacunas-programadas", args=[(solicitud or self.solicitud).pk])

    def detalle(self, vacuna_id, solicitud=None):
        return reverse("solicitud-vacuna-programada", args=[(solicitud or self.solicitud).pk, vacuna_id])

    def datos(self, nombre="Refuerzo", dias=1):
        return {"nombre": nombre, "fecha_programada": (self.hoy + timedelta(days=dias)).isoformat()}

    def test_crear_ordenar_editar_y_eliminar_sin_cambiar_historial_clinico(self):
        aplicado = self.solicitud.mascota.registros_clinicos.create(
            tipo=RegistroClinico.Tipo.VACUNACION, fecha=self.hoy - timedelta(days=30),
        )
        self.assertEqual(self.client.get(self.lista()).data, {"mascota_nombre": "Nube", "vacunas": []})
        primera = self.client.post(self.lista(), self.datos("Refuerzo posterior", dias=7), format="json")
        segunda = self.client.post(self.lista(), self.datos("  Próxima vacuna  ", dias=1), format="json")
        self.assertEqual(primera.status_code, status.HTTP_201_CREATED, primera.data)
        self.assertEqual(segunda.status_code, status.HTTP_201_CREATED, segunda.data)
        self.assertEqual(segunda.data["nombre"], "Próxima vacuna")

        agenda = self.client.get(self.lista())
        self.assertEqual(agenda["Cache-Control"], "private, no-store")
        self.assertEqual(agenda.data["vacunas"], [segunda.data, primera.data])

        editada = self.client.patch(self.detalle(primera.data["id"]), self.datos("Fecha corregida", dias=0), format="json")
        self.assertEqual(editada.status_code, status.HTTP_200_OK, editada.data)
        self.assertEqual(editada.data["nombre"], "Fecha corregida")
        self.assertEqual(editada.data["fecha_programada"], self.hoy.isoformat())
        self.assertEqual(self.client.get(self.lista()).data["vacunas"], [editada.data, segunda.data])
        self.assertEqual(self.client.get(self.lista(self.otra_mascota)).data["vacunas"], [])

        borrado = self.client.delete(self.detalle(primera.data["id"]))
        self.assertEqual(borrado.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(self.client.get(self.lista()).data["vacunas"], [segunda.data])
        self.assertEqual(self.solicitud.mascota.registros_clinicos.count(), 1)
        self.assertTrue(RegistroClinico.objects.filter(pk=aplicado.pk, fecha=aplicado.fecha).exists())

    def test_rechaza_registros_y_adopciones_ajenas(self):
        vacuna_ajena = self.ajena.vacunas_programadas.create(**self.datos())
        otra_vacuna = self.otra_mascota.vacunas_programadas.create(**self.datos())
        for vacuna, solicitud in ((vacuna_ajena, self.ajena), (otra_vacuna, self.otra_mascota)):
            with self.subTest(vacuna=vacuna.pk):
                self.assertEqual(self.client.patch(self.detalle(vacuna.pk), self.datos(), format="json").status_code, 404)
                self.assertEqual(self.client.delete(self.detalle(vacuna.pk)).status_code, 404)
                self.assertTrue(solicitud.vacunas_programadas.filter(pk=vacuna.pk).exists())
        self.assertEqual(self.client.get(self.lista(self.ajena)).status_code, 404)
        self.assertEqual(self.client.post(self.lista(self.ajena), self.datos(), format="json").status_code, 404)
        self.assertEqual(self.client.patch(self.detalle(vacuna_ajena.pk, self.ajena), self.datos(), format="json").status_code, 404)
        self.assertEqual(self.client.delete(self.detalle(vacuna_ajena.pk, self.ajena)).status_code, 404)
        self.client.force_authenticate(self.responsable)
        self.assertEqual(self.client.get(self.lista()).status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(self.lista()).status_code, 401)

    def test_acepta_la_sesion_spring_activa_que_usa_android(self):
        usuario_spring_id = 6001
        version = 2
        sesion_id = str(uuid4())
        self.titular.usuario_spring_id = usuario_spring_id
        self.titular.save(update_fields=["usuario_spring_id"])
        with connection.cursor() as cursor:
            cursor.execute("""CREATE TABLE usuarios (
                id BIGINT PRIMARY KEY, token_version INT, cuenta_suspendida BOOLEAN, suspension_hasta DATETIME
            )""")
            cursor.execute("""CREATE TABLE sesiones_renovables (
                id VARCHAR(36), usuario_id BIGINT, token_version INT, revocada_en DATETIME
            )""")
            cursor.execute("INSERT INTO usuarios VALUES (%s, %s, FALSE, NULL)", [usuario_spring_id, version])
            cursor.execute("INSERT INTO sesiones_renovables VALUES (%s, %s, %s, NULL)", [
                sesion_id, usuario_spring_id, version,
            ])
        token = jwt.encode({
            "sid": sesion_id, "sub": str(usuario_spring_id), "version": version,
            "type": "access", "rol": "ADOPTANTE", "iss": "petly-spring", "aud": ["petly-django"],
            "exp": timezone.now() + timedelta(minutes=5),
        }, settings.SPRING_JWT_SECRET, algorithm="HS256")
        self.client.force_authenticate(None)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        nueva = self.client.post(self.lista(), self.datos(), format="json")
        self.assertEqual(nueva.status_code, 201, nueva.data)
        self.assertEqual(self.client.get(self.lista()).data["vacunas"], [nueva.data])
        with connection.cursor() as cursor:
            cursor.execute("UPDATE sesiones_renovables SET revocada_en = CURRENT_TIMESTAMP WHERE id = %s", [sesion_id])
        self.assertEqual(self.client.get(self.lista()).status_code, 401)

    def test_rechaza_adopciones_sin_entrega(self):
        vacuna = self.solicitud.vacunas_programadas.create(**self.datos())
        for estado in SolicitudAdopcion.Estado.values:
            if estado == SolicitudAdopcion.Estado.ENTREGADA:
                continue
            with self.subTest(estado=estado):
                SolicitudAdopcion.objects.filter(pk=self.solicitud.pk).update(estado=estado, fecha_entrega=None)
                self.assertEqual(self.client.get(self.lista()).status_code, 403)
                self.assertEqual(self.client.post(self.lista(), self.datos(), format="json").status_code, 403)
                self.assertEqual(self.client.patch(self.detalle(vacuna.pk), self.datos(), format="json").status_code, 403)
                self.assertEqual(self.client.delete(self.detalle(vacuna.pk)).status_code, 403)
        self.assertTrue(VacunaProgramada.objects.filter(pk=vacuna.pk).exists())

    def test_validacion_de_nombre_fecha_y_expiracion_de_una_fecha_existente(self):
        for datos, campo in (
            ({"fecha_programada": self.hoy.isoformat()}, "nombre"),
            (self.datos("   "), "nombre"),
            (self.datos("x" * 121), "nombre"),
            ({"nombre": "Refuerzo"}, "fecha_programada"),
            ({**self.datos(), "fecha_programada": ""}, "fecha_programada"),
            ({**self.datos(), "fecha_programada": "2027-02-30"}, "fecha_programada"),
            (self.datos(dias=-1), "fecha_programada"),
        ):
            with self.subTest(datos=datos):
                respuesta = self.client.post(self.lista(), datos, format="json")
                self.assertEqual(respuesta.status_code, 400)
                self.assertIn(campo, respuesta.data)
        self.assertFalse(self.solicitud.vacunas_programadas.exists())

        anterior = self.solicitud.vacunas_programadas.create(**self.datos(dias=-1))
        corregida = self.client.patch(self.detalle(anterior.pk), self.datos("Nombre corregido", dias=-1), format="json")
        self.assertEqual(corregida.status_code, 200, corregida.data)
        self.assertEqual(self.client.get(self.lista()).data["vacunas"], [corregida.data])
