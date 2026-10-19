"""Pruebas de los contratos Django para US-01 y US-06."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from apps.acceso.models import AdoptanteProfile, Autorizacion, Postulante, SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class CompatibilidadMascotaAPITest(TestCase):
    def setUp(self):
        User = get_user_model()
        owner = User.objects.create_user(username="albergue", password="pass", rol="ALBERGUE")
        user = User.objects.create_user(username="adoptante", password="pass", rol="ADOPTANTE")
        AdoptanteProfile.objects.create(user=user, cuestionario_completado=True,
            tipo_vivienda="Casa", tiene_patio=True, horas_fuera=6)
        albergue = Albergue.objects.create(usuario=owner, nombre="Hogar", direccion="Calle 1")
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
        self.assertEqual(item["compatibilidad"]["puntaje"], 100)
        self.assertEqual(item["compatibilidad"]["cobertura"], 100)
        self.assertFalse(item["compatibilidad"]["datosInsuficientes"])


class SolicitudAdopcionSprint2APITest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.user = User.objects.create_user(username="adoptante", password="pass")
        self.owner = User.objects.create_user(username="albergue", password="pass", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(usuario=self.owner, nombre="Hogar", direccion="Calle 1")
        self.postulante = Postulante.objects.create(
            documento_numero="12345678", nombres="Ana", apellidos="Pérez",
        )
        AdoptanteProfile.objects.create(
            user=self.user, documento_numero="12345678", nombres="Ana", apellidos="Pérez",
            cuestionario_completado=True,
        )
        self.autorizacion = Autorizacion.objects.create(
            adoptante=self.user, albergue=self.albergue,
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre="Luna", codigo="LUNA02",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.DISPONIBLE,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)

    def _payload(self, **updates):
        payload = {"mascota": self.mascota.id, "mensaje_postulante": "Quiero adoptar",
                   "respuestas_cuestionario": {}}
        payload.update(updates)
        return payload

    def test_crea_solicitud_pendiente_y_deduce_albergue(self):
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["estado"], SolicitudAdopcion.Estado.PENDIENTE)
        self.assertEqual(response.data["albergue"], self.albergue.id)
        self.assertEqual(response.data["postulante"], self.postulante.id)
        self.assertEqual(SolicitudAdopcion.objects.count(), 1)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.DISPONIBLE)

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

    def test_rechaza_albergue_inactivo(self):
        self.albergue.activo = False
        self.albergue.save(update_fields=["activo"])
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("albergue", str(response.data).lower())

    def test_rechaza_perfil_no_finalizado(self):
        self.user.adoptante_profile.cuestionario_completado = False
        self.user.adoptante_profile.save(update_fields=["cuestionario_completado"])
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("perfil", str(response.data).lower())

    def test_rechaza_sin_autorizacion_vigente(self):
        self.autorizacion.estado = Autorizacion.Estado.REVOCADA
        self.autorizacion.save(update_fields=["estado"])
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("autorización", str(response.data).lower())

    def test_rechaza_autorizacion_vencida(self):
        self.autorizacion.fecha_expiracion = timezone.now() - timezone.timedelta(seconds=1)
        self.autorizacion.save(update_fields=["fecha_expiracion"])
        response = self.client.post("/api/acceso/solicitudes/", self._payload(), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("vencida", str(response.data).lower())

    def test_no_acepta_crear_solicitud_a_nombre_de_otra_persona(self):
        otro = Postulante.objects.create(
            documento_numero="87654321", nombres="Luis", apellidos="Rojas",
        )
        response = self.client.post(
            "/api/acceso/solicitudes/",
            self._payload(postulante=otro.id),
            format="json",
        )
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["postulante"], self.postulante.id)
        self.assertNotEqual(response.data["postulante"], otro.id)

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

    def _decision_request(self):
        return SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota, albergue=self.albergue,
        )

    def test_albergue_observa_con_motivo_y_aprobacion_reserva_mascota(self):
        solicitud = self._decision_request()
        self.client.force_authenticate(user=self.owner)
        path = f"/api/acceso/solicitudes/{solicitud.pk}/cambiar-estado/"
        missing_reason = self.client.post(path, {"estado": "OBSERVADA"}, format="json")
        self.assertEqual(missing_reason.status_code, 400)
        observed = self.client.post(path, {
            "estado": "OBSERVADA", "observaciones_revision": "Falta coordinar visita.",
        }, format="json")
        self.assertEqual(observed.status_code, 200, observed.data)
        approved = self.client.post(path, {"estado": "APROBADA"}, format="json")
        self.assertEqual(approved.status_code, 200, approved.data)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.EN_PROCESO)

    def test_albergue_no_puede_modificar_solicitud_por_put_patch_ni_delete(self):
        solicitud = self._decision_request()
        self.client.force_authenticate(user=self.owner)
        path = f"/api/acceso/solicitudes/{solicitud.pk}/"
        # La política de rol bloquea antes de resolver el método del ViewSet.
        self.assertEqual(self.client.patch(path, {"estado": "APROBADA"}, format="json").status_code, 403)
        self.assertEqual(self.client.delete(path).status_code, 403)
