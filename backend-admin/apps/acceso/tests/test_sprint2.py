"""Acceptance tests for recommendation data, requests, decisions and timelines."""
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class MascotaCompatibilityDataAPITest(TestCase):
    def setUp(self):
        user = get_user_model().objects.create_user(username="shelter", password="pass", rol="ALBERGUE")
        self.shelter = Albergue.objects.create(usuario=user, nombre="Hogar", direccion="Calle 1")
        self.available = Mascota.objects.create(
            albergue=self.shelter, nombre="Luna", codigo="LUNA01", especie="PERRO", sexo="HEMBRA",
            tamanio="MEDIANO", edad_anos=2, edad_meses=3, temperamento="SOCIABLE", energia="MEDIA", estado="DISPONIBLE",
        )
        Mascota.objects.create(albergue=self.shelter, nombre="Adoptado", codigo="ADOP01", especie="GATO", sexo="MACHO", estado="ADOPTADO")
        self.client = APIClient(); self.client.force_authenticate(user=user)

    def test_expone_atributos_disponibles_sin_calcular_puntaje(self):
        response = self.client.get("/api/mascotas/compatibilidad/")
        self.assertEqual(response.status_code, 200)
        rows = response.data["results"] if isinstance(response.data, dict) else response.data
        self.assertEqual([row["id"] for row in rows], [self.available.id])
        self.assertEqual(rows[0]["energia"], "MEDIA")
        self.assertNotIn("compatibilidad", rows[0])


class AdoptionRequestAPITest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.user = User.objects.create_user(username="adoptante", password="pass", email="ana@example.test")
        owner = User.objects.create_user(username="owner", password="pass", rol="ALBERGUE")
        self.shelter = Albergue.objects.create(usuario=owner, nombre="Hogar", direccion="Calle 2")
        self.pet = Mascota.objects.create(albergue=self.shelter, nombre="Luna", codigo="LUNA02", especie="PERRO", sexo="HEMBRA", estado="DISPONIBLE")
        self.profile = {"finalizado": True}
        self.authorizations = [{"albergueId": self.shelter.id}]
        def spring_response(path, token):
            return self.profile if path == "/api/perfil" else self.authorizations
        self.spring_patch = patch("apps.acceso.authentication.spring_get", side_effect=spring_response)
        self.spring_patch.start(); self.addCleanup(self.spring_patch.stop)
        self.client = APIClient(); self.client.force_authenticate(user=self.user, token="test-token")

    def payload(self, **changes):
        return {"mascota": self.pet.id, "mensaje_postulante": "Quiero adoptar", "respuestas_cuestionario": {}, **changes}

    def post(self, body=None):
        return self.client.post("/api/acceso/solicitudes/", body if body is not None else self.payload(), format="json")

    def test_crea_solicitud_pendiente_sin_reservar_mascota(self):
        response = self.post()
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["estado"], "PENDIENTE")
        self.assertEqual(response.data["adoptante"], self.user.id)
        self.assertEqual(response.data["albergue"], self.shelter.id)
        self.assertIsNone(response.data["postulante"])
        self.assertTrue(SolicitudEvento.objects.filter(solicitud_id=response.data["id"], tipo="SOLICITUD_CREADA").exists())
        self.pet.refresh_from_db(); self.assertEqual(self.pet.estado, "DISPONIBLE")

    def test_rechaza_duplicada_mascota_no_disponible_albergue_inactivo_perfil_o_sin_consentimiento(self):
        SolicitudAdopcion.objects.create(adoptante=self.user, mascota=self.pet, albergue=self.shelter)
        self.assertEqual(self.post().status_code, 400)
        SolicitudAdopcion.objects.filter(adoptante=self.user).delete()
        self.pet.estado = "EN_PROCESO"; self.pet.save(update_fields=["estado"])
        self.assertEqual(self.post().status_code, 400)
        self.pet.estado = "DISPONIBLE"; self.pet.save(update_fields=["estado"])
        self.shelter.activo = False; self.shelter.save(update_fields=["activo"])
        self.assertEqual(self.post().status_code, 400)
        self.shelter.activo = True; self.shelter.save(update_fields=["activo"])
        self.profile = {"finalizado": False}
        self.assertEqual(self.post().status_code, 400)
        self.profile = {"finalizado": True}; self.authorizations = []
        self.assertEqual(self.post().status_code, 400)

    def test_cliente_no_puede_asignar_solicitud_a_otra_persona(self):
        other = get_user_model().objects.create_user(username="other", password="pass")
        response = self.post(self.payload(adoptante=other.id, postulante=999))
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["adoptante"], self.user.id)

    def test_rechaza_referencias_invalidas_y_permite_reintento_tras_rechazo(self):
        self.assertEqual(self.post({}).status_code, 400)
        self.assertEqual(self.post(self.payload(mascota=99999)).status_code, 400)
        SolicitudAdopcion.objects.create(adoptante=self.user, mascota=self.pet, albergue=self.shelter, estado="RECHAZADA")
        self.assertEqual(self.post().status_code, 201)


