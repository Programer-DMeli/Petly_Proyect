"""Tests de API para Albergues."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from apps.albergues.models import Albergue, Infraestructura

User = get_user_model()


class AlbergueAPITest(TestCase):
    """Tests de endpoints API de Albergue."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123')
        self.other_user = User.objects.create_user(username='other', password='pass123')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True)
        
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Mi Albergue',
            direccion='Calle 123',
            telefono='987654321',
            email='test@test.com',
            capacidad_maxima=50,
        )
        self.client = APIClient()

    def _auth(self, user):
        self.client.force_authenticate(user=user)

    def test_listar_albergues_solo_suyo(self):
        """Usuario normal ve solo su albergue (lista directa, sin paginación)."""
        self._auth(self.user)
        response = self.client.get('/api/albergues/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]['nombre'], 'Mi Albergue')

    def test_staff_ve_todos(self):
        """Staff ve todos los albergues."""
        Albergue.objects.create(usuario=self.other_user, nombre='Otro', direccion='Dir')
        self._auth(self.staff)
        response = self.client.get('/api/albergues/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 2)

    def test_crear_albergue_asigna_usuario(self):
        """Al crear, se asigna el usuario autenticado como dueño."""
        self._auth(self.other_user)
        data = {'nombre': 'Nuevo', 'direccion': 'Dir', 'capacidad_maxima': 30}
        response = self.client.post('/api/albergues/', data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        # El create ahora retorna el serializer de lectura con usuario_id
        self.assertIn('usuario_id', response.data)
        self.assertEqual(response.data['usuario_id'], self.other_user.id)

    def test_no_puede_crear_si_ya_tiene(self):
        """Usuario con albergue no puede crear otro (OneToOne constraint retorna 400)."""
        self._auth(self.user)
        data = {'nombre': 'Otro', 'direccion': 'Dir', 'capacidad_maxima': 30}
        response = self.client.post('/api/albergues/', data)
        # Debe fallar por constraint único - 400
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_mi_perfil_endpoint(self):
        """Endpoint /mi-perfil/ retorna el albergue del usuario."""
        self._auth(self.user)
        response = self.client.get('/api/albergues/mi-perfil/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['nombre'], 'Mi Albergue')

    def test_actualizar_propio_albergue_ok(self):
        """Dueño puede actualizar su albergue."""
        self._auth(self.user)
        response = self.client.patch(f'/api/albergues/{self.albergue.id}/', {'nombre': 'Actualizado'})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['nombre'], 'Actualizado')

    def test_no_puede_actualizar_ajeno_retorna_404(self):
        """No puede actualizar albergue de otro usuario (retorna 404 por seguridad)."""
        self._auth(self.other_user)
        response = self.client.patch(f'/api/albergues/{self.albergue.id}/', {'nombre': 'Hack'})
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_infraestructura_crud(self):
        """CRUD completo de infraestructura anidada."""
        self._auth(self.user)
        
        # Crear
        data = {'tipo': 'ZONA_CACHORROS', 'nombre': 'Canil A', 'capacidad': 10}
        response = self.client.post(f'/api/albergues/{self.albergue.id}/infraestructura/', data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        infra_id = response.data['id']
        
        # Listar
        response = self.client.get(f'/api/albergues/{self.albergue.id}/infraestructura/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 1)
        
        # Actualizar
        response = self.client.patch(f'/api/albergues/{self.albergue.id}/infraestructura/{infra_id}/', {'capacidad': 15})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['capacidad'], 15)
        
        # Eliminar
        response = self.client.delete(f'/api/albergues/{self.albergue.id}/infraestructura/{infra_id}/')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Verificar eliminado
        response = self.client.get(f'/api/albergues/{self.albergue.id}/infraestructura/')
        data = response.data if isinstance(response.data, list) else response.data.get('results', [])
        self.assertEqual(len(data), 0)