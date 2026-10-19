"""
Configuración de Django para el backend administrativo de Petly.

Variables de entorno: ver `.env.example`. En desarrollo se puede crear un
archivo `.env` (excluido de Git) que se carga con python-dotenv.
"""
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent

# Carga .env si existe (los valores reales nunca van a Git).
load_dotenv(BASE_DIR / ".env")

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")

DEBUG = os.getenv("DJANGO_DEBUG", "false").lower() in {"1", "true", "yes"}

ALLOWED_HOSTS = [
    host.strip()
    for host in os.getenv("DJANGO_ALLOWED_HOSTS", "localhost,127.0.0.1").split(",")
    if host.strip()
]
# Android Emulator reaches Django with this Host header; local DEBUG only.
if DEBUG and "10.0.2.2" not in ALLOWED_HOSTS:
    ALLOWED_HOSTS.append("10.0.2.2")

# Aplicaciones locales: gestión de albergues, catálogo de mascotas y acceso.
INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    # Terceros
    "rest_framework",
    "corsheaders",
    "rest_framework_simplejwt",
    "drf_spectacular",
    # Petly
    "apps.acceso",
    "apps.albergues",
    "apps.mascotas",
    "apps.contratos",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

# Base de datos: MariaDB de XAMPP, base compartida `database_petly`.
# Credenciales por variables de entorno; ver `.env.example`.
# Los permisos se validan en cada vista: ocultar una pantalla no sustituye
# la comprobación de permisos (PLANNING v2 §5).
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.mysql',
        'NAME': os.getenv('DB_NAME', 'database_petly'),
        'USER': os.getenv('DB_USER', 'root'),
        'PASSWORD': os.getenv('DB_PASSWORD', ''),
        'HOST': os.getenv('DB_HOST', 'localhost'),
        'PORT': os.getenv('DB_PORT', '3306'),
        'OPTIONS': {
            'init_command': "SET sql_mode='STRICT_TRANS_TABLES'",
        },
    }
}

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# Modelo de usuario personalizado con roles (US-21).
AUTH_USER_MODEL = "acceso.User"

LANGUAGE_CODE = "es"
TIME_ZONE = "America/Lima"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"

# --- CORS: orígenes locales explícitos (PLANNING v2 §11) ---
CORS_ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv(
        "DJANGO_ALLOWED_ORIGINS", "http://localhost:5173"
    ).split(",")
    if origin.strip()
]

# Frontend URL para emails de recuperación (US-24)
FRONTEND_URL = os.getenv("FRONTEND_URL", "http://localhost:5173")

# Email para recuperación de contraseña (US-24)
DEFAULT_FROM_EMAIL = os.getenv("DEFAULT_FROM_EMAIL", "noreply@petly.local")
EMAIL_BACKEND = os.getenv("EMAIL_BACKEND", "django.core.mail.backends.filebased.EmailBackend")
EMAIL_FILE_PATH = os.getenv("EMAIL_FILE_PATH", BASE_DIR / "emails")

# SMTP (descomentar y configurar en producción)
# EMAIL_HOST = os.getenv("EMAIL_HOST", "smtp.gmail.com")
# EMAIL_PORT = int(os.getenv("EMAIL_PORT", "587"))
# EMAIL_USE_TLS = os.getenv("EMAIL_USE_TLS", "True") == "True"
# EMAIL_HOST_USER = os.getenv("EMAIL_HOST_USER")
# EMAIL_HOST_PASSWORD = os.getenv("EMAIL_HOST_PASSWORD")

# --- DRF ---
REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "apps.acceso.authentication.SpringBootJWTAuthentication",
    ],
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": "20/min",
        "user": "100/min",
        "auth": "2/min",  # Base rate; custom AuthThrottle handles 5-min window
    },
    # Paginación global (US-12): preserva query params en next/previous
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 20,
    "PAGE_SIZE_QUERY_PARAM": "page_size",
    "MAX_PAGE_SIZE": 100,
}

# Spring Boot es la única autoridad de cuentas de negocio.
SPRING_API_URL = os.getenv("SPRING_API_URL", "http://127.0.0.1:8080").rstrip("/")
SPRING_JWT_SECRET = os.getenv("SPRING_JWT_SECRET", "")
MEDIA_URL = "/media/"
MEDIA_ROOT = BASE_DIR / "media"

# US-03: secreto de servicio para el feed de datos vigentes de mascotas.
PET_COMPATIBILITY_SERVICE_TOKEN = os.getenv("PET_COMPATIBILITY_SERVICE_TOKEN", "")

# US-09: proveedor REST de firma electrónica (el proveedor concreto se configura por entorno).
CONTRACT_SIGNATURE_API_URL = os.getenv("CONTRACT_SIGNATURE_API_URL", "")
CONTRACT_SIGNATURE_API_TOKEN = os.getenv("CONTRACT_SIGNATURE_API_TOKEN", "")
CONTRACT_SIGNATURE_WEBHOOK_SECRET = os.getenv("CONTRACT_SIGNATURE_WEBHOOK_SECRET", "")
CONTRACT_SIGNATURE_TIMEOUT_SECONDS = int(os.getenv("CONTRACT_SIGNATURE_TIMEOUT_SECONDS", "10"))
CONTRACT_SIGNATURE_PROVIDER = os.getenv("CONTRACT_SIGNATURE_PROVIDER", "generic-rest")
CONTRACT_SIGNATURE_ADAPTER_CLASS = os.getenv(
    "CONTRACT_SIGNATURE_ADAPTER_CLASS", "apps.contratos.providers.ProveedorFirmaREST"
)
ADOPTION_CONTRACT_TERMS = os.getenv("ADOPTION_CONTRACT_TERMS", "")
PUBLIC_API_BASE_URL = os.getenv("PUBLIC_API_BASE_URL", "http://localhost:8000")
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
REST_FRAMEWORK["EXCEPTION_HANDLER"] = "apps.acceso.integracion.exception_handler"

# --- Logging ---
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "verbose": {
            "format": "{levelname} {asctime} {module} {message}",
            "style": "{",
        },
        "simple": {
            "format": "{levelname} {message}",
            "style": "{",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "verbose",
        },
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
    "loggers": {
        "django": {
            "handlers": ["console"],
            "level": os.getenv("DJANGO_LOG_LEVEL", "INFO"),
            "propagate": False,
        },
        "apps": {
            "handlers": ["console"],
            "level": "DEBUG",
            "propagate": False,
        },
    },
}

# --- DRF Spectacular (OpenAPI 3 / Swagger UI) ---
SPECTACULAR_SETTINGS = {
    "TITLE": "Petly Admin API",
    "DESCRIPTION": "API para gestión de albergues, mascotas y administración (PLANNING v2).",
    "VERSION": "0.1.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
    "SCHEMA_PATH_PREFIX": "/api/",
    "TAGS": [
        {"name": "albergues", "description": "Gestión de albergues (US-46)"},
        {"name": "mascotas", "description": "Catálogo de mascotas (US-11, US-13)"},
        {"name": "acceso", "description": "Autenticación y tokens (US-21)"},
        {"name": "postulantes", "description": "Consulta de postulantes (US-18)"},
    ],
}
