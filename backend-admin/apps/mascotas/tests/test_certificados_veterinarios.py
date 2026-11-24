from hashlib import sha256
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import DatabaseError
from django.test import override_settings
from django.urls import reverse
from pyhanko.pdf_utils.generic import NameObject as NombrePdf
from pyhanko.pdf_utils.incremental_writer import IncrementalPdfFileWriter
from pyhanko.sign.signers import SimpleSigner
from pyhanko.sign.validation.dss import DocumentSecurityStore
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ArrayObject, ByteStringObject, DictionaryObject, NameObject, TextStringObject
from rest_framework import status
from rest_framework.test import APITestCase

from apps.albergues.models import Albergue
from apps.mascotas.certificados_demo import firmar_pdf_demo, generar_identidad_demo, pdf_veterinario_demo
from apps.mascotas.certificados_veterinarios import PERFIL_DEMO, CertificadoNoDisponible, abrir_pdf_integro
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

    def test_un_reemplazo_bloqueado_conserva_la_descarga_del_certificado_vigente(self):
        tipo = CertificadoVeterinario.Tipo.ESTERILIZACION
        original = pdf_de_prueba()
        certificado = self.preparar_pendiente(self.mascota, tipo, original)
        # Simula un registro previamente verificado para probar la ruta de descarga.
        certificado.validacion = CertificadoVeterinario.Validacion.VERIFICADO
        certificado.save(update_fields=["validacion"])
        ruta_original = Path(certificado.archivo.path)

        reemplazo = self.intentar_carga(tipo, pdf_de_prueba(con_marcador_falso=True))
        self.assertEqual(reemplazo.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertEqual(CertificadoVeterinario.objects.filter(mascota=self.mascota, tipo=tipo).count(), 1)
        certificado.refresh_from_db()
        self.assertEqual(certificado.validacion, CertificadoVeterinario.Validacion.VERIFICADO)
        self.assertEqual(Path(certificado.archivo.path), ruta_original)
        self.assertEqual(list(ruta_original.parent.iterdir()), [ruta_original])

        descarga = self.client.get(self.ruta(tipo))
        self.addCleanup(descarga.close)
        self.assertEqual(descarga.status_code, status.HTTP_200_OK)
        self.assertEqual(descarga["Content-Type"], "application/pdf")
        self.assertEqual(descarga["Cache-Control"], "private, no-store")
        self.assertEqual(b"".join(descarga.streaming_content), original)

        self.client.force_authenticate(self.otro_albergue)
        self.assertEqual(self.client.get(self.ruta(tipo)).status_code, status.HTTP_404_NOT_FOUND)
        self.client.force_authenticate(self.propietario)
        ruta_original.write_bytes(original + b"alterado")
        self.assertEqual(self.client.get(self.ruta(tipo)).status_code, status.HTTP_409_CONFLICT)

    def firmante_demo(self, nombre):
        desde = self.privado.parent
        clave_bytes, cert_bytes = generar_identidad_demo(b"clave-efimera-de-prueba")
        self.assertIn(b"BEGIN ENCRYPTED PRIVATE KEY", clave_bytes)
        clave, publico = desde / f"{nombre}-clave.pem", desde / f"{nombre}-publico.pem"
        clave.write_bytes(clave_bytes)
        publico.write_bytes(cert_bytes)
        firmante = SimpleSigner.load(str(clave), str(publico), key_passphrase=b"clave-efimera-de-prueba")
        self.assertIsNotNone(firmante)
        return firmante, publico

    def test_perfil_demo_acepta_ambos_tipos_y_reemplaza_solo_al_guardar(self):
        firmante, publico = self.firmante_demo("petly")
        with self.settings(PETLY_CERTIFICADOS_PERFIL=PERFIL_DEMO, PETLY_CERTIFICADOS_CERT_PUBLICO=publico):
            self.assertTrue(self.client.get(self.lista).data["carga_habilitada"])
            for tipo in CertificadoVeterinario.Tipo.values:
                original = pdf_veterinario_demo(tipo)
                self.assertIn("Muestra de prueba · sin validez veterinaria",
                              PdfReader(BytesIO(original)).pages[0].extract_text())
                firmado = firmar_pdf_demo(original, firmante)
                respuesta = self.intentar_carga(tipo, firmado)
                self.assertEqual(respuesta.status_code, 200, respuesta.data)
                self.assertEqual(respuesta.data, {"tipo": tipo, "validacion": "VERIFICADO", "codigo": "VALID"})
                descarga = self.client.get(self.ruta(tipo))
                self.addCleanup(descarga.close)
                self.assertEqual(b"".join(descarga.streaming_content), firmado)

            tipo = CertificadoVeterinario.Tipo.VACUNACION
            registro = self.mascota.certificados_veterinarios.get(tipo=tipo)
            ruta_anterior = Path(registro.archivo.path)
            reemplazo = firmar_pdf_demo(pdf_veterinario_demo(tipo), firmante)
            with self.captureOnCommitCallbacks(execute=True):
                self.assertEqual(self.intentar_carga(tipo, reemplazo).status_code, 200)
            registro.refresh_from_db()
            self.assertFalse(ruta_anterior.exists())
            self.assertEqual(Path(registro.archivo.path).read_bytes(), reemplazo)
            self.assertEqual(self.mascota.certificados_veterinarios.count(), 2)

            with patch.object(CertificadoVeterinario, "save", side_effect=DatabaseError("fallo simulado")):
                respuesta = self.intentar_carga(tipo, firmado)
                self.assertEqual(respuesta.status_code, 503, respuesta.data)

            def guardar_parcial(nombre, contenido, *, max_length):
                Path(self.storage.path(nombre)).write_bytes(b"archivo parcial")
                raise OSError("fallo simulado de escritura")

            with patch.object(self.storage, "save", side_effect=guardar_parcial):
                respuesta = self.intentar_carga(tipo, firmado)
                self.assertEqual(respuesta.status_code, 503, respuesta.data)
            registro.refresh_from_db()
            self.assertEqual(list(ruta_anterior.parent.iterdir()), [Path(registro.archivo.path)])
            self.assertEqual(Path(registro.archivo.path).read_bytes(), reemplazo)

    def test_perfil_demo_distingue_rechazos_y_protege_el_archivo_vigente(self):
        firmante, publico = self.firmante_demo("petly")
        ajeno, _ = self.firmante_demo("ajeno")
        tipo = CertificadoVeterinario.Tipo.VACUNACION
        original = pdf_veterinario_demo(tipo)
        firmado = firmar_pdf_demo(original, firmante)
        alterado = firmado.replace(b"/Reason (Muestra de prueba)", b"/Reason (Muestra de fraude)")
        self.assertNotEqual(alterado, firmado)
        editor = IncrementalPdfFileWriter(BytesIO(firmado))
        editor.root[NombrePdf("/PageMode")] = NombrePdf("/UseThumbs")
        editor.update_root()
        revision = BytesIO()
        editor.write(revision)
        with self.settings(PETLY_CERTIFICADOS_PERFIL=PERFIL_DEMO, PETLY_CERTIFICADOS_CERT_PUBLICO=publico):
            self.assertEqual(self.intentar_carga(tipo, firmado).status_code, 200)
            for contenido, codigo in [
                (original, "UNSIGNED"), (alterado, "INVALID"), (revision.getvalue(), "INVALID"),
                (firmar_pdf_demo(original, ajeno), "UNTRUSTED"), (b"%PDF-danado", "INVALID"),
            ]:
                with self.subTest(codigo=codigo):
                    respuesta = self.intentar_carga(tipo, contenido)
                    self.assertEqual(respuesta.status_code, 400, respuesta.data)
                    self.assertEqual(respuesta.data["codigo"], codigo)
            guardado = self.mascota.certificados_veterinarios.get(tipo=tipo)
            self.assertEqual(Path(guardado.archivo.path).read_bytes(), firmado)
            self.assertEqual(list(Path(guardado.archivo.path).parent.iterdir()), [Path(guardado.archivo.path)])
            self.client.force_authenticate(self.otro_albergue)
            self.assertEqual(self.intentar_carga(tipo, firmado).status_code, 404)

    def test_actualizacion_incremental_de_datos_de_validacion_es_admisible(self):
        firmante, publico = self.firmante_demo("petly")
        tipo = CertificadoVeterinario.Tipo.ESTERILIZACION
        firmado = firmar_pdf_demo(pdf_veterinario_demo(tipo), firmante)
        editor = IncrementalPdfFileWriter(BytesIO(firmado))
        DocumentSecurityStore.supply_dss_in_writer(editor, None, certs=[firmante.signing_cert])
        revision = BytesIO()
        editor.write(revision)
        with self.settings(PETLY_CERTIFICADOS_PERFIL=PERFIL_DEMO, PETLY_CERTIFICADOS_CERT_PUBLICO=publico):
            resultado = self.intentar_carga(tipo, revision.getvalue())
            self.assertEqual(resultado.status_code, 200, resultado.data)
