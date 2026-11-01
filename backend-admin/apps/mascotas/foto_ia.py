"""Proveedor configurable para generar una propuesta de mejora fotográfica (US-27).

Contrato del proveedor: POST JSON con ``prompt``, ``image`` (mimeType/base64) y
``outputMimeType``. La respuesta JSON debe contener ``imageBase64`` y
``mimeType`` (image/jpeg o image/png). El resultado se devuelve como preview
temporal; Petly no lo persiste hasta que el albergue lo envía como
``foto_principal`` en la edición normal de la mascota.

Configuración por variables de entorno: US27_IMAGE_API_URL, US27_IMAGE_API_TOKEN
y US27_IMAGE_API_TIMEOUT_SECONDS. El endpoint debe usar HTTPS, excepto HTTP en
DEBUG. El proveedor debe realizar edición/encuadre real de imagen; no se aplica
un filtro local que se presente como mejora de IA.
"""
import base64
import binascii
import json
import os
from io import BytesIO
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from django.conf import settings
from PIL import Image, ImageOps


MAX_INPUT_BYTES = 5 * 1024 * 1024
MAX_OUTPUT_BYTES = 10 * 1024 * 1024
MAX_IMAGE_PIXELS = 20_000_000


class FotoIAError(Exception):
    """Imagen ausente o inválida para procesar."""


class ProveedorFotoNoConfigurado(FotoIAError):
    """No hay proveedor de mejora configurado."""


class ErrorProveedorFoto(FotoIAError):
    """El proveedor no pudo devolver una imagen válida."""


def _validar_imagen(data, mime_type):
    if mime_type not in {"image/jpeg", "image/png"}:
        raise ErrorProveedorFoto("El proveedor debe devolver una imagen JPEG o PNG.")
    if not data or len(data) > MAX_OUTPUT_BYTES:
        raise ErrorProveedorFoto("La imagen mejorada está vacía o supera 10 MB.")
    try:
        with Image.open(BytesIO(data)) as image:
            expected = {"image/jpeg": "JPEG", "image/png": "PNG"}[mime_type]
            if image.format != expected or image.width * image.height > MAX_IMAGE_PIXELS:
                raise ValueError("tipo o dimensiones inválidos")
            image.verify()
        with Image.open(BytesIO(data)) as image:
            image = ImageOps.exif_transpose(image)
            width, height = image.size
    except Exception as exc:
        raise ErrorProveedorFoto("El proveedor devolvió una imagen inválida.") from exc
    return width, height


def mejorar_foto(mascota):
    """Genera una versión mejorada temporal y conserva intacta la original."""
    endpoint = os.getenv("US27_IMAGE_API_URL", "").strip()
    token = os.getenv("US27_IMAGE_API_TOKEN", "").strip()
    if not endpoint or not token:
        raise ProveedorFotoNoConfigurado(
            "Configura US27_IMAGE_API_URL y US27_IMAGE_API_TOKEN para mejorar fotografías."
        )
    parsed = urlsplit(endpoint)
    allowed_schemes = {"https"} | ({"http"} if settings.DEBUG else set())
    if parsed.scheme not in allowed_schemes or not parsed.netloc:
        raise ProveedorFotoNoConfigurado("El proveedor de imágenes debe usar HTTPS (HTTP solo en DEBUG).")

    if not mascota.foto_principal:
        raise FotoIAError("La mascota necesita una foto guardada antes de generar una mejora.")
    photo = mascota.foto_principal
    try:
        with photo.open("rb") as stream:
            original = stream.read(MAX_INPUT_BYTES + 1)
    except (OSError, ValueError) as exc:
        raise FotoIAError("No se pudo leer la fotografía guardada.") from exc
    if len(original) > MAX_INPUT_BYTES:
        raise FotoIAError("La fotografía supera el límite de 5 MB.")
    try:
        with Image.open(BytesIO(original)) as image:
            mime_type = {"JPEG": "image/jpeg", "PNG": "image/png"}[image.format]
            if image.width * image.height > MAX_IMAGE_PIXELS:
                raise ValueError("dimensiones excesivas")
            image.verify()
    except Exception as exc:
        raise FotoIAError("Usa una fotografía JPEG o PNG válida.") from exc

    prompt = (
        "Mejora esta fotografía para un catálogo de adopción: corrige iluminación, contraste y nitidez "
        "de forma natural y optimiza el encuadre para que la mascota sea el foco principal. Conserva "
        "exactamente la identidad, apariencia, colores y rasgos reales del animal. No añadas, elimines "
        "ni alteres partes de la mascota; no agregues texto, accesorios ni marcas. Mantén un resultado "
        "fotográfico realista y devuelve únicamente la imagen editada."
    )
    payload = json.dumps({
        "prompt": prompt,
        "image": {"mimeType": mime_type, "base64": base64.b64encode(original).decode("ascii")},
        "outputMimeType": mime_type,
    }).encode("utf-8")
    try:
        timeout = max(1, min(int(os.getenv("US27_IMAGE_API_TIMEOUT_SECONDS", "30")), 90))
        request = Request(endpoint, data=payload, headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }, method="POST")
        with urlopen(request, timeout=timeout) as response:
            raw = response.read(64 * 1024 * 1024 + 1)
        if len(raw) > 64 * 1024 * 1024:
            raise ValueError("respuesta demasiado grande")
        result = json.loads(raw.decode("utf-8"))
        if not isinstance(result, dict) or not isinstance(result.get("imageBase64"), str):
            raise ValueError("faltan campos de imagen")
        output_mime = result.get("mimeType")
        improved = base64.b64decode(result["imageBase64"], validate=True)
        if len(improved) > MAX_OUTPUT_BYTES:
            raise ValueError("imagen de salida demasiado grande")
        width, height = _validar_imagen(improved, output_mime)
    except HTTPError as exc:
        raise ErrorProveedorFoto(f"El proveedor de imágenes respondió HTTP {exc.code}.") from exc
    except (URLError, TimeoutError, OSError) as exc:
        raise ErrorProveedorFoto("No fue posible conectar con el proveedor de imágenes.") from exc
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, TypeError, binascii.Error) as exc:
        raise ErrorProveedorFoto("El proveedor no devolvió una imagen válida.") from exc

    extension = "jpg" if output_mime == "image/jpeg" else "png"
    encoded = base64.b64encode(improved).decode("ascii")
    return {
        "mime_type": output_mime,
        "nombre": f"{mascota.nombre.strip().replace(' ', '-')}-mejorada.{extension}",
        "base64": encoded,
        "data_url": f"data:{output_mime};base64,{encoded}",
        "ancho": width,
        "alto": height,
    }
