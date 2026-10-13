"""Tests de serializadores para Mascotas (US-11)."""
import io
from django.test import TestCase
from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.mascotas.serializers import MascotaCreateUpdateSerializer, validate_image_file

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

User = get_user_model()


class MascotaSerializerValidationTest(TestCase):
    """Tests de validación del serializador MascotaCreateUpdateSerializer."""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
        )

    def _make_image(self, fmt='JPEG', size_kb=10):
        """Crea una imagen real en memoria usando PIL."""
        if not HAS_PIL:
            self.skipTest("PIL/Pillow no instalado")
        img = Image.new('RGB', (100, 100), color='red')
        buf = io.BytesIO()
        img.save(buf, format=fmt)
        buf.seek(0)
        content = buf.read()
        # Si es muy pequeño, rellenar para llegar al tamaño deseado
        if len(content) < size_kb * 1024:
            content += b'x' * (size_kb * 1024 - len(content))
        name = f'test.{fmt.lower()}'
        content_type = f'image/{fmt.lower()}' if fmt != 'JPEG' else 'image/jpeg'
        return SimpleUploadedFile(name, content, content_type=content_type)

    def _jpeg_file(self, size_kb=10):
        return self._make_image('JPEG', size_kb)

    def _png_file(self, size_kb=10):
        return self._make_image('PNG', size_kb)

    def _gif_file(self):
        return self._make_image('GIF', 10)

    def _large_jpeg(self):
        return self._make_image('JPEG', 6 * 1024)  # 6 MB

    def test_serializer_acepta_jpeg_valido(self):
        """JPEG < 5 MB es válido."""
        data = {
            'nombre': 'Test',
            'codigo': 'PET-010',
            'especie': 'PERRO',
            'sexo': 'MACHO',
            'edad_anos': 1,
            'foto_principal': self._jpeg_file(100),  # 100 KB
        }
        serializer = MascotaCreateUpdateSerializer(data=data)
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_serializer_acepta_png_valido(self):
        """PNG < 5 MB es válido."""
        data = {
            'nombre': 'Test',
            'codigo': 'PET-011',
            'especie': 'GATO',
            'sexo': 'HEMBRA',
            'edad_anos': 1,
            'foto_principal': self._png_file(200),  # 200 KB
        }
        serializer = MascotaCreateUpdateSerializer(data=data)
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_serializer_rechaza_gif(self):
        """GIF no permitido."""
        data = {
            'nombre': 'Test',
            'codigo': 'PET-012',
            'especie': 'PERRO',
            'sexo': 'MACHO',
            'edad_anos': 1,
            'foto_principal': self._gif_file(),
        }
        serializer = MascotaCreateUpdateSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('foto_principal', serializer.errors)
        self.assertIn('JPEG', str(serializer.errors['foto_principal']))

    def test_serializer_rechaza_archivo_grande(self):
        """Archivo > 5 MB rechazado."""
        data = {
            'nombre': 'Test',
            'codigo': 'PET-013',
            'especie': 'PERRO',
            'sexo': 'MACHO',
            'edad_anos': 1,
            'foto_principal': self._large_jpeg(),
        }
        serializer = MascotaCreateUpdateSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('foto_principal', serializer.errors)
        self.assertIn('5 MB', str(serializer.errors['foto_principal']))

    def test_serializer_sin_foto_es_valido(self):
        """foto_principal es opcional en actualización."""
        mascota = Mascota.objects.create(
            albergue=Albergue.objects.create(usuario=User.objects.create_user(username='test'), nombre='A', direccion='D'),
            nombre='Existente', codigo='PET-014', especie='PERRO', sexo='MACHO', edad_anos=1,
        )
        data = {'nombre': 'Actualizado'}
        serializer = MascotaCreateUpdateSerializer(instance=mascota, data=data, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)


class ValidateImageFileTest(TestCase):
    """Tests unitarios del validador validate_image_file."""

    def test_jpeg_pequeno_pasa(self):
        f = SimpleUploadedFile('a.jpg', b'\xff\xd8\xff\xe0' + b'x' * 1000, content_type='image/jpeg')
        self.assertEqual(validate_image_file(f), f)

    def test_png_pequeno_pasa(self):
        f = SimpleUploadedFile('a.png', b'\x89PNG\r\n\x1a\n' + b'x' * 1000, content_type='image/png')
        self.assertEqual(validate_image_file(f), f)

    def test_gif_falla(self):
        f = SimpleUploadedFile('a.gif', b'GIF89a' + b'x' * 1000, content_type='image/gif')
        with self.assertRaises(Exception) as ctx:
            validate_image_file(f)
        self.assertIn('JPEG', str(ctx.exception))

    def test_mas_de_5mb_falla(self):
        f = SimpleUploadedFile('big.jpg', b'\xff\xd8\xff\xe0' + b'x' * (6 * 1024 * 1024), content_type='image/jpeg')
        with self.assertRaises(Exception) as ctx:
            validate_image_file(f)
        self.assertIn('5 MB', str(ctx.exception))

    def test_extension_jpg_sin_content_type_pasa(self):
        """Si no hay content_type, valida por extensión .jpg"""
        f = SimpleUploadedFile('test.jpg', b'\xff\xd8\xff\xe0' + b'x' * 1000)
        f.content_type = ''
        self.assertEqual(validate_image_file(f), f)

    def test_extension_png_sin_content_type_pasa(self):
        f = SimpleUploadedFile('test.png', b'\x89PNG\r\n\x1a\n' + b'x' * 1000)
        f.content_type = ''
        self.assertEqual(validate_image_file(f), f)

    def test_extension_invalida_sin_content_type_falla(self):
        f = SimpleUploadedFile('test.txt', b'texto', content_type='')
        with self.assertRaises(Exception) as ctx:
            validate_image_file(f)
        self.assertIn('JPEG', str(ctx.exception))