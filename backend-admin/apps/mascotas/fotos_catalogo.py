"""Almacenamiento público de las fotos principales y sus referencias IA."""
import json
import logging
import re
from contextlib import contextmanager
from http.client import HTTPException
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4

from django.conf import settings
from django.core.files.base import ContentFile
from django.core.files.storage import FileSystemStorage
from django.db import DatabaseError, transaction
from django.db.models import Q
from django.utils.deconstruct import deconstructible
from rest_framework.exceptions import APIException

BUCKET = "petly-catalogo"
PREFIJO = "mascotas/supabase/"
RUTA_REMOTA = re.compile(r"mascotas/supabase/[0-9a-f]{32}\.(?:jpg|png)")
logger = logging.getLogger(__name__)


class FotoCatalogoNoDisponible(APIException):
    status_code = 503
    default_detail = "No se pudo subir la foto. Intenta nuevamente."


class SinRedirecciones(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _solicitud(url, metodo, datos=None, cabeceras=None):
    # La clave del servidor nunca se reenvía a otra URL mediante una redirección.
    request = Request(url, data=datos, headers=cabeceras or {}, method=metodo)
    try:
        return build_opener(SinRedirecciones()).open(request, timeout=10)
    except HTTPException as exc:
        raise OSError("Respuesta inválida del almacenamiento de catálogo.") from exc


def _url_objetos():
    if not settings.SUPABASE_URL:
        raise OSError("Falta SUPABASE_URL para el catálogo.")
    return f"{settings.SUPABASE_URL}/storage/v1/object"


def _cabeceras_privadas():
    return {"apikey": settings.SUPABASE_SECRET_KEY}


@deconstructible
class AlmacenamientoFotosCatalogo(FileSystemStorage):
    def path(self, name):
        if RUTA_REMOTA.fullmatch(name):
            raise NotImplementedError("La foto del catálogo no tiene una ruta local.")
        return super().path(name)

    def url(self, name):
        if RUTA_REMOTA.fullmatch(name):
            return f"{_url_objetos()}/public/{BUCKET}/{name}"
        return super().url(name)

    def _open(self, name, mode="rb"):
        if not RUTA_REMOTA.fullmatch(name):
            return super()._open(name, mode)
        if mode != "rb":
            raise ValueError("La apertura de fotos remotas requiere modo rb.")
        try:
            with _solicitud(self.url(name), "GET") as response:
                return ContentFile(response.read(), name=name)
        except HTTPError as exc:
            with exc:
                if exc.code == 404:
                    raise FileNotFoundError(name) from exc
            raise OSError("No se pudo abrir la foto del catálogo.") from exc
        except HTTPException as exc:
            raise OSError("No se pudo leer la foto completa.") from exc

    def exists(self, name):
        if not RUTA_REMOTA.fullmatch(name):
            return super().exists(name)
        try:
            with _solicitud(self.url(name), "HEAD"):
                return True
        except HTTPError as exc:
            with exc:
                if exc.code == 404:
                    return False
            raise

    def delete(self, name):
        if not RUTA_REMOTA.fullmatch(name):
            return super().delete(name)
        datos = json.dumps({"prefixes": [name]}).encode()
        cabeceras = {**_cabeceras_privadas(), "Content-Type": "application/json"}
        with _solicitud(f"{_url_objetos()}/{BUCKET}", "DELETE", datos, cabeceras):
            pass


almacenamiento_fotos_catalogo = AlmacenamientoFotosCatalogo()


def _eliminar_sin_referencias(ruta):
    from .models import Mascota, PedidoAfiche

    try:
        fotos = Q(foto_principal=ruta) | Q(foto_original_ia=ruta) | Q(foto_mejorada_ia=ruta)
        if not Mascota.objects.filter(fotos).exists() and not PedidoAfiche.objects.filter(foto_ruta=ruta).exists():
            almacenamiento_fotos_catalogo.delete(ruta)
    except (DatabaseError, OSError):
        logger.warning("Quedó pendiente revisar la foto de catálogo %s", ruta)


@contextmanager
def guardar_foto_catalogo():
    """Coordina una subida y la escritura de la ficha en una transacción."""
    candidata = None

    def preparar(foto, mascota=None):
        nonlocal candidata
        if foto is None or not (settings.SUPABASE_URL or settings.SUPABASE_SECRET_KEY):
            return foto
        if not (settings.SUPABASE_URL and settings.SUPABASE_SECRET_KEY):
            raise FotoCatalogoNoDisponible()
        extension, tipo = {"JPEG": ("jpg", "image/jpeg"), "PNG": ("png", "image/png")}[foto.image.format]
        candidata = f"{PREFIJO}{uuid4().hex}.{extension}"
        clave_objeto = f"{BUCKET}/{candidata}"
        cabeceras = {**_cabeceras_privadas(), "Content-Type": tipo, "x-upsert": "false"}
        try:
            with _solicitud(f"{_url_objetos()}/{clave_objeto}", "POST", foto.read(), cabeceras) as response:
                confirmacion = json.loads(response.read(4096))
                if not isinstance(confirmacion, dict) or confirmacion.get("Key") != clave_objeto:
                    raise ValueError("Supabase no confirmó la clave subida.")
        except HTTPError as exc:
            with exc:
                # Un rechazo 4xx (incluido un nombre ocupado) no crea un objeto.
                if 400 <= exc.code < 500 and exc.code != 408:
                    candidata = None
            raise FotoCatalogoNoDisponible() from exc
        except (OSError, HTTPException, ValueError) as exc:
            # Sin confirmación, la subida podría haberse completado en el servidor.
            raise FotoCatalogoNoDisponible() from exc
        if mascota is not None:
            anteriores = {mascota.foto_principal.name, mascota.foto_original_ia.name, mascota.foto_mejorada_ia.name}
            for ruta in anteriores:
                if ruta and RUTA_REMOTA.fullmatch(ruta):
                    transaction.on_commit(lambda ruta=ruta: _eliminar_sin_referencias(ruta))
        return candidata

    try:
        with transaction.atomic():
            yield preparar
    except Exception:
        if candidata:
            _eliminar_sin_referencias(candidata)
        raise
