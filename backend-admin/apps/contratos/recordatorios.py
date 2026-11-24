import secrets
from datetime import timedelta

from django.conf import settings
from django.db.models import Exists, OuterRef
from django.utils import timezone
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento
from .models import ExpedientePostAdopcion, ReporteBienestarPostAdopcion, SeguimientoPostAdopcion


class FeedRecordatoriosSeguimientoView(APIView):
    """Feed interno mínimo para sincronizar el estado de recordatorios postadopción."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        return self.feed(request, registrar_avisos=False)

    def post(self, request):
        return self.feed(request, registrar_avisos=True)

    def feed(self, request, *, registrar_avisos):
        ahora = timezone.now()
        ventana = ahora + timedelta(hours=settings.RECORDATORIOS_ANTICIPACION_HORAS)
        secreto_configurado = settings.INTERNAL_API_SECRET
        secreto_recibido = request.headers.get("X-Internal-Secret", "")
        if not secreto_configurado:
            return Response(
                {"detail": "La integración interna de recordatorios no está configurada."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        if not secreto_recibido or not secrets.compare_digest(secreto_recibido, secreto_configurado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)

        seguimientos = (
            SeguimientoPostAdopcion.objects.filter(
                requiere_reporte=True,
                solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
                solicitud__adoptante__usuario_spring_id__isnull=False,
            )
            .select_related("solicitud__adoptante", "solicitud__mascota")
            .annotate(
                reporte_enviado=Exists(
                    ReporteBienestarPostAdopcion.objects.filter(seguimiento_id=OuterRef("pk"))
                ),
                expediente_cerrado=Exists(
                    ExpedientePostAdopcion.objects.filter(solicitud_id=OuterRef("solicitud_id"), cerrado_en__isnull=False)
                ),
            )
            .order_by("programado_para", "id")
        )

        filas = []
        for seguimiento in seguimientos:
            activo = (
                ahora < seguimiento.programado_para <= ventana
                and seguimiento.estado != SeguimientoPostAdopcion.Estado.CANCELADO
                and not seguimiento.reporte_enviado
                and not seguimiento.expediente_cerrado
            )
            horas_restantes = seguimiento.programado_para - ahora
            tipo = None
            if timedelta(hours=48) < horas_restantes <= timedelta(hours=72):
                tipo = SolicitudEvento.Tipo.RECORDATORIO_REPORTE
            elif timedelta(0) < horas_restantes <= timedelta(hours=24):
                tipo = SolicitudEvento.Tipo.REPORTE_1_DIA
            if registrar_avisos and activo and tipo is not None:
                SolicitudEvento.objects.get_or_create(
                    seguimiento=seguimiento,
                    tipo=tipo,
                    defaults={"solicitud_id": seguimiento.solicitud_id},
                )
            filas.append({
                "id": seguimiento.pk,
                "adoptante_spring_id": seguimiento.solicitud.adoptante.usuario_spring_id,
                "mascota_nombre": seguimiento.solicitud.mascota.nombre,
                "programado_para": seguimiento.programado_para,
                "estado": seguimiento.estado,
                "reporte_enviado": seguimiento.reporte_enviado,
                "expediente_cerrado": seguimiento.expediente_cerrado,
                "recordatorio_activo": activo,
            })
        return Response(filas)
