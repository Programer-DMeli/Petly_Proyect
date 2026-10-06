"""Tests para modelos de albergues."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from apps.albergues.models import Albergue, Infraestructura

User = get_user_model()


class AlbergueModelTest(TestCase):
    """Tests del modelo Albergue."""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')

    def test_crear_albergue(self):
        """Debe crear un albergue válido."""
        albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
            telefono='987654321',
            email='test@test.com',
            capacidad_maxima=50,
        )
        self.assertEqual(albergue.nombre, 'Albergue Test')
        self.assertEqual(albergue.usuario, self.user)
        self.assertTrue(albergue.activo)
        self.assertIsNotNone(albergue.creado_en)

    def test_str_representation(self):
        """__str__ debe retornar el nombre."""
        albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Mi Albergue',
            direccion='Dirección',
        )
        self.assertEqual(str(albergue), 'Mi Albergue')

    def test_un_usuario_un_albergue(self):
        """Un usuario solo puede tener un albergue (OneToOne)."""
        Albergue.objects.create(usuario=self.user, nombre='A1', direccion='D1')
        with self.assertRaises(Exception):
            Albergue.objects.create(usuario=self.user, nombre='A2', direccion='D2')


class InfraestructuraModelTest(TestCase):
    """Tests del modelo Infraestructura."""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser2', password='testpass123')
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
        )

    def test_crear_infraestructura(self):
        """Debe crear infraestructura válida."""
        infra = Infraestructura.objects.create(
            albergue=self.albergue,
            tipo=Infraestructura.TipoArea.ZONA_CACHORROS,
            nombre='Canil A',
            metros_cuadrados=25.5,
            capacidad=10,
        )
        self.assertEqual(infra.albergue, self.albergue)
        self.assertEqual(infra.tipo, 'ZONA_CACHORROS')
        self.assertEqual(infra.get_tipo_display(), 'Zona de cachorros')

    def test_unique_together_albergue_tipo_nombre(self):
        """No puede haber dos áreas con mismo tipo y nombre en un albergue."""
        Infraestructura.objects.create(
            albergue=self.albergue,
            tipo=Infraestructura.TipoArea.ZONA_CACHORROS,
            nombre='Canil A',
        )
        with self.assertRaises(Exception):
            Infraestructura.objects.create(
                albergue=self.albergue,
                tipo=Infraestructura.TipoArea.ZONA_CACHORROS,
                nombre='Canil A',
            )

    def test_diferentes_albergues_mismo_nombre_ok(self):
        """Distintos albergues pueden tener áreas con mismo nombre."""
        user2 = User.objects.create_user(username='user2', password='pass')
        albergue2 = Albergue.objects.create(usuario=user2, nombre='A2', direccion='D2')
        
        Infraestructura.objects.create(
            albergue=self.albergue,
            tipo=Infraestructura.TipoArea.ZONA_CACHORROS,
            nombre='Canil A',
        )
        # No debe fallar
        infra2 = Infraestructura.objects.create(
            albergue=albergue2,
            tipo=Infraestructura.TipoArea.ZONA_CACHORROS,
            nombre='Canil A',
        )
        self.assertEqual(infra2.nombre, 'Canil A')