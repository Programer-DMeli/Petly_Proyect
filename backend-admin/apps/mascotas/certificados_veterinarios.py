import logging
from datetime import datetime, timezone
from hashlib import sha256
from io import BytesIO
from pathlib import Path

from django.conf import settings
from django.core.files.base import ContentFile
from django.db import DatabaseError, transaction
from pyhanko.keys import load_certs_from_pemder
from pyhanko.pdf_utils.misc import PdfError
from pyhanko.pdf_utils.reader import PdfFileReader
from pyhanko.sign.diff_analysis import ModificationLevel
from pyhanko.sign.validation import validate_pdf_signature
from pyhanko.sign.validation.errors import SignatureValidationError
from pyhanko.sign.validation.status import SignatureCoverageLevel
from pyhanko_certvalidator import ValidationContext
from pypdf import PdfReader
from pypdf.errors import PdfReadError
from pypdf.generic import DictionaryObject
from rest_framework.exceptions import APIException, ValidationError

from .models import CertificadoVeterinario, Mascota

MAX_BYTES = 10 * 1024 * 1024
PERFIL_DEMO = "DEMO_AUTOFIRMADA"
PERFIL_PENDIENTE = "La carga requiere un perfil de firma digital aprobado y el certificado público de confianza."
AVISO_DEMO = "Perfil académico con certificado de prueba. Usa únicamente documentos con datos ficticios."
logger = logging.getLogger(__name__)


class PerfilFirmaNoDefinido(APIException):
    status_code = 503
    default_detail = PERFIL_PENDIENTE


class CertificadoNoDisponible(APIException):
    status_code = 409
    default_detail = "El certificado permanece pendiente de verificación."


class GuardadoCertificadoNoDisponible(APIException):
    status_code = 503
    default_detail = "No se pudo guardar el certificado. Vuelve a intentar."


def comprobar_formato_pdf(archivo):
    """Inspección preliminar. Un campo de firma no demuestra integridad ni confianza."""
    if not archivo or not 0 < archivo.size <= MAX_BYTES:
        rechazar("INVALID", "Adjunta un PDF de hasta 10 MB.")

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
        raise ValidationError({
            "codigo": "INVALID", "detail": "Adjunta un PDF legible, completo y sin contraseña.",
        }) from error

    if not any(isinstance(firma, DictionaryObject) and firma.get("/Contents") for firma in firmas):
        rechazar("UNSIGNED", "El PDF no contiene una firma digital incorporada.")
    archivo.seek(0)
    return archivo.read(MAX_BYTES + 1)


def rechazar(codigo, mensaje):
    raise ValidationError({"codigo": codigo, "detail": mensaje})


def certificado_de_confianza():
    """El perfil académico usa un certificado configurado explícitamente como ancla y firmante."""
    if settings.PETLY_CERTIFICADOS_PERFIL != PERFIL_DEMO or not settings.PETLY_CERTIFICADOS_CERT_PUBLICO:
        return None
    try:
        ruta = Path(settings.PETLY_CERTIFICADOS_CERT_PUBLICO)
        certificados = list(load_certs_from_pemder([str(ruta)]))
        if len(certificados) == 1 and certificados[0].issuer == certificados[0].subject:
            return certificados[0]
    except (OSError, ValueError):
        pass
    return None


def verificar_firma_pdf(contenido, confiable):
    """Valida una firma PAdES y el alcance de las revisiones con el perfil de prueba."""
    try:
        lector = PdfFileReader(BytesIO(contenido), strict=True)
        firmas = lector.embedded_regular_signatures
        if not firmas:
            rechazar("UNSIGNED", "El PDF no contiene una firma digital verificable.")
        if len(firmas) != 1 or firmas[0].sig_object.get("/SubFilter") != "/ETSI.CAdES.detached":
            rechazar("INVALID", "El perfil de prueba requiere una única firma PAdES.")
        contexto = ValidationContext(trust_roots=[confiable], allow_fetching=False)
        estado = validate_pdf_signature(firmas[0], signer_validation_context=contexto)
    except (PdfError, SignatureValidationError, ValueError, TypeError, KeyError, IndexError,
            AttributeError, OverflowError, RecursionError) as error:
        raise ValidationError({
            "codigo": "INVALID", "detail": "No se pudo verificar la integridad de la firma del PDF.",
        }) from error

    revision_valida = (
        estado.coverage == SignatureCoverageLevel.ENTIRE_FILE
        or (estado.coverage == SignatureCoverageLevel.ENTIRE_REVISION
            and estado.modification_level in (ModificationLevel.NONE, ModificationLevel.LTA_UPDATES))
    )
    if not estado.intact or not estado.valid or not revision_valida or not estado.docmdp_ok:
        rechazar("INVALID", "El PDF fue alterado o la firma no protege su contenido.")
    vigencia = estado.signing_cert["tbs_certificate"]["validity"]
    ahora = datetime.now(timezone.utc)
    if (estado.signing_cert.dump() != confiable.dump() or not estado.trusted
            or not vigencia["not_before"].native <= ahora <= vigencia["not_after"].native):
        rechazar("UNTRUSTED", "La firma usa un certificado no confiable o fuera de vigencia.")
    if not estado.bottom_line:
        rechazar("INVALID", "La firma no cumple el perfil de prueba de Petly.")


def guardar_certificado_verificado(mascota, tipo, contenido):
    """Serializa altas y reemplazos. El archivo anterior se retira tras confirmar la base."""
    archivo_nuevo = None
    campo = CertificadoVeterinario._meta.get_field("archivo")
    storage = campo.storage
    try:
        with transaction.atomic():
            Mascota.objects.select_for_update().get(pk=mascota.pk, albergue_id=mascota.albergue_id)
            certificado, _ = mascota.certificados_veterinarios.get_or_create(tipo=tipo)
            archivo_anterior = certificado.archivo.name
            archivo_nuevo = campo.generate_filename(certificado, "certificado.pdf")
            archivo_nuevo = storage.save(archivo_nuevo, ContentFile(contenido), max_length=campo.max_length)
            certificado.archivo = archivo_nuevo
            certificado.sha256 = sha256(contenido).hexdigest()
            certificado.validacion = CertificadoVeterinario.Validacion.VERIFICADO
            certificado.save(update_fields=["archivo", "sha256", "validacion"])
            if archivo_anterior and archivo_anterior != archivo_nuevo:
                transaction.on_commit(lambda: storage.delete(archivo_anterior), robust=True)
    except (DatabaseError, OSError) as error:
        if archivo_nuevo:
            try:
                storage.delete(archivo_nuevo)
            except OSError:
                logger.exception("No se pudo retirar el archivo temporal del certificado veterinario.")
        logger.exception("Falló el almacenamiento del certificado veterinario.")
        raise GuardadoCertificadoNoDisponible() from error
    return certificado


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
