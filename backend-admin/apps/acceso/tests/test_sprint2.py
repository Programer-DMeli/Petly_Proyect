"""Pruebas de los contratos Django para US-01 y US-06."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient

from apps.acceso.models import Postulante, SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class CompatibilidadMascotaAPITest(TestCase):
    def setUp(self):
        User = get_user_model()
        user = User.objects.create_user(username="albergue", password="pass", rol="ALBERGUE")
        albergue = Albergue.objects.create(usuario=user, nombre="Hogar", direccion="Calle 1")
        self.disponible = Mascota.objects.create(
            albergue=albergue, nombre="Luna", codigo="LUNA01",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
            tamanio=Mascota.Tamanio.MEDIANO, edad_anos=2, edad_meses=3,
            temperamento=Mascota.Temperamento.SOCIABLE, energia="MEDIA",
            estado=Mascota.Estado.DISPONIBLE,
        )
        Mascota.objects.create(
            albergue=albergue, nombre="Adoptado", codigo="ADOP01",
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.ADOPTADO,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=user)

    def test_expone_atributos_y_solo_mascotas_disponibles(self):
        response = self.client.get("/api/mascotas/compatibilidad/")
        self.assertEqual(response.status_code, 200)
        result = response.data["results"] if isinstance(response.data, dict) else response.data
        self.assertEqual([item["id"] for item in result], [self.disponible.id])
        item = result[0]
        self.assertEqual(item["edad_meses"], 3)
        self.assertEqual(item["temperamento"], "SOCIABLE")
        self.assertEqual(item["energia"], "MEDIA")
        self.assertEqual(item["estado"], "DISPONIBLE")
        self.assertNotIn("compatibilidad", item)


class SolicitudAdopcionSprint2APITest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.user = User.objects.create_user(username="adoptante", password="pass")
        self.albergue = Albergue.objects.create(usuario=self.user, nombre="Hogar", direccion="Calle 1")
        self.postulante = Postulante.objects.create(
            documento_numero="12345678", nombres="Ana", apellidos="Pérez",
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre="Luna", codigo="LUNA02",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.DISPONIBLE,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)

    def _payload(self, **updates):
        payload = {"postulante": self.postulante.id, "mascota": self.mascota.id,
                   "mensaje_postulante": "Quiero adoptar", "respuestas_cuestionario": {}}
        payload.update(updates)
        return payload

    def test_crea_solicitud_pendiente_y_deduce_albergue(self):
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["estado"], SolicitudAdopcion.Estado.PENDIENTE)
        self.assertEqual(response.data["albergue"], self.albergue.id)
        self.assertEqual(SolicitudAdopcion.objects.count(), 1)

    def test_rechaza_solicitud_activa_duplicada(self):
        SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota, albergue=self.albergue,
        )
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("solicitud activa", str(response.data).lower())
        self.assertEqual(SolicitudAdopcion.objects.count(), 1)

    def test_rechaza_mascota_no_disponible(self):
        self.mascota.estado = Mascota.Estado.EN_PROCESO
        self.mascota.save(update_fields=["estado"])
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("no está disponible", str(response.data).lower())

    def test_rechaza_referencias_inexistentes_y_campos_faltantes(self):
        missing = self.client.post("/api/acceso/solicitudes/", {}, format="json")
        self.assertEqual(missing.status_code, 400)
        invalid = self.client.post("/api/acceso/solicitudes/", self._payload(mascota=99999), format="json")
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(SolicitudAdopcion.objects.count(), 0)

    def test_acepta_nueva_solicitud_tras_solicitud_rechazada(self):
        SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota, albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.RECHAZADA,
        )
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 201, response.data)
