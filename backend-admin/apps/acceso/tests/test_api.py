"""Tests de API para Acceso (Postulantes, Solicitudes)."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from django.utils import timezone
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.acceso.models import Postulante, SolicitudAdopcion

User = get_user_model()


class PostulanteAPITest(TestCase):
    """Tests de endpoints API de Postulantes."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True)
        
        self.albergue = Albergue.objects.create(
            usuario=self.user, nombre='Mi Albergue', direccion='Calle 123',
        )
        self.postulante = Postulante.objects.create(
            documento_numero='12345678',
            nombres='Juan', apellidos='Pérez',
            email='juan@test.com', telefono='987654321',
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre='Firulais', codigo='PET-001',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
        )
        self.solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota,
            albergue=self.albergue, estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        self.client = APIClient()

    def _auth(self, user):
        self.client.force_authenticate(user=user)

    def test_listar_postulantes_con_solicitudes_en_albergue(self):
        """Solo ve postulantes que han solicitado en su albergue."""
        # Postulante sin solicitudes en este albergue
        Postulante.objects.create(documento_numero='87654321', nombres='Otro', apellidos='Usuario')
        
        self._auth(self.user)
        response = self.client.get('/api/acceso/postulantes/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]['documento_numero'], '12345678')

    def test_historial_endpoint(self):
        """Endpoint /historial/ retorna solicitudes agrupadas."""
        self._auth(self.user)
        response = self.client.get(f'/api/acceso/postulantes/{self.postulante.id}/historial/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('postulante', response.data)
        self.assertIn('resumen', response.data)
        self.assertIn('solicitudes', response.data)
        self.assertEqual(response.data['resumen']['total'], 1)
        self.assertEqual(response.data['resumen']['pendientes'], 1)


class SolicitudAdopcionAPITest(TestCase):
    """Tests de endpoints API de Solicitudes de Adopción."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True)
        
        self.albergue = Albergue.objects.create(
            usuario=self.user, nombre='Mi Albergue', direccion='Calle 123',
        )
        self.postulante = Postulante.objects.create(
            documento_numero='12345678', nombres='Juan', apellidos='Pérez',
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre='Firulais', codigo='PET-001',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
        )
        self.solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota,
            albergue=self.albergue, estado=SolicitudAdopcion.Estado.PENDIENTE,
            mensaje_postulante='Quiero adoptar',
        )
        self.client = APIClient()

    def _auth(self, user):
        self.client.force_authenticate(user=user)

    def test_listar_solicitudes_propias(self):
        """Usuario ve solo solicitudes de su albergue."""
        self._auth(self.user)
        response = self.client.get('/api/acceso/solicitudes/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 1)

    def test_cambiar_estado_pendiente_a_en_revision(self):
        """PENDIENTE -> EN_REVISION permitido."""
        self._auth(self.user)
        response = self.client.post(
            f'/api/acceso/solicitudes/{self.solicitud.id}/cambiar-estado/',
            {'estado': SolicitudAdopcion.Estado.EN_REVISION}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['estado'], SolicitudAdopcion.Estado.EN_REVISION)
        self.assertIsNotNone(response.data['fecha_revision'])
        self.assertEqual(response.data['revisado_por'], self.user.id)

    def test_cambiar_estado_en_revision_a_aprobada(self):
        """EN_REVISION -> APROBADA permitido."""
        self.solicitud.estado = SolicitudAdopcion.Estado.EN_REVISION
        self.solicitud.save()
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/acceso/solicitudes/{self.solicitud.id}/cambiar-estado/',
            {'estado': SolicitudAdopcion.Estado.APROBADA}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['estado'], SolicitudAdopcion.Estado.APROBADA)

    def test_cambiar_estado_aprobada_a_entregada_actualiza_mascota(self):
        """APROBADA -> ENTREGADA actualiza mascota a ADOPTADO."""
        self.solicitud.estado = SolicitudAdopcion.Estado.APROBADA
        self.solicitud.save()
        self.mascota.estado = Mascota.Estado.DISPONIBLE
        self.mascota.save()
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/acceso/solicitudes/{self.solicitud.id}/cambiar-estado/',
            {'estado': SolicitudAdopcion.Estado.ENTREGADA}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['estado'], SolicitudAdopcion.Estado.ENTREGADA)
        self.assertIsNotNone(response.data['fecha_entrega'])
        
        # Verificar mascota actualizada
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.ADOPTADO)
        self.assertIsNotNone(self.mascota.fecha_adopcion)

    def test_transicion_invalida_falla(self):
        """Transición no permitida retorna 400."""
        self.solicitud.estado = SolicitudAdopcion.Estado.ENTREGADA
        self.solicitud.save()
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/acceso/solicitudes/{self.solicitud.id}/cambiar-estado/',
            {'estado': SolicitudAdopcion.Estado.PENDIENTE}
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('error', response.data)

    def test_estadisticas_solicitudes(self):
        """Endpoint estadísticas retorna conteos."""
        SolicitudAdopcion.objects.create(
            postulante=self.postulante, mascota=self.mascota,
            albergue=self.albergue, estado=SolicitudAdopcion.Estado.APROBADA,
        )
        
        self._auth(self.user)
        response = self.client.get('/api/acceso/solicitudes/estadisticas/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['total'], 2)
        self.assertEqual(response.data['pendientes'], 1)
        self.assertEqual(response.data['aprobadas'], 1)

    def test_no_puede_cambiar_solicitud_ajena(self):
        """Usuario no puede cambiar solicitud de otro albergue."""
        other_user = User.objects.create_user(username='other', password='pass')
        albergue2 = Albergue.objects.create(usuario=other_user, nombre='A2', direccion='D2')
        mascota2 = Mascota.objects.create(
            albergue=albergue2, nombre='M2', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
        )
        postulante2 = Postulante.objects.create(
            documento_numero='87654321', nombres='Ana', apellidos='García',
        )
        solicitud_ajena = SolicitudAdopcion.objects.create(
            postulante=postulante2, mascota=mascota2,
            albergue=albergue2, estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/acceso/solicitudes/{solicitud_ajena.id}/cambiar-estado/',
            {'estado': SolicitudAdopcion.Estado.EN_REVISION}
        )
        # get_queryset filtra por albergue -> 404
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)