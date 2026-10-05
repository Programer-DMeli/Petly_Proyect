"""URLs del backend administrativo de Petly."""
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.contrib.auth.decorators import login_required
from django.http import JsonResponse
from django.urls import include, path
from django.views.generic import TemplateView

from apps.albergues.panel_views import (
    panel_home,
    albergue_perfil,
    albergue_infraestructura,
    infraestructura_eliminar,
    infraestructura_editar,
)


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
            "/api/acceso/",
            "/panel/",
        ]
    })


urlpatterns = [
    path("", home, name="home"),
    path("admin/", admin.site.urls),
    path("api/health/", health, name="health"),
    path("api/albergues/", include("apps.albergues.urls")),
    path("api/mascotas/", include("apps.mascotas.urls")),
    path("api/acceso/", include("apps.acceso.urls")),
    
    # Panel web del albergue (protegido con login)
    path("panel/", login_required(panel_home), name="panel_home"),
    path("panel/albergue/", login_required(albergue_perfil), name="albergue_perfil"),
    path("panel/albergue/infraestructura/", login_required(albergue_infraestructura), name="albergue_infraestructura"),
    path("panel/albergue/infraestructura/<int:infra_id>/eliminar/", login_required(infraestructura_eliminar), name="infraestructura_eliminar"),
    path("panel/albergue/infraestructura/<int:infra_id>/editar/", login_required(infraestructura_editar), name="infraestructura_editar"),
]

# Servir archivos estáticos en entorno de desarrollo
if settings.DEBUG:
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)