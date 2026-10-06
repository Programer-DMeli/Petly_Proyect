"""URLs de acceso (US-18, US-21)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import (
    PostulanteViewSet,
    SolicitudAdopcionViewSet,
    CustomTokenObtainPairView,
    CustomTokenRefreshView,
    LogoutView,
    UserRegistrationView,
    UserProfileView,
    AdoptanteProfileView,
    PasswordResetRequestView,
    PasswordResetConfirmView,
)

router = DefaultRouter()
router.register(r"postulantes", PostulanteViewSet, basename="postulante")
router.register(r"solicitudes", SolicitudAdopcionViewSet, basename="solicitud")

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
]