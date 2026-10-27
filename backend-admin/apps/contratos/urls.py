from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import (
    AprobarContratoTokenView,
    ContratoViewSet,
    GenerarContratoView,
    RegistrarEntregaContratoView,
    WebhookFirmaView,
)

router = DefaultRouter()
router.register("", ContratoViewSet, basename="contrato")

urlpatterns = [
    path("webhooks/firma/", WebhookFirmaView.as_view(), name="webhook_firma"),
    path("solicitudes/<int:solicitud_id>/generar/", GenerarContratoView.as_view(), name="generar_contrato"),
    path("solicitudes/<int:solicitud_id>/aprobar-token/", AprobarContratoTokenView.as_view(), name="aprobar_contrato_token"),
    path("solicitudes/<int:solicitud_id>/entrega/", RegistrarEntregaContratoView.as_view(), name="registrar_entrega_contrato"),
    path("", include(router.urls)),
]
