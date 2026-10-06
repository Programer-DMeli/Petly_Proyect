"""Tests para modelos de mascotas."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota

User = get_user_model()


class MascotaModelTest(TestCase):
    """Tests del modelo Mascota."""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
        )

    def test_crear_mascota_valida(self):
        """Debe crear una mascota con campos obligatorios."""
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Firulais',
            codigo='PET-001',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            creado_por=self.user,
        )
        self.assertEqual(mascota.nombre, 'Firulais')
        self.assertEqual(mascota.codigo, 'PET-001')
        self.assertEqual(mascota.estado, Mascota.Estado.DISPONIBLE)
        self.assertEqual(mascota.albergue, self.albergue)
        self.assertEqual(mascota.creado_por, self.user)

    def test_codigo_unico(self):
        """El código debe ser único en todo el sistema."""
        Mascota.objects.create(
            albergue=self.albergue,
            nombre='Mascota 1',
            codigo='PET-001',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
        )
        with self.assertRaises(Exception):
            Mascota.objects.create(
                albergue=self.albergue,
                nombre='Mascota 2',
                codigo='PET-001',  # Duplicado
                especie=Mascota.Especie.GATO,
                sexo=Mascota.Sexo.HEMBRA,
            )

    def test_edad_texto(self):
        """Propiedad edad_texto debe formatear correctamente."""
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-002',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            edad_anos=2,
            edad_meses=3,
        )
        self.assertEqual(mascota.edad_texto, '2 años 3 meses')

    def test_edad_texto_solo_anos(self):
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-003',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            edad_anos=5,
        )
        self.assertEqual(mascota.edad_texto, '5 años')

    def test_edad_texto_solo_meses(self):
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-004',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            edad_meses=6,
        )
        self.assertEqual(mascota.edad_texto, '6 meses')

    def test_edad_texto_desconocida(self):
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-005',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
        )
        self.assertEqual(mascota.edad_texto, 'Desconocida')

    def test_get_valid_transitions_disponible(self):
        """DISPONIBLE puede ir a EN_PROCESO o ADOPTADO."""
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-006',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
        )
        transiciones = mascota.get_valid_transitions
        self.assertIn(Mascota.Estado.EN_PROCESO, transiciones)
        self.assertIn(Mascota.Estado.ADOPTADO, transiciones)
        self.assertEqual(len(transiciones), 2)

    def test_get_valid_transiciones_en_proceso(self):
        """EN_PROCESO puede ir a DISPONIBLE o ADOPTADO."""
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-007',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.EN_PROCESO,
        )
        transiciones = mascota.get_valid_transitions
        self.assertIn(Mascota.Estado.DISPONIBLE, transiciones)
        self.assertIn(Mascota.Estado.ADOPTADO, transiciones)

    def test_get_valid_transiciones_adoptado_terminal(self):
        """ADOPTADO no tiene transiciones válidas."""
        mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Test',
            codigo='PET-008',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.ADOPTADO,
        )
        self.assertEqual(mascota.get_valid_transitions, [])