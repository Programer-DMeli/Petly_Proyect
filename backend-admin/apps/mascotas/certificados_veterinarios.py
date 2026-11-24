from hashlib import sha256
from io import BytesIO

from pypdf import PdfReader
from pypdf.errors import PdfReadError
from pypdf.generic import DictionaryObject
from rest_framework.exceptions import APIException, ValidationError


MAX_BYTES = 10 * 1024 * 1024
PERFIL_PENDIENTE = "La carga de certificados está suspendida hasta que se defina el perfil de firma digital."


class PerfilFirmaNoDefinido(APIException):
    status_code = 503
    default_detail = PERFIL_PENDIENTE


class CertificadoNoDisponible(APIException):
    status_code = 409
    default_detail = "El certificado permanece pendiente de verificación."


def comprobar_formato_pdf(archivo):
    """Inspección preliminar. Un campo de firma no demuestra integridad ni confianza."""
    if not archivo or not 0 < archivo.size <= MAX_BYTES:
        raise ValidationError("Adjunta un PDF de hasta 10 MB.")

    try:
        lector = PdfReader(archivo.file, strict=True)
        if lector.is_encrypted or not lector.pages:
            raise PdfReadError("El archivo debe tener páginas y poder abrirse sin contraseña.")
        campos = lector.get_fields() or {}
        firmas = [
            valor.get_object() for campo in campos.values()
            if campo.get("/FT") == "/Sig" and (valor := campo.get("/V")) is not None
        ]
    except (PdfReadError, ValueError, TypeError, KeyError, IndexError, AttributeError, RecursionError, OverflowError) as error:
        raise ValidationError("Adjunta un PDF legible, completo y sin contraseña.") from error

    if not any(isinstance(firma, DictionaryObject) and firma.get("/Contents") for firma in firmas):
        raise ValidationError("El PDF no contiene una firma digital incorporada.")


def abrir_pdf_integro(certificado):
    """Comprueba que los bytes almacenados coinciden con los registrados antes de servirlos."""
    try:
        with certificado.archivo.open("rb") as archivo:
            contenido = archivo.read(MAX_BYTES + 1)
    except OSError as error:
        raise CertificadoNoDisponible("El archivo del certificado no está disponible.") from error

    if (len(contenido) > MAX_BYTES or not contenido.startswith(b"%PDF-")
            or sha256(contenido).hexdigest() != certificado.sha256):
        raise CertificadoNoDisponible("El archivo almacenado no coincide con el certificado registrado.")
    return BytesIO(contenido)
