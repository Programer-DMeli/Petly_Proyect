from .settings import *
DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}}
SECRET_KEY = __import__("secrets").token_urlsafe(48)
SPRING_JWT_SECRET = __import__("secrets").token_urlsafe(48)
# Use the ignored project media directory. System temp may be outside the
# writable sandbox on Windows, causing image upload tests to fail.
MEDIA_ROOT = BASE_DIR / "media"
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]

# Unit tests never call a real paid provider implicitly.
GEMINI_API_KEY = ""
