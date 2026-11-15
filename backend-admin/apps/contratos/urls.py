from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import ContratoViewSet, GenerarContratoView, RegistrarEntregaContratoView, WebhookFirmaView
from .seguimientos import ExpedienteAlbergueViewSet, SeguimientoPostAdopcionViewSet, SolicitudAsesoriaVeterinariaViewSet
from .reportes import ReporteBienestarPostAdopcionViewSet
from .recordatorios import FeedRecordatoriosSeguimientoView
from .casos_exito import CasoExitoAdopcionViewSet
from .resenas import ElegibilidadResenaView

router = DefaultRouter()
router.register("asesorias-veterinarias", SolicitudAsesoriaVeterinariaViewSet, basename="asesoria-veterinaria")
router.register("reportes-seguimiento", ReporteBienestarPostAdopcionViewSet, basename="reporte-seguimiento")
router.register("casos-exito", CasoExitoAdopcionViewSet, basename="caso-exito")
router.register("expedientes", ExpedienteAlbergueViewSet, basename="expediente-post-adopcion")
router.register("seguimientos", SeguimientoPostAdopcionViewSet, basename="seguimiento-post-adopcion")
router.register("", ContratoViewSet, basename="contrato")

urlpatterns = [
    path("resenas/eligibilidad/", ElegibilidadResenaView.as_view(), name="elegibilidad_resena"),
    path("reportes/feed-recordatorios/", FeedRecordatoriosSeguimientoView.as_view(), name="feed_recordatorios_seguimiento"),
    path("webhooks/firma/", WebhookFirmaView.as_view(), name="webhook_firma"),
    path("solicitudes/<int:solicitud_id>/generar/", GenerarContratoView.as_view(), name="generar_contrato"),
    path("solicitudes/<int:solicitud_id>/entrega/", RegistrarEntregaContratoView.as_view(), name="registrar_entrega_contrato"),
    path("", include(router.urls)),
]
