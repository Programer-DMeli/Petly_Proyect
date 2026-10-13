"""URLs de acceso (US-18, US-21)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import (
    PostulanteViewSet,
    SolicitudAdopcionViewSet,
    AutorizacionViewSet,
    CustomTokenObtainPairView,
    CustomTokenRefreshView,
    LogoutView,
    UserRegistrationView,
    UserProfileView,
    AdoptanteProfileView,
    PasswordResetRequestView,
    PasswordResetConfirmView,
    AutorizacionCheckView,
)
from .integracion import (
    PerfilCompartidoView,
    PerfilesCompartidosListView,
    RetiredBusinessAccessView,
)

router = DefaultRouter()
router.register(r"postulantes", PostulanteViewSet, basename="postulante")
router.register(r"solicitudes", SolicitudAdopcionViewSet, basename="solicitud")
router.register(r"autorizaciones", AutorizacionViewSet, basename="autorizacion")

urlpatterns = [
    path("", include(router.urls)),

    # Auth endpoints (US-21, US-24)
    path("auth/registro/", UserRegistrationView.as_view(), name="auth_registro"),
    path("auth/login/", CustomTokenObtainPairView.as_view(), name="auth_login"),
    path("auth/refresh/", CustomTokenRefreshView.as_view(), name="auth_refresh"),
    path("auth/logout/", LogoutView.as_view(), name="auth_logout"),
    path("auth/me/", UserProfileView.as_view(), name="auth_me"),
    path("auth/me/profile/", AdoptanteProfileView.as_view(), name="auth_me_profile"),
    path("auth/password/reset/", PasswordResetRequestView.as_view(), name="auth_password_reset"),
    path("auth/password/reset/confirm/", PasswordResetConfirmView.as_view(), name="auth_password_reset_confirm"),
    
    # US-18: Perfiles compartidos (albergue consulta adoptantes autorizados)
    path("perfiles-compartidos/", PerfilesCompartidosListView.as_view(), name="perfiles_compartidos_list"),
    path("perfiles-compartidos/<int:adoptante_id>/", PerfilCompartidoView.as_view(), name="perfil_compartido_detail"),
    
    # US-18: Verificación de autorización de perfil (endpoint interno Spring)
    path("interno/autorizacion/", AutorizacionCheckView.as_view(), name="autorizacion_check"),
    
    # Endpoints retirados (fuera de Sprint 1) — 410 Gone
    # Rutas que existían en versión anterior pero ya no se usan
    path("negocio/auth/registro/", RetiredBusinessAccessView.as_view(), name="retired_auth_registro"),
    path("negocio/auth/login/", RetiredBusinessAccessView.as_view(), name="retired_auth_login"),
    path("negocio/postulantes/", RetiredBusinessAccessView.as_view(), name="retired_postulantes"),
    path("negocio/solicitudes/", RetiredBusinessAccessView.as_view(), name="retired_solicitudes"),
    path("negocio/interno/autorizacion/", RetiredBusinessAccessView.as_view(), name="retired_autorizacion_check"),
]