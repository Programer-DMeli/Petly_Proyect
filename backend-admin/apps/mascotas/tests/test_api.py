"""Tests de API para Mascotas."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota

from django.core.files.uploadedfile import SimpleUploadedFile
from PIL import Image
from io import BytesIO

def foto_prueba():
    out = BytesIO(); Image.new("RGB", (10,10)).save(out, format="PNG")
    return SimpleUploadedFile("mascota.png", out.getvalue(), content_type="image/png")

User = get_user_model()


class MascotaAPITest(TestCase):
    """Tests de endpoints API de Mascotas."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123', rol='ALBERGUE')
        self.other_user = User.objects.create_user(username='other', password='pass123', rol='ALBERGUE')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True, rol='ADMIN')
        
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
            'foto_principal': foto_prueba(),
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
            {'estado': 'INVALIDO'}
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        # DRF devuelve errores a nivel de campo para ChoiceField inválido
        self.assertIn('estado', response.data)

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

    def test_otro_albergue_no_puede_modificar_ni_eliminar_mascota(self):
        Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        self._auth(self.other_user)
        path = f'/api/mascotas/{self.mascota.id}/'
        self.assertEqual(
            self.client.patch(path, {'nombre': 'Tomada'}, format='json').status_code,
            status.HTTP_404_NOT_FOUND,
        )
        self.assertEqual(self.client.delete(path).status_code, status.HTTP_404_NOT_FOUND)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.nombre, 'Firulais')

    def test_foto_y_edad_invalidas_rechazadas(self):
        self._auth(self.user)
        payload = {"nombre": "Luna", "especie": "PERRO", "sexo": "HEMBRA", "edad_anos": 2}
        # La foto válida es obligatoria al crear y las edades respetan el rango.
        self.assertEqual(self.client.post('/api/mascotas/', payload).status_code, 400)
        payload["foto_principal"] = foto_prueba()
        self.assertEqual(self.client.post('/api/mascotas/', payload).status_code, 201)
        payload["foto_principal"] = SimpleUploadedFile("fake.png",b"not an image",content_type="image/png")
        self.assertEqual(self.client.post('/api/mascotas/',payload).status_code,400)
        payload["foto_principal"] = foto_prueba(); payload["edad_meses"] = 12
        self.assertEqual(self.client.post('/api/mascotas/',payload).status_code,400)

    def test_edad_total_y_filtros_invalidos(self):
        self._auth(self.user)
        self.mascota.edad_anos = 2
        self.mascota.edad_meses = 3
        self.mascota.save()
        r = self.client.get('/api/mascotas/?edad_min_meses=27&edad_max_meses=27')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data['count'], 1)
        self.assertEqual(self.client.get('/api/mascotas/?edad_max_meses=3').data['count'], 0)
        for query in ['edad_min_meses=x','edad_min_meses=-1','edad_min_meses=28&edad_max_meses=27']:
            self.assertEqual(self.client.get('/api/mascotas/?'+query).status_code, 400)

    def test_revertir_adoptado_limpia_fecha(self):
        import datetime
        self._auth(self.user)
        self.mascota.estado = Mascota.Estado.ADOPTADO
        self.mascota.fecha_adopcion = datetime.date.today()
        self.mascota.save()
        r = self.client.post(f'/api/mascotas/{self.mascota.id}/cambiar-estado/', {'estado':'DISPONIBLE'})
        self.assertEqual(r.status_code, 200)
        self.mascota.refresh_from_db()
        self.assertIsNone(self.mascota.fecha_adopcion)
        self.assertEqual(self.mascota.estado, 'DISPONIBLE')

    def test_edicion_multipart_limpia_edad(self):
        self._auth(self.user)
        self.mascota.edad_anos = 2
        self.mascota.save()
        response = self.client.patch(f'/api/mascotas/{self.mascota.id}/', {'edad_anos':'','edad_meses':'','nombre':'Actualizada'}, format='multipart')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['id'], self.mascota.id)
        self.assertIn('estado', response.data)
        self.mascota.refresh_from_db()
        self.assertIsNone(self.mascota.edad_anos)
        self.assertIsNone(self.mascota.edad_meses)
