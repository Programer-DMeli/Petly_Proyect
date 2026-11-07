from django.conf import settings
from django.core.files.storage import FileSystemStorage


class PrivateContractStorage(FileSystemStorage):
    """Private contract and follow-up files outside MEDIA_ROOT, served only by authorized APIs."""

    def __init__(self, *args, **kwargs):
        kwargs.setdefault("location", settings.PRIVATE_FILES_ROOT)
        kwargs.setdefault("base_url", None)
        super().__init__(*args, **kwargs)
