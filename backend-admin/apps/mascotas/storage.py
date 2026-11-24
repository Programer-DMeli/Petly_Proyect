from uuid import uuid4

from django.conf import settings
from django.core.files.storage import FileSystemStorage


class CertificadoVeterinarioStorage(FileSystemStorage):
    def __init__(self):
        super().__init__(location=settings.PRIVATE_FILES_ROOT, base_url=None,
                         file_permissions_mode=0o600, directory_permissions_mode=0o700)

    def url(self, name):
        raise ValueError("Los certificados veterinarios se sirven mediante una API privada.")


def ruta_certificado_veterinario(certificado, _nombre_original):
    return f"mascotas/certificados/{certificado.mascota_id}/{certificado.tipo}/{uuid4().hex}.pdf"
