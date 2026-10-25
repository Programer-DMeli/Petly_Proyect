"""Prueba de carrera real, ejecutada en bases con SELECT ... FOR UPDATE."""
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

from django.contrib.auth import get_user_model
from django.db import close_old_connections
from django.test import TransactionTestCase, skipUnlessDBFeature

from apps.acceso.models import AdoptanteProfile, Autorizacion, Postulante, SolicitudAdopcion, User
from apps.acceso.solicitudes import cambiar_estado_solicitud
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from rest_framework.exceptions import ValidationError


class AprobacionConcurrenteTest(TransactionTestCase):
    reset_sequences = True

    def setUp(self):
        user_model = get_user_model()
        self.shelter_user = user_model.objects.create_user(
            username="refugio-carrera", password="pass", rol=User.Rol.ALBERGUE
        )
        self.shelter = Albergue.objects.create(
            usuario=self.shelter_user, nombre="Refugio carrera", direccion="Lima"
        )
        self.pet = Mascota.objects.create(
            albergue=self.shelter, nombre="Luna", codigo="RACE-001",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.DISPONIBLE,
        )
        self.requests = []
        for index in range(2):
            document = f"9000000{index}"
            adopter = user_model.objects.create_user(
                username=f"adoptante-carrera-{index}", password="pass", rol=User.Rol.ADOPTANTE
            )
            Postulante.objects.create(
                documento_numero=document, nombres="Adoptante", apellidos=str(index)
            )
            AdoptanteProfile.objects.create(
                user=adopter, documento_numero=document,
                cuestionario_completado=True,
            )
            Autorizacion.objects.create(adoptante=adopter, albergue=self.shelter)
            self.requests.append(SolicitudAdopcion.objects.create(
                postulante=Postulante.objects.get(documento_numero=document),
                adoptante=adopter, mascota=self.pet, albergue=self.shelter,
            ).pk)

    @skipUnlessDBFeature("has_select_for_update")
    def test_solo_una_aprobacion_gana_la_carrera_por_la_misma_mascota(self):
        barrier = Barrier(2)

        def approve(request_id):
            close_old_connections()
            try:
                actor = get_user_model().objects.get(pk=self.shelter_user.pk)
                barrier.wait(timeout=10)
                try:
                    cambiar_estado_solicitud(
                        request_id, SolicitudAdopcion.Estado.APROBADA, actor
                    )
                    return "aprobada"
                except ValidationError:
                    return "rechazada"
            finally:
                close_old_connections()

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(approve, self.requests))

        self.assertCountEqual(results, ["aprobada", "rechazada"])
        self.assertEqual(
            SolicitudAdopcion.objects.filter(
                mascota=self.pet, estado=SolicitudAdopcion.Estado.APROBADA
            ).count(),
            1,
        )
        self.pet.refresh_from_db()
        self.assertEqual(self.pet.estado, Mascota.Estado.EN_PROCESO)
        self.assertEqual(
            SolicitudAdopcion.objects.filter(
                mascota=self.pet, eventos__tipo="APROBADA"
            ).distinct().count(),
            1,
        )

    @skipUnlessDBFeature("has_select_for_update")
    def test_aprobar_y_cancelar_simultaneamente_solo_confirma_una_transicion(self):
        request_id = self.requests[0]
        adopter_id = SolicitudAdopcion.objects.get(pk=request_id).adoptante_id
        barrier = Barrier(2)

        def transition(operation):
            close_old_connections()
            try:
                if operation == "aprobar":
                    actor = get_user_model().objects.get(pk=self.shelter_user.pk)
                    next_state = SolicitudAdopcion.Estado.APROBADA
                else:
                    actor = get_user_model().objects.get(pk=adopter_id)
                    next_state = SolicitudAdopcion.Estado.CANCELADA
                barrier.wait(timeout=10)
                try:
                    cambiar_estado_solicitud(request_id, next_state, actor)
                    return operation
                except ValidationError:
                    return "rechazada"
            finally:
                close_old_connections()

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(transition, ["aprobar", "cancelar"]))

        self.assertEqual(results.count("rechazada"), 1)
        self.assertEqual(sum(result in {"aprobar", "cancelar"} for result in results), 1)
        request = SolicitudAdopcion.objects.get(pk=request_id)
        self.assertIn(request.estado, [SolicitudAdopcion.Estado.APROBADA, SolicitudAdopcion.Estado.CANCELADA])
        self.assertEqual(request.eventos.count(), 1)
        self.pet.refresh_from_db()
        expected_pet_state = (
            Mascota.Estado.EN_PROCESO if request.estado == SolicitudAdopcion.Estado.APROBADA
            else Mascota.Estado.DISPONIBLE
        )
        self.assertEqual(self.pet.estado, expected_pet_state)
