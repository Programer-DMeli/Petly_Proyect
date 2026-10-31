"""URLs de acceso (US-18, US-21)."""
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import (
    AdoptanteProfileView,
    AutorizacionCheckView,
    AutorizacionViewSet,
    CustomTokenObtainPairView,
    CustomTokenRefreshView,
    LogoutView,
    PasswordResetConfirmView,
    PasswordResetRequestView,
    PostulanteViewSet,
    SolicitudAdopcionViewSet,
    UserProfileView,
    UserRegistrationView,
)
from .kyc_views import VerificacionKycViewSet
from .integracion import (
    PerfilCompartidoView,
    PerfilesCompartidosListView,
    RetiredBusinessAccessView,
)

router = DefaultRouter()
router.register("postulantes", PostulanteViewSet, basename="postulante")
router.register("solicitudes", SolicitudAdopcionViewSet, basename="solicitud")
router.register("verificaciones-kyc", VerificacionKycViewSet, basename="verificacion-kyc")

from .fotos_hogar import FotosHogarView, FotoHogarView

urlpatterns = [
    path("solicitudes/<int:pk>/fotos-hogar/", FotosHogarView.as_view()),
    path("solicitudes/<int:pk>/fotos-hogar/<int:foto_id>/", FotoHogarView.as_view()),
    path("", include(router.urls)),
    path("auth/registro/", UserRegistrationView.as_view(), name="auth_registro"),
    path("auth/login/", CustomTokenObtainPairView.as_view(), name="auth_login"),
    path("auth/refresh/", CustomTokenRefreshView.as_view(), name="auth_refresh"),
    path("auth/logout/", LogoutView.as_view(), name="auth_logout"),
    path("auth/me/", UserProfileView.as_view(), name="auth_me"),
    path("auth/me/profile/", AdoptanteProfileView.as_view(), name="auth_me_profile"),
    path("mi-perfil/", AdoptanteProfileView.as_view(), name="mi_perfil_adoptante"),
    path("auth/password/reset/", PasswordResetRequestView.as_view(), name="auth_password_reset"),
    path(
        "auth/password/reset/confirm/",
        PasswordResetConfirmView.as_view(),
        name="auth_password_reset_confirm",
    ),
    path("perfiles-compartidos/", PerfilesCompartidosListView.as_view(), name="perfiles_compartidos_list"),
    path(
        "perfiles-compartidos/<int:adoptante_id>/",
        PerfilCompartidoView.as_view(),
        name="perfil_compartido_detail",
    ),
    path("interno/autorizacion/", AutorizacionCheckView.as_view(), name="autorizacion_check"),
    path("negocio/auth/registro/", RetiredBusinessAccessView.as_view(), name="retired_auth_registro"),
    path("negocio/auth/login/", RetiredBusinessAccessView.as_view(), name="retired_auth_login"),
    path("negocio/postulantes/", RetiredBusinessAccessView.as_view(), name="retired_postulantes"),
    path("negocio/solicitudes/", RetiredBusinessAccessView.as_view(), name="retired_solicitudes"),
    path(
        "negocio/interno/autorizacion/",
        RetiredBusinessAccessView.as_view(),
        name="retired_autorizacion_check",
    ),
    path("<path:unused>", RetiredBusinessAccessView.as_view()),
]
