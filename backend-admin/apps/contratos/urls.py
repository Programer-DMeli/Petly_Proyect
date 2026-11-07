from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import ContratoViewSet, GenerarContratoView, RegistrarEntregaContratoView, WebhookFirmaView
from .seguimientos import SeguimientoPostAdopcionViewSet, SolicitudAsesoriaVeterinariaViewSet
from .reportes import ReporteBienestarPostAdopcionViewSet

router = DefaultRouter()
router.register("asesorias-veterinarias", SolicitudAsesoriaVeterinariaViewSet, basename="asesoria-veterinaria")
router.register("reportes-seguimiento", ReporteBienestarPostAdopcionViewSet, basename="reporte-seguimiento")
router.register("seguimientos", SeguimientoPostAdopcionViewSet, basename="seguimiento-post-adopcion")
router.register("", ContratoViewSet, basename="contrato")

urlpatterns = [
    path("webhooks/firma/", WebhookFirmaView.as_view(), name="webhook_firma"),
    path("solicitudes/<int:solicitud_id>/generar/", GenerarContratoView.as_view(), name="generar_contrato"),
    path("solicitudes/<int:solicitud_id>/entrega/", RegistrarEntregaContratoView.as_view(), name="registrar_entrega_contrato"),
    path("", include(router.urls)),
]
