from .settings import *
import tempfile
DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}}
SECRET_KEY = __import__("secrets").token_urlsafe(48)
SPRING_JWT_SECRET = __import__("secrets").token_urlsafe(48)
MEDIA_ROOT = tempfile.mkdtemp(prefix="petly-test-media-")
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]
