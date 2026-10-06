"""URLs del backend administrativo de Petly."""
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.contrib.auth import views as auth_views
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
    mascota_lista,
    mascota_crear,
    mascota_editar,
    mascota_eliminar,
    mascota_cambiar_estado,
    mascota_detalle,
    postulante_lista,
    postulante_detalle,
    solicitud_lista,
    solicitud_detalle,
    solicitud_cambiar_estado,
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
    
    # Autenticación (login/logout)
    path("accounts/login/", auth_views.LoginView.as_view(template_name="registration/login.html"), name="login"),
    path("accounts/logout/", auth_views.LogoutView.as_view(next_page="/"), name="logout"),
    
    # Panel web del albergue (protegido con login)
    path("panel/", login_required(panel_home), name="panel_home"),
    path("panel/albergue/", login_required(albergue_perfil), name="albergue_perfil"),
    path("panel/albergue/infraestructura/", login_required(albergue_infraestructura), name="albergue_infraestructura"),
    path("panel/albergue/infraestructura/<int:infra_id>/eliminar/", login_required(infraestructura_eliminar), name="infraestructura_eliminar"),
    path("panel/albergue/infraestructura/<int:infra_id>/editar/", login_required(infraestructura_editar), name="infraestructura_editar"),
    
    # Panel web - Mascotas
    path("panel/mascotas/", login_required(mascota_lista), name="mascota_lista"),
    path("panel/mascotas/crear/", login_required(mascota_crear), name="mascota_crear"),
    path("panel/mascotas/<int:mascota_id>/editar/", login_required(mascota_editar), name="mascota_editar"),
    path("panel/mascotas/<int:mascota_id>/eliminar/", login_required(mascota_eliminar), name="mascota_eliminar"),
    path("panel/mascotas/<int:mascota_id>/cambiar-estado/", login_required(mascota_cambiar_estado), name="mascota_cambiar_estado"),
    path("panel/mascotas/<int:mascota_id>/detalle/", login_required(mascota_detalle), name="mascota_detalle"),
    
    # Panel web - Postulantes
    path("panel/postulantes/", login_required(postulante_lista), name="postulante_lista"),
    path("panel/postulantes/<int:postulante_id>/detalle/", login_required(postulante_detalle), name="postulante_detalle"),
    
    # Panel web - Solicitudes
    path("panel/solicitudes/", login_required(solicitud_lista), name="solicitud_lista"),
    path("panel/solicitudes/<int:solicitud_id>/detalle/", login_required(solicitud_detalle), name="solicitud_detalle"),
    path("panel/solicitudes/<int:solicitud_id>/cambiar-estado/", login_required(solicitud_cambiar_estado), name="solicitud_cambiar_estado"),
]

# Servir archivos estáticos en entorno de desarrollo
if settings.DEBUG:
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)