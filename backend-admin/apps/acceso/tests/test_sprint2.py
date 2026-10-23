"""Pruebas de los contratos Django para US-01 y US-06."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from apps.acceso.models import AdoptanteProfile, Autorizacion, Postulante, SolicitudAdopcion, SolicitudEvento
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class CompatibilidadMascotaAPITest(TestCase):
    def setUp(self):
        User = get_user_model()
        owner = User.objects.create_user(username="albergue", password="pass", rol="ALBERGUE")
        user = User.objects.create_user(username="adoptante", password="pass", rol="ADOPTANTE")
        self.perfil = AdoptanteProfile.objects.create(user=user, cuestionario_completado=True,
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
        inactivo = Albergue.objects.create(
            usuario=get_user_model().objects.create_user(
                username="albergue-inactivo", password="pass", rol="ALBERGUE"
            ),
            nombre="Hogar inactivo", direccion="Calle 2", activo=False,
        )
        Mascota.objects.create(
            albergue=inactivo, nombre="No disponible", codigo="INACTIVA01",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.DISPONIBLE,
        )
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
        self.assertEqual(item["compatibilidad"]["porcentaje"], 100)
        self.assertEqual(item["compatibilidad"]["cobertura"], 100)
        self.assertFalse(item["compatibilidad"]["datosInsuficientes"])
        self.assertEqual(item["compatibilidad"]["versionReglas"], "petly-compat-v1")

    def test_aplica_las_mismas_penalizaciones_de_convivencia_que_spring(self):
        self.perfil.tipo_vivienda = "Departamento"
        self.perfil.horas_fuera = 10
        self.perfil.tiene_ninos = True
        self.perfil.tiene_otras_mascotas = True
        self.perfil.save()
        self.disponible.tamanio = Mascota.Tamanio.GRANDE
        self.disponible.energia = "ALTA"
        self.disponible.temperamento = Mascota.Temperamento.TIMIDO
        self.disponible.save()

        response = self.client.get("/api/mascotas/compatibilidad/")

        items = response.data["results"] if isinstance(response.data, dict) else response.data
        score = items[0]["compatibilidad"]
        self.assertEqual(score["puntaje"], 46)
        self.assertEqual(score["porcentaje"], 46)
        self.assertEqual(score["criterios"]["convivencia"], 45)

    def test_datos_desconocidos_no_generan_compatibilidad_perfecta(self):
        self.disponible.tamanio = ""
        self.disponible.energia = "POR_EVALUAR"
        self.disponible.temperamento = Mascota.Temperamento.POR_EVALUAR
        self.disponible.save()

        response = self.client.get("/api/mascotas/compatibilidad/")

        items = response.data["results"] if isinstance(response.data, dict) else response.data
        score = items[0]["compatibilidad"]
        self.assertIsNone(score["puntaje"])
        self.assertTrue(score["datosInsuficientes"])
        self.assertEqual(score["cobertura"], 0)
        self.assertEqual(score["mensaje"], "Información insuficiente para calcular compatibilidad.")


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
            postulante=self.postulante, adoptante=self.user,
            mascota=self.mascota, albergue=self.albergue,
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

    def test_adoptante_cancela_solicitud_pendiente_y_persiste_fecha_evento_y_titular(self):
        solicitud = self._decision_request()
        path = f"/api/acceso/solicitudes/{solicitud.pk}/cancelar/"

        response = self.client.post(path)

        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["estado"], SolicitudAdopcion.Estado.CANCELADA)
        evento = SolicitudEvento.objects.get(
            solicitud=solicitud,
            tipo=SolicitudEvento.Tipo.CANCELADA,
        )
        self.assertEqual(evento.estado_anterior, SolicitudAdopcion.Estado.PENDIENTE)
        self.assertEqual(evento.estado_nuevo, SolicitudAdopcion.Estado.CANCELADA)
        self.assertEqual(evento.actor, self.user)
        self.assertIsNotNone(evento.ocurrido_en)

        duplicate = self.client.post(path)
        self.assertEqual(duplicate.status_code, 400)
        self.assertEqual(SolicitudEvento.objects.filter(
            solicitud=solicitud,
            tipo=SolicitudEvento.Tipo.CANCELADA,
        ).count(), 1)

    def test_adoptante_cancela_solicitud_observada_conservando_datos_de_revision(self):
        solicitud = self._decision_request()
        fecha_revision = timezone.now()
        solicitud.estado = SolicitudAdopcion.Estado.OBSERVADA
        solicitud.revisado_por = self.owner
        solicitud.fecha_revision = fecha_revision
        solicitud.observaciones_revision = "Coordinar una visita."
        solicitud.save(update_fields=[
            "estado", "revisado_por", "fecha_revision", "observaciones_revision",
        ])

        response = self.client.post(f"/api/acceso/solicitudes/{solicitud.pk}/cancelar/")

        self.assertEqual(response.status_code, 200, response.data)
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, SolicitudAdopcion.Estado.CANCELADA)
        self.assertEqual(solicitud.revisado_por, self.owner)
        self.assertEqual(solicitud.observaciones_revision, "Coordinar una visita.")
        self.assertTrue(solicitud.eventos.filter(
            tipo=SolicitudEvento.Tipo.CANCELADA,
            estado_anterior=SolicitudAdopcion.Estado.OBSERVADA,
        ).exists())

    def test_solo_el_adoptante_titular_puede_cancelar(self):
        solicitud = self._decision_request()
        otro_adoptante = get_user_model().objects.create_user(
            username="otro-adoptante",
            password="pass",
            rol="ADOPTANTE",
        )
        self.client.force_authenticate(user=otro_adoptante)

        response = self.client.post(f"/api/acceso/solicitudes/{solicitud.pk}/cancelar/")

        self.assertEqual(response.status_code, 404)
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, SolicitudAdopcion.Estado.PENDIENTE)

    def test_albergue_no_puede_cancelar_desde_el_endpoint_de_decisiones(self):
        solicitud = self._decision_request()
        self.client.force_authenticate(user=self.owner)

        response = self.client.post(
            f"/api/acceso/solicitudes/{solicitud.pk}/cambiar-estado/",
            {"estado": "CANCELADA"},
            format="json",
        )

        self.assertEqual(response.status_code, 400)
        solicitud.refresh_from_db()
        self.assertEqual(solicitud.estado, SolicitudAdopcion.Estado.PENDIENTE)
        self.assertFalse(solicitud.eventos.filter(
            tipo=SolicitudEvento.Tipo.CANCELADA,
        ).exists())

    def test_cancelacion_y_aprobacion_no_pueden_revertir_una_transicion_ya_confirmada(self):
        solicitud_cancelada = self._decision_request()
        cancel_response = self.client.post(
            f"/api/acceso/solicitudes/{solicitud_cancelada.pk}/cancelar/"
        )
        self.assertEqual(cancel_response.status_code, 200, cancel_response.data)

        self.client.force_authenticate(user=self.owner)
        approve_cancelled = self.client.post(
            f"/api/acceso/solicitudes/{solicitud_cancelada.pk}/cambiar-estado/",
            {"estado": "APROBADA"},
            format="json",
        )
        self.assertEqual(approve_cancelled.status_code, 400)

        solicitud_aprobada = self._decision_request()
        approve_response = self.client.post(
            f"/api/acceso/solicitudes/{solicitud_aprobada.pk}/cambiar-estado/",
            {"estado": "APROBADA"},
            format="json",
        )
        self.assertEqual(approve_response.status_code, 200, approve_response.data)
        self.client.force_authenticate(user=self.user)
        cancel_approved = self.client.post(
            f"/api/acceso/solicitudes/{solicitud_aprobada.pk}/cancelar/"
        )
        self.assertEqual(cancel_approved.status_code, 400)
