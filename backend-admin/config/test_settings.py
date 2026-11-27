import os

os.environ["PYTHON_DOTENV_DISABLED"] = "1"

from .settings import *
DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}}
SECRET_KEY = __import__("secrets").token_urlsafe(48)
SPRING_JWT_SECRET = __import__("secrets").token_urlsafe(48)
# Use the ignored project media directory. System temp may be outside the
# writable sandbox on Windows, causing image upload tests to fail.
MEDIA_ROOT = BASE_DIR / "media"
PRIVATE_FILES_ROOT = BASE_DIR / "private-media"
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]
PETLY_TEST_MAX_IMAGE_BYTES = 5 * 1024 * 1024

# Unit tests never call a real paid provider implicitly.
GEMINI_API_KEY = ""
SUPABASE_URL = ""
SUPABASE_SECRET_KEY = ""