class AdoptionDecisionTimelineAPITest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.owner = User.objects.create_user(username="hogar", password="pass", rol="ALBERGUE")
        self.shelter = Albergue.objects.create(usuario=self.owner, nombre="Hogar", direccion="Calle 3")
        self.adopter = User.objects.create_user(username="ana", password="pass", email="ana@example.test")
        self.pet = Mascota.objects.create(albergue=self.shelter, nombre="Nube", codigo="NUBE01", especie="PERRO", sexo="HEMBRA", estado="DISPONIBLE")
        self.request = SolicitudAdopcion.objects.create(adoptante=self.adopter, mascota=self.pet, albergue=self.shelter)
        self.client = APIClient(); self.client.force_authenticate(user=self.owner, token="shelter-token")

    def decide(self, state, reason=""):
        with patch("apps.acceso.authentication.spring_get", return_value={"finalizado": True}):
            return self.client.post(f"/api/acceso/solicitudes/{self.request.id}/cambiar-estado/", {"estado": state, "observaciones_revision": reason}, format="json")

    def test_observacion_y_rechazo_exigen_motivo_y_guardan_eventos(self):
        self.assertEqual(self.decide("OBSERVADA").status_code, 400)
        response = self.decide("OBSERVADA", "Coordinar visita")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertTrue(self.request.eventos.filter(tipo="OBSERVADA", descripcion="Coordinar visita").exists())
        self.assertEqual(self.decide("RECHAZADA").status_code, 400)

    def test_aprobacion_requiere_autorizacion_y_reserva_mascota(self):
        response = self.decide("APROBADA")
        self.assertEqual(response.status_code, 200, response.data)
        self.pet.refresh_from_db(); self.assertEqual(self.pet.estado, "EN_PROCESO")
        self.assertTrue(self.request.eventos.filter(tipo="APROBADA").exists())

    def test_aprobaciones_dobles_no_pueden_reservar_la_misma_mascota(self):
        other = get_user_model().objects.create_user(username="luis", password="pass")
        second = SolicitudAdopcion.objects.create(adoptante=other, mascota=self.pet, albergue=self.shelter)
        self.assertEqual(self.decide("APROBADA").status_code, 200)
        with patch("apps.acceso.authentication.spring_get", return_value={"finalizado": True}):
            response = self.client.post(f"/api/acceso/solicitudes/{second.id}/cambiar-estado/", {"estado": "APROBADA"}, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(SolicitudAdopcion.objects.filter(mascota=self.pet, estado="APROBADA").count(), 1)

    def test_adoptante_ajeno_no_consulta_seguimiento(self):
        other = get_user_model().objects.create_user(username="otro", password="pass")
        client = APIClient(); client.force_authenticate(user=other, token="other-token")
        self.assertEqual(client.get(f"/api/acceso/solicitudes/{self.request.id}/seguimiento/").status_code, 404)
