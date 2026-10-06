"""Tests para modelos de acceso (Postulante, SolicitudAdopcion)."""
from django.test import TestCase
from django.contrib.auth import get_user_model
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.acceso.models import Postulante, SolicitudAdopcion

User = get_user_model()


class PostulanteModelTest(TestCase):
    """Tests del modelo Postulante."""

    def setUp(self):
        self.user = User.objects.create_user(username='staff', password='pass')
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
        )

    def test_crear_postulante(self):
        """Debe crear postulante válido."""
        postulante = Postulante.objects.create(
            documento_numero='12345678',
            nombres='Juan',
            apellidos='Pérez',
            email='juan@test.com',
            telefono='987654321',
        )
        self.assertEqual(postulante.nombre_completo, 'Juan Pérez')
        self.assertEqual(postulante.estado, Postulante.EstadoPostulante.ACTIVO)

    def test_documento_unico(self):
        """Número de documento debe ser único."""
        Postulante.objects.create(
            documento_numero='12345678',
            nombres='Juan',
            apellidos='Pérez',
        )
        with self.assertRaises(Exception):
            Postulante.objects.create(
                documento_numero='12345678',  # Duplicado
                nombres='Otro',
                apellidos='Usuario',
            )


class SolicitudAdopcionModelTest(TestCase):
    """Tests del modelo SolicitudAdopcion y transiciones de estado."""

    def setUp(self):
        self.user = User.objects.create_user(username='staff', password='pass')
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Albergue Test',
            direccion='Calle 123',
        )
        self.postulante = Postulante.objects.create(
            documento_numero='12345678',
            nombres='Juan',
            apellidos='Pérez',
            email='juan@test.com',
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Firulais',
            codigo='PET-001',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
        )

    def test_crear_solicitud_pendiente(self):
        """Solicitud nueva debe ser PENDIENTE por defecto."""
        solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            mensaje_postulante='Quiero adoptar',
        )
        self.assertEqual(solicitud.estado, SolicitudAdopcion.Estado.PENDIENTE)
        self.assertEqual(solicitud.albergue, self.albergue)

    def test_get_valid_transitions_pendiente(self):
        """PENDIENTE -> EN_REVISION, RECHAZADA, CANCELADA."""
        solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        transiciones = solicitud.get_valid_transitions
        self.assertIn(SolicitudAdopcion.Estado.EN_REVISION, transiciones)
        self.assertIn(SolicitudAdopcion.Estado.RECHAZADA, transiciones)
        self.assertIn(SolicitudAdopcion.Estado.CANCELADA, transiciones)
        self.assertEqual(len(transiciones), 3)

    def test_get_valid_transitions_en_revision(self):
        """EN_REVISION -> APROBADA, RECHAZADA, PENDIENTE."""
        solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.EN_REVISION,
        )
        transiciones = solicitud.get_valid_transitions
        self.assertIn(SolicitudAdopcion.Estado.APROBADA, transiciones)
        self.assertIn(SolicitudAdopcion.Estado.RECHAZADA, transiciones)
        self.assertIn(SolicitudAdopcion.Estado.PENDIENTE, transiciones)

    def test_get_valid_transitions_aprobada(self):
        """APROBADA -> ENTREGADA, RECHAZADA."""
        solicitud = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.APROBADA,
        )
        transiciones = solicitud.get_valid_transitions
        self.assertIn(SolicitudAdopcion.Estado.ENTREGADA, transiciones)
        self.assertIn(SolicitudAdopcion.Estado.RECHAZADA, transiciones)

    def test_estados_terminales_sin_transiciones(self):
        """RECHAZADA, CANCELADA, ENTREGADA no tienen transiciones."""
        for estado in [
            SolicitudAdopcion.Estado.RECHAZADA,
            SolicitudAdopcion.Estado.CANCELADA,
            SolicitudAdopcion.Estado.ENTREGADA,
        ]:
            solicitud = SolicitudAdopcion.objects.create(
                postulante=self.postulante,
                mascota=self.mascota,
                albergue=self.albergue,
                estado=estado,
            )
            self.assertEqual(solicitud.get_valid_transitions, [], f"Estado {estado} debería ser terminal")

    def test_unique_pending_por_mascota_solo_bd_no_aplica_mariadb(self):
        """
        MariaDB no soporta constraints condicionales (UniqueConstraint con condition).
        Este test verifica que el modelo se crea correctamente.
        La validación de unicidad se hace a nivel de aplicación (API/Views).
        """
        # En MariaDB el constraint no se crea, así que esto NO falla a nivel BD
        # La validación real está en el serializer/vista
        s1 = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        s2 = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        # Ambos se crean en BD (constraint no aplica en MariaDB)
        self.assertIsNotNone(s1.pk)
        self.assertIsNotNone(s2.pk)

    def test_puede_tener_solicitud_aprobada_y_nueva_pendiente(self):
        """Si la anterior fue APROBADA, puede crear nueva PENDIENTE."""
        SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.APROBADA,
        )
        # Debe permitir crear nueva PENDIENTE
        solicitud2 = SolicitudAdopcion.objects.create(
            postulante=self.postulante,
            mascota=self.mascota,
            albergue=self.albergue,
            estado=SolicitudAdopcion.Estado.PENDIENTE,
        )
        self.assertEqual(solicitud2.estado, SolicitudAdopcion.Estado.PENDIENTE)