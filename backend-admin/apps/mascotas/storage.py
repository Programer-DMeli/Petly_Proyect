import json
from pathlib import PurePosixPath
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4

from django.conf import settings
from django.core.files.base import ContentFile
from django.core.files.storage import FileSystemStorage, Storage, default_storage
from PIL import Image


class ErrorFotosCatalogo(OSError):
    pass


class SinRedireccion(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # La credencial de escritura solo puede llegar al endpoint configurado.
        return None


class FotosCatalogoStorage(Storage):
    @property
    def activo(self):
        return bool(settings.SUPABASE_URL)

    def _endpoint(self, name, *, publico=False):
        ruta = PurePosixPath(name)
        if (len(ruta.parts) != 2 or ruta.parts[0] != "mascotas" or ruta.name == ".."
                or ruta.as_posix() != name or "\\" in name):
            raise ErrorFotosCatalogo("La ruta de una foto principal debe estar en mascotas/.")
        base = urlsplit(settings.SUPABASE_URL)
        if (base.scheme != "https" or not base.hostname or base.username or base.password
                or base.path not in ("", "/") or base.query or base.fragment):
            raise ErrorFotosCatalogo("Configura SUPABASE_URL con el origen HTTPS del proyecto.")
        publico = "public/" if publico else ""
        bucket = quote(settings.SUPABASE_CATALOG_BUCKET, safe="")
        return f"{base.scheme}://{base.netloc}/storage/v1/object/{publico}{bucket}/{quote(name)}"

    def _request(self, method, name, *, publico=False, data=None, headers=None):
        headers = dict(headers or {})
        if not publico:
            secret = settings.SUPABASE_STORAGE_SECRET_KEY
            if not secret:
                raise ErrorFotosCatalogo("Falta la credencial de escritura de las fotos del catálogo.")
            headers["apikey"] = secret
            if not secret.startswith("sb_secret_"):
                headers["Authorization"] = f"Bearer {secret}"
        request = Request(self._endpoint(name, publico=publico), data=data, headers=headers, method=method)
        try:
            return build_opener(SinRedireccion()).open(request, timeout=15)
        except HTTPError as exc:
            with exc:
                if publico and method == "GET" and exc.code in {400, 404}:
                    # Storage envía NoSuchKey como HTTP 400 en el endpoint público.
                    try:
                        error = json.loads(exc.read(4096))
                    except ValueError:
                        error = {}
                    if isinstance(error, dict) and error.get("code") == "NoSuchKey":
                        raise FileNotFoundError(name) from None
            raise ErrorFotosCatalogo(f"Supabase Storage respondió HTTP {exc.code}.") from None
        except (URLError, OSError):
            raise ErrorFotosCatalogo("No se pudo conectar con Supabase Storage.") from None

    def url(self, name):
        return self._endpoint(name, publico=True) if self.activo else default_storage.url(name)

    def existe_en_supabase(self, name):
        try:
            with self._request("GET", name, publico=True, headers={"Range": "bytes=0-0"}):
                return True
        except FileNotFoundError:
            return False

    def exists(self, name):
        name = name.replace("\\", "/")
        return default_storage.exists(name) or (self.activo and self.existe_en_supabase(name))

    def _open(self, name, mode="rb"):
        if self.activo:
            try:
                with self._request("GET", name, publico=True) as response:
                    return ContentFile(response.read(), name=name)
            except FileNotFoundError:
                pass
        return default_storage.open(name, mode)

    def _save(self, name, content):
        if not self.activo:
            return default_storage.save(name, content)
        return self.publicar(name.replace("\\", "/"), content)

    def publicar(self, name, content):
        """Sube a la ruta exacta cuando la ruta ya está registrada en MariaDB."""
        try:
            image = Image.open(content)
            mime_type = {"JPEG": "image/jpeg", "PNG": "image/png"}[image.format]
            image.verify()
            content.seek(0)
        except (OSError, KeyError, ValueError, SyntaxError, Image.DecompressionBombError):
            raise ErrorFotosCatalogo("La foto principal debe ser una imagen JPEG o PNG válida.") from None
        with self._request("POST", name, data=content.read(),
                           headers={"Content-Type": mime_type, "x-upsert": "false"}):
            return name

    def delete(self, name):
        if self.activo:
            with self._request("DELETE", name):
                return
        default_storage.delete(name)


def almacenamiento_fotos_catalogo():
    return FotosCatalogoStorage()


class CertificadoVeterinarioStorage(FileSystemStorage):
    def __init__(self):
        super().__init__(location=settings.PRIVATE_FILES_ROOT, base_url=None,
                         file_permissions_mode=0o600, directory_permissions_mode=0o700)

    def url(self, name):
        raise ValueError("Los certificados veterinarios se sirven mediante una API privada.")


def ruta_certificado_veterinario(certificado, _nombre_original):
    return f"mascotas/certificados/{certificado.mascota_id}/{certificado.tipo}/{uuid4().hex}.pdf"
