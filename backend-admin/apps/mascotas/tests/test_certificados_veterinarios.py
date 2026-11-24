from hashlib import sha256
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from django.urls import reverse
from pypdf import PdfWriter
from pypdf.generic import ArrayObject, ByteStringObject, DictionaryObject, NameObject, TextStringObject
from rest_framework import status
from rest_framework.test import APITestCase

from apps.albergues.models import Albergue
from apps.mascotas.certificados_veterinarios import CertificadoNoDisponible, abrir_pdf_integro
from apps.mascotas.models import CertificadoVeterinario, Mascota
from apps.mascotas.storage import CertificadoVeterinarioStorage


def pdf_de_prueba(con_marcador_falso=False):
    escritor = PdfWriter()
    escritor.add_blank_page(200, 200)
    if con_marcador_falso:
        campo = DictionaryObject({
            NameObject("/FT"): NameObject("/Sig"),
            NameObject("/T"): TextStringObject("Campo que no contiene una firma válida"),
            NameObject("/V"): DictionaryObject({NameObject("/Contents"): ByteStringObject(b"invalid placeholder")}),
        })
        escritor.root_object[NameObject("/AcroForm")] = DictionaryObject({
            NameObject("/Fields"): ArrayObject([escritor._add_object(campo)]),
        })
    buffer = BytesIO()
    escritor.write(buffer)
    return buffer.getvalue()


