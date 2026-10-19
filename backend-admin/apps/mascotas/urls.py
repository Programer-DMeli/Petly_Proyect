"""URLs de mascotas (US-11, US-13)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import DatosCompatibilidadView, MascotaViewSet

router = DefaultRouter()
router.register(r"", MascotaViewSet, basename="mascota")

urlpatterns = [
    path("datos-compatibilidad/", DatosCompatibilidadView.as_view(), name="datos_compatibilidad"),
    path("", include(router.urls)),
]
