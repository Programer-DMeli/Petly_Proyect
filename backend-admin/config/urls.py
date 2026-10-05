"""URLs del backend administrativo de Petly."""
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path


def health(request):
    """GET /api/health/ — respuesta básica."""
    return JsonResponse({"status": "UP"})


def home(request):
    """GET / — respuesta en la raíz."""
    return JsonResponse({
        "status": "UP",
        "mensaje": "Backend Administrativo Petly corriendo correctamente",
        "endpoints": [
            "/admin/",
            "/api/health/",
            "/api/albergues/",
            "/api/mascotas/",
            "/api/acceso/"
        ]
    })


urlpatterns = [
    path("", home, name="home"),
    path("admin/", admin.site.urls),
    path("api/health/", health, name="health"),
    path("api/albergues/", include("apps.albergues.urls")),
    path("api/mascotas/", include("apps.mascotas.urls")),
    path("api/acceso/", include("apps.acceso.urls")),
]

# Servir archivos estáticos en entorno de desarrollo
if settings.DEBUG:
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)