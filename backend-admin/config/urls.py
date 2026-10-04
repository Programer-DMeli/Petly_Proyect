"""URLs del backend administrativo de Petly."""
from django.http import JsonResponse
from django.urls import include, path


def health(request):
    """GET /api/health/ — respuesta básica, sin información sensible."""
    return JsonResponse({"status": "UP"})


urlpatterns = [
    path("api/health/", health, name="health"),
    path("api/albergues/", include("apps.albergues.urls")),
    path("api/mascotas/", include("apps.mascotas.urls")),
    path("api/acceso/", include("apps.acceso.urls")),
]
