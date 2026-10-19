from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import ContratoViewSet, GenerarContratoView, WebhookFirmaView

router = DefaultRouter()
router.register("", ContratoViewSet, basename="contrato")

urlpatterns = [
    path("webhooks/firma/", WebhookFirmaView.as_view(), name="webhook_firma"),
    path("solicitudes/<int:solicitud_id>/generar/", GenerarContratoView.as_view(), name="generar_contrato"),
    path("", include(router.urls)),
]
