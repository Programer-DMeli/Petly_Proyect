import secrets

from django.conf import settings
from django.db.models import Exists, OuterRef
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion
from .models import ReporteBienestarPostAdopcion, SeguimientoPostAdopcion


class FeedRecordatoriosSeguimientoView(APIView):
    """Feed interno mínimo para que Spring programe recordatorios postadopción."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def get(self, request):
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
                solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
                solicitud__expediente_post_adopcion__isnull=True,
            )
            .exclude(estado=SeguimientoPostAdopcion.Estado.CANCELADO)
            .select_related("solicitud")
            .annotate(
                reporte_enviado=Exists(
                    ReporteBienestarPostAdopcion.objects.filter(seguimiento_id=OuterRef("pk"))
                )
            )
            .order_by("programado_para", "id")
        )

        return Response([
            {
                "id": seguimiento.pk,
                "adoptante_id": seguimiento.solicitud.adoptante_id,
                "programado_para": seguimiento.programado_para,
                "estado": seguimiento.estado,
                "reporte_enviado": seguimiento.reporte_enviado,
            }
            for seguimiento in seguimientos
        ])