@override_settings(CACHES={"default": {
    "BACKEND": "django.core.cache.backends.locmem.LocMemCache",
    "LOCATION": "us59-certificados-tests",
}})
class CertificadosVeterinariosTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.propietario = usuarios.create_user(username="us59-refugio", rol="ALBERGUE")
        cls.otro_albergue = usuarios.create_user(username="us59-ajeno", rol="ALBERGUE")
        cls.adoptante = usuarios.create_user(username="us59-adoptante", rol="ADOPTANTE")
        albergue = Albergue.objects.create(usuario=cls.propietario, nombre="Refugio", direccion="Lima")
        Albergue.objects.create(usuario=cls.otro_albergue, nombre="Otro refugio", direccion="Lima")
        cls.mascota = Mascota.objects.create(
            albergue=albergue, nombre="Nube", codigo="VET-059", especie="GATO", sexo="HEMBRA",
            evidencia_vacunacion="mascotas/veterinaria/vacunacion/evidencia.png",
            evidencia_esterilizacion="mascotas/veterinaria/esterilizacion/evidencia.png",
        )
        cls.otra_mascota = Mascota.objects.create(
            albergue=albergue, nombre="Sol", codigo="VET-060", especie="GATO", sexo="HEMBRA",
        )

    def setUp(self):
        carpeta = TemporaryDirectory()
        self.addCleanup(carpeta.cleanup)
        self.privado = Path(carpeta.name) / "privado"
        self.publico = Path(carpeta.name) / "publico"
        with self.settings(PRIVATE_FILES_ROOT=self.privado, MEDIA_ROOT=self.publico):
            self.storage = CertificadoVeterinarioStorage()
        campo = CertificadoVeterinario._meta.get_field("archivo")
        cambio = patch.object(campo, "storage", self.storage)
        cambio.start()
        self.addCleanup(cambio.stop)
        self.client.force_authenticate(self.propietario)
        self.lista = reverse("mascota-certificados-veterinarios", args=[self.mascota.pk])

    def ruta(self, tipo):
        return reverse("mascota-certificado-veterinario", args=[self.mascota.pk, tipo])

    def intentar_carga(self, tipo, contenido):
        return self.client.put(self.ruta(tipo), {
            "archivo": SimpleUploadedFile("certificado.pdf", contenido, content_type="application/pdf"),
        }, format="multipart")

    def preparar_pendiente(self, mascota, tipo, contenido):
        certificado = CertificadoVeterinario(mascota=mascota, tipo=tipo, sha256=sha256(contenido).hexdigest())
        certificado.archivo.save("nombre-original.pdf", ContentFile(contenido))
        return certificado

    def test_almacenamiento_privado_y_estado_pendiente_por_mascota_y_tipo(self):
        contenido = pdf_de_prueba()
        for tipo in CertificadoVeterinario.Tipo.values:
            with self.subTest(tipo=tipo):
                certificado = self.preparar_pendiente(self.mascota, tipo, contenido)
                ruta = Path(certificado.archivo.path)
                self.assertTrue(ruta.is_relative_to(self.privado))
                self.assertFalse(ruta.is_relative_to(self.publico))
                self.assertNotEqual(ruta.name, "nombre-original.pdf")
                with self.assertRaises(ValueError):
                    certificado.archivo.url
                self.assertEqual(abrir_pdf_integro(certificado).getvalue(), contenido)
                self.assertEqual(self.client.get(self.ruta(tipo)).status_code, status.HTTP_409_CONFLICT)

        self.preparar_pendiente(self.otra_mascota, CertificadoVeterinario.Tipo.VACUNACION, contenido)
        listado = self.client.get(self.lista)
        self.assertEqual(listado.status_code, status.HTTP_200_OK)
        self.assertEqual(listado["Cache-Control"], "private, no-store")
        self.assertFalse(listado.data["carga_habilitada"])
        self.assertCountEqual(listado.data["certificados"], [
            {"tipo": tipo, "validacion": "PENDIENTE"} for tipo in CertificadoVeterinario.Tipo.values
        ])

    def test_rechaza_pdf_incompleto_y_sin_firma_y_bloquea_cualquier_alta(self):
        contenido = pdf_de_prueba()
        for tipo in CertificadoVeterinario.Tipo.values:
            with self.subTest(tipo=tipo):
                self.assertEqual(self.intentar_carga(tipo, b"Texto simple").status_code, status.HTTP_400_BAD_REQUEST)
                self.assertEqual(self.intentar_carga(tipo, contenido[:90]).status_code, status.HTTP_400_BAD_REQUEST)
                sin_firma = self.intentar_carga(tipo, contenido)
                self.assertEqual(sin_firma.status_code, status.HTTP_400_BAD_REQUEST)
                self.assertIn("no contiene una firma", str(sin_firma.data))
                intento = self.intentar_carga(tipo, pdf_de_prueba(con_marcador_falso=True))
                self.assertEqual(intento.status_code, status.HTTP_503_SERVICE_UNAVAILABLE, intento.data)
                self.assertIn("perfil de firma digital", str(intento.data))
        self.assertFalse(CertificadoVeterinario.objects.exists())
        self.assertFalse(self.privado.exists())

    def test_integridad_y_reemplazo_bloqueado_preservan_el_archivo_anterior(self):
        tipo = CertificadoVeterinario.Tipo.VACUNACION
        original = pdf_de_prueba()
        certificado = self.preparar_pendiente(self.mascota, tipo, original)
        ruta = Path(certificado.archivo.path)
        for contenido, codigo in ((b"invalido", 400), (pdf_de_prueba(con_marcador_falso=True), 503)):
            respuesta = self.intentar_carga(tipo, contenido)
            self.assertEqual(respuesta.status_code, codigo, respuesta.data)
        certificado.refresh_from_db()
        self.assertEqual(ruta.read_bytes(), original)
        self.assertEqual(certificado.sha256, sha256(original).hexdigest())
        self.assertEqual(list(ruta.parent.iterdir()), [ruta])
        self.mascota.refresh_from_db()
        self.assertTrue(self.mascota.evidencia_vacunacion.name.endswith("/evidencia.png"))
        self.assertTrue(self.mascota.evidencia_esterilizacion.name.endswith("/evidencia.png"))
        ruta.write_bytes(original + b"alterado")
        with self.assertRaisesRegex(CertificadoNoDisponible, "no coincide"):
            abrir_pdf_integro(certificado)

    def test_acceso_protegido_en_listado_archivo_y_reemplazo(self):
        tipo = CertificadoVeterinario.Tipo.ESTERILIZACION
        self.preparar_pendiente(self.mascota, tipo, pdf_de_prueba())
        for actor, codigo in (
            (self.otro_albergue, status.HTTP_404_NOT_FOUND),
            (self.adoptante, status.HTTP_403_FORBIDDEN),
            (None, status.HTTP_401_UNAUTHORIZED),
        ):
            with self.subTest(actor=actor):
                self.client.force_authenticate(actor)
                self.assertEqual(self.client.get(self.lista).status_code, codigo)
                self.assertEqual(self.client.get(self.ruta(tipo)).status_code, codigo)
                self.assertEqual(self.intentar_carga(tipo, b"datos irrelevantes").status_code, codigo)
