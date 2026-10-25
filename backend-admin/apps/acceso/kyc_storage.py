import uuid

from django.conf import settings
from django.core.files.storage import FileSystemStorage


class PrivateKycStorage(FileSystemStorage):
    """KYC files are stored outside MEDIA_ROOT and served only by an authorized view."""

    def __init__(self, *args, **kwargs):
        kwargs.setdefault("location", settings.PRIVATE_FILES_ROOT)
        kwargs.setdefault("base_url", None)
        super().__init__(*args, **kwargs)


def nombre_archivo_kyc(instance, filename):
    extension = filename.rsplit(".", 1)[-1].lower() if "." in filename else "bin"
    return f"kyc/{instance.adoptante_id}/{uuid.uuid4().hex}.{extension}"
