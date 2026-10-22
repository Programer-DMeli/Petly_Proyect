from django.conf import settings
from django.core.files.storage import FileSystemStorage


class PrivateContractStorage(FileSystemStorage):
    """Contract files live outside MEDIA_ROOT and are served only by the authorized API."""

    def __init__(self, *args, **kwargs):
        kwargs.setdefault("location", settings.PRIVATE_FILES_ROOT)
        kwargs.setdefault("base_url", None)
        super().__init__(*args, **kwargs)
