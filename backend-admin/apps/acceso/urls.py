"""URLs de acceso (US-18, US-21)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import PostulanteViewSet, SolicitudAdopcionViewSet

router = DefaultRouter()
router.register(r"postulantes", PostulanteViewSet, basename="postulante")
router.register(r"solicitudes", SolicitudAdopcionViewSet, basename="solicitud")

urlpatterns = [
    path("", include(router.urls)),
]