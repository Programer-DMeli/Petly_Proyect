import base64

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from rest_framework.test import APIClient

from apps.acceso.models import VerificacionKyc


class VerificacionKycProteccionTest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.adoptante = User.objects.create_user(username="kyc-adoptante", password="pass", rol="ADOPTANTE")
        self.otro = User.objects.create_user(username="kyc-otro", password="pass", rol="ADOPTANTE")
        self.client = APIClient()
        self.client.force_authenticate(user=self.adoptante)

    @staticmethod
    def imagen(nombre):
        png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/"
            "lV8AAAAASUVORK5CYII="
        )
        return SimpleUploadedFile(nombre, png, content_type="image/png")

    def test_no_se_pueden_reemplazar_documentos_tras_aprobacion_manual(self):
        record = VerificacionKyc.objects.create(
            adoptante=self.adoptante, dni="12345678", nombres="Ana", apellidos="Perez",
            estado=VerificacionKyc.Estado.APROBADO_MANUAL,
        )
        response = self.client.post("/api/acceso/verificaciones-kyc/", {
            "dni": "87654321", "nombres": "Ana", "apellidos": "Perez",
            "documento_anverso": self.imagen("anverso.png"),
            "documento_reverso": self.imagen("reverso.png"),
        }, format="multipart")
        self.assertEqual(response.status_code, 400)
        record.refresh_from_db()
        self.assertEqual(record.dni, "12345678")
        self.assertEqual(record.estado, VerificacionKyc.Estado.APROBADO_MANUAL)

    def test_no_se_pueden_reemplazar_documentos_tras_rechazo_manual(self):
        record = VerificacionKyc.objects.create(
            adoptante=self.adoptante, dni="12345678", nombres="Ana", apellidos="Perez",
            estado=VerificacionKyc.Estado.RECHAZADO_MANUAL,
        )
        response = self.client.post("/api/acceso/verificaciones-kyc/", {
            "dni": "87654321", "nombres": "Ana", "apellidos": "Perez",
            "documento_anverso": self.imagen("anverso.png"),
            "documento_reverso": self.imagen("reverso.png"),
        }, format="multipart")
        self.assertEqual(response.status_code, 400)
        record.refresh_from_db()
        self.assertEqual(record.dni, "12345678")
        self.assertEqual(record.estado, VerificacionKyc.Estado.RECHAZADO_MANUAL)

    def test_no_se_pueden_reemplazar_documentos_mientras_espera_revision(self):
        record = VerificacionKyc.objects.create(
            adoptante=self.adoptante, dni="12345678", nombres="Ana", apellidos="Perez",
            estado=VerificacionKyc.Estado.PENDIENTE_REVISION,
        )
        response = self.client.post("/api/acceso/verificaciones-kyc/", {
            "dni": "87654321", "nombres": "Ana", "apellidos": "Perez",
            "documento_anverso": self.imagen("anverso.png"),
            "documento_reverso": self.imagen("reverso.png"),
        }, format="multipart")
        self.assertEqual(response.status_code, 400)
        record.refresh_from_db()
        self.assertEqual(record.dni, "12345678")
        self.assertEqual(record.estado, VerificacionKyc.Estado.PENDIENTE_REVISION)

    def test_documentos_y_registros_no_se_exponen_a_otro_adoptante(self):
        record = VerificacionKyc.objects.create(
            adoptante=self.adoptante, dni="12345678", nombres="Ana", apellidos="Perez",
        )
        self.client.force_authenticate(user=self.otro)
        response = self.client.get("/api/acceso/verificaciones-kyc/")
        records = response.data["results"] if isinstance(response.data, dict) else response.data
        self.assertEqual(response.status_code, 200)
        self.assertEqual(records, [])
        document = self.client.get(f"/api/acceso/verificaciones-kyc/{record.pk}/documentos/anverso/")
        self.assertEqual(document.status_code, 404)

    def test_adoptante_con_is_staff_no_puede_listar_kyc_ajenos(self):
        self.adoptante.is_staff = True
        self.adoptante.save(update_fields=["is_staff"])
        VerificacionKyc.objects.create(
            adoptante=self.otro, dni="87654321", nombres="Luis", apellidos="Gomez",
            estado=VerificacionKyc.Estado.PENDIENTE_REVISION,
        )
        response = self.client.get("/api/acceso/verificaciones-kyc/")
        records = response.data["results"] if isinstance(response.data, dict) else response.data
        self.assertEqual(response.status_code, 200)
        self.assertEqual(records, [])

    def test_adoptante_con_is_staff_no_puede_resolver_revision(self):
        self.adoptante.is_staff = True
        self.adoptante.save(update_fields=["is_staff"])
        record = VerificacionKyc.objects.create(
            adoptante=self.otro, dni="87654321", nombres="Luis", apellidos="Gomez",
            estado=VerificacionKyc.Estado.PENDIENTE_REVISION,
        )
        response = self.client.post(
            f"/api/acceso/verificaciones-kyc/{record.pk}/revision/",
            {"decision": "APROBADO_MANUAL", "motivo": "Documento válido"},
            format="json",
        )
        self.assertEqual(response.status_code, 403)
        record.refresh_from_db()
        self.assertEqual(record.estado, VerificacionKyc.Estado.PENDIENTE_REVISION)

    def test_admin_puede_resolver_revision(self):
        admin = get_user_model().objects.create_user(
            username="kyc-admin", password="pass", rol="ADMIN",
        )
        record = VerificacionKyc.objects.create(
            adoptante=self.otro, dni="87654321", nombres="Luis", apellidos="Gomez",
            estado=VerificacionKyc.Estado.PENDIENTE_REVISION,
        )
        self.client.force_authenticate(user=admin)
        response = self.client.post(
            f"/api/acceso/verificaciones-kyc/{record.pk}/revision/",
            {"decision": "APROBADO_MANUAL", "motivo": "Documento válido"},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        record.refresh_from_db()
        self.assertEqual(record.estado, VerificacionKyc.Estado.APROBADO_MANUAL)
        self.assertEqual(record.revisado_por, admin)
        self.assertEqual(record.motivo_revision, "Documento válido")
        self.assertIsNotNone(record.fecha_revision)

    def test_admin_no_puede_revisar_su_propia_verificacion(self):
        admin = get_user_model().objects.create_user(
            username="kyc-admin-propio", password="pass", rol="ADMIN",
        )
        record = VerificacionKyc.objects.create(
            adoptante=admin, dni="87654321", nombres="Luis", apellidos="Gomez",
            estado=VerificacionKyc.Estado.PENDIENTE_REVISION,
        )
        self.client.force_authenticate(user=admin)
        response = self.client.post(
            f"/api/acceso/verificaciones-kyc/{record.pk}/revision/",
            {"decision": "APROBADO_MANUAL", "motivo": "Autorevisión"},
            format="json",
        )
        self.assertEqual(response.status_code, 403)
        record.refresh_from_db()
        self.assertEqual(record.estado, VerificacionKyc.Estado.PENDIENTE_REVISION)
