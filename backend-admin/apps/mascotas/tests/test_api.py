"""Tests de API para Mascotas."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota

User = get_user_model()


class MascotaAPITest(TestCase):
    """Tests de endpoints API de Mascotas."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123')
        self.other_user = User.objects.create_user(username='other', password='pass123')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True)
        
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Mi Albergue',
            direccion='Calle 123',
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Firulais',
            codigo='PET-001',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
            creado_por=self.user,
        )
        self.client = APIClient()

    def _auth(self, user):
        self.client.force_authenticate(user=user)

    def test_listar_mis_mascotas(self):
        """Usuario ve solo mascotas de su albergue (endpoint mis-mascotas con paginación)."""
        Mascota.objects.create(
            albergue=self.albergue, nombre='Otra', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
        )
        # Crear mascota de otro albergue
        albergue2 = Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        Mascota.objects.create(
            albergue=albergue2, nombre='Ajena', codigo='PET-003',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
        )
        
        self._auth(self.user)
        response = self.client.get('/api/mascotas/mis-mascotas/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        # response.data puede ser ReturnList (paginado) o dict con 'results'
        if hasattr(response.data, 'get'):
            data = response.data.get('results', response.data)
        else:
            data = response.data
        self.assertEqual(len(data), 2)
        codigos = [m['codigo'] for m in data]
        self.assertIn('PET-001', codigos)
        self.assertIn('PET-002', codigos)
        self.assertNotIn('PET-003', codigos)

    def test_crear_mascota_asigna_albergue(self):
        """Al crear, se asigna el albergue del usuario."""
        self._auth(self.user)
        data = {
            'nombre': 'Nueva', 'codigo': 'PET-010',
            'especie': 'GATO', 'sexo': 'HEMBRA',
            'edad_anos': 1,
        }
        response = self.client.post('/api/mascotas/', data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        # El serializer de lectura incluye el campo albergue
        self.assertIn('albergue', response.data)
        self.assertEqual(response.data['albergue'], self.albergue.id)

    def test_cambiar_estado_transicion_valida(self):
        """Cambio DISPONIBLE -> EN_PROCESO permitido."""
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': Mascota.Estado.EN_PROCESO}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['estado'], Mascota.Estado.EN_PROCESO)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.EN_PROCESO)

    def test_cambiar_estado_transicion_invalida(self):
        """Cambio DISPONIBLE -> (algo inválido) falla."""
        self.mascota.estado = Mascota.Estado.ADOPTADO
        self.mascota.save()
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': Mascota.Estado.DISPONIBLE}
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('error', response.data)

    def test_cambiar_estado_adoptado_fecha_adopcion(self):
        """Al pasar a ADOPTADO, se setea fecha_adopcion."""
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': Mascota.Estado.ADOPTADO}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsNotNone(response.data['fecha_adopcion'])
        self.mascota.refresh_from_db()
        self.assertIsNotNone(self.mascota.fecha_adopcion)

    def test_estadisticas_endpoint(self):
        """Endpoint estadísticas retorna conteos correctos."""
        Mascota.objects.create(
            albergue=self.albergue, nombre='M2', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.EN_PROCESO,
        )
        Mascota.objects.create(
            albergue=self.albergue, nombre='M3', codigo='PET-003',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.ADOPTADO,
        )
        
        self._auth(self.user)
        response = self.client.get('/api/mascotas/estadisticas/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['total'], 3)
        self.assertEqual(response.data['disponibles'], 1)
        self.assertEqual(response.data['en_proceso'], 1)
        self.assertEqual(response.data['adoptados'], 1)
        self.assertEqual(response.data['por_especie']['perros'], 2)
        self.assertEqual(response.data['por_especie']['gatos'], 1)

    def test_no_puede_ver_mascotas_ajenas(self):
        """Usuario no ve mascotas de otro albergue en detalle."""
        albergue2 = Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        mascota_ajena = Mascota.objects.create(
            albergue=albergue2, nombre='Ajena', codigo='PET-999',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
        )
        
        self._auth(self.user)
        response = self.client.get(f'/api/mascotas/{mascota_ajena.id}/')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)