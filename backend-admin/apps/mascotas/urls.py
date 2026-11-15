"""URLs de mascotas (US-11, US-13)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .afiches import (
    ConsultarPedidoAfiche, CrearPedidoAfiche, EntregarPedidoAfiche, FondoPedidoAfiche, TomarPedidoAfiche,
)
from .views import DatosCompatibilidadView, MascotaViewSet

router = DefaultRouter()
router.register(r"", MascotaViewSet, basename="mascota")

urlpatterns = [
    path("datos-compatibilidad/", DatosCompatibilidadView.as_view(), name="datos_compatibilidad"),
    path("<int:mascota_id>/afiches/", CrearPedidoAfiche.as_view(), name="afiche-crear"),
    path("afiches/<int:pedido_id>/", ConsultarPedidoAfiche.as_view(), name="afiche-consultar"),
    path("afiches/<int:pedido_id>/fondo/", FondoPedidoAfiche.as_view(), name="afiche-fondo"),
    path("interno/afiches/tomar/", TomarPedidoAfiche.as_view(), name="afiche-tomar"),
    path("interno/afiches/<int:pedido_id>/entregar/", EntregarPedidoAfiche.as_view(), name="afiche-entregar"),
    path("", include(router.urls)),
]
