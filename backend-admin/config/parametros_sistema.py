"""Consulta parámetros globales a Spring, autoridad única de configuración."""
import json
import urllib.error
import urllib.request

from django.conf import settings
from rest_framework.exceptions import APIException


class ConfiguracionGlobalNoDisponible(APIException):
    status_code = 503
    default_detail = "No se pudo consultar la configuración del sistema. Intenta nuevamente."


def obtener_limite_imagen_bytes(timeout=3.0):
    """Obtiene el máximo vigente de Spring; falla cerrado si no puede comprobarse."""
    limite_test = getattr(settings, "PETLY_TEST_MAX_IMAGE_BYTES", None)
    if limite_test is not None:
        return limite_test

    url = f"{settings.SPRING_API_URL.rstrip('/')}/api/interno/configuracion-global"
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "X-Internal-Secret": settings.INTERNAL_API_SECRET,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
        limite_mb = data.get("maxImagenMb")
        if type(limite_mb) is not int or not 1 <= limite_mb <= 20:
            raise ValueError("Spring devolvió un límite de imagen inválido.")
        return limite_mb * 1024 * 1024
    except (urllib.error.URLError, urllib.error.HTTPError, OSError, ValueError, TypeError) as error:
        raise ConfiguracionGlobalNoDisponible() from error
