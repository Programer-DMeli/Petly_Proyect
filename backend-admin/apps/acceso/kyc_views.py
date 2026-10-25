import mimetypes

from django.db import transaction
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.response import Response

from .models import User, VerificacionKyc
from .kyc_serializers import VerificacionKycSerializer


class VerificacionKycViewSet(viewsets.ModelViewSet):
    """El adoptante consulta su KYC; solo el titular o ADMIN descarga sus documentos."""

    serializer_class = VerificacionKycSerializer
    permission_classes = [permissions.IsAuthenticated]
    http_method_names = ["get", "post", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = VerificacionKyc.objects.select_related("adoptante")
        if user.is_staff or user.is_admin_user:
            return qs
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(adoptante=user)
        return qs.none()

    def create(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante titular puede presentar sus documentos KYC.")
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        instance = serializer.save(adoptante=request.user)
        return Response(self.get_serializer(instance).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], url_path="revision")
    def revision(self, request, pk=None):
        if not (request.user.is_staff or request.user.is_admin_user):
            raise PermissionDenied("Solo ADMIN puede resolver verificaciones KYC.")
        decision = request.data.get("decision")
        motivo = str(request.data.get("motivo", "")).strip()
        if decision not in {"APROBADO_MANUAL", "RECHAZADO_MANUAL"}:
            return Response({"detail": "La decisión debe ser APROBADO_MANUAL o RECHAZADO_MANUAL."}, status=400)
        if not motivo or len(motivo) > 500:
            return Response({"detail": "Ingresa un motivo de hasta 500 caracteres."}, status=400)
        with transaction.atomic():
            verificacion = get_object_or_404(self.get_queryset().select_for_update(), pk=pk)
            if verificacion.estado != VerificacionKyc.Estado.PENDIENTE_REVISION:
                return Response({"detail": "Solo se puede revisar un caso pendiente de revisión."}, status=409)
            verificacion.estado = decision
            verificacion.motivo_revision = motivo
            verificacion.fecha_revision = timezone.now()
            verificacion.revisado_por = request.user
            verificacion.save(update_fields=[
                "estado", "motivo_revision", "fecha_revision", "revisado_por", "actualizado_en",
            ])
        return Response(self.get_serializer(verificacion).data)

    @action(detail=True, methods=["get"], url_path=r"documentos/(?P<tipo>anverso|reverso)")
    def documento(self, request, pk=None, tipo=None):
        verificacion = self.get_object()
        archivo = getattr(verificacion, f"documento_{tipo}")
        if not archivo:
            return Response({"detail": "El documento solicitado no está disponible."}, status=404)
        response = FileResponse(
            archivo.open("rb"),
            as_attachment=True,
            filename=f"dni-{tipo}{'.png' if archivo.name.lower().endswith('.png') else '.jpg'}",
            content_type=mimetypes.guess_type(archivo.name)[0] or "application/octet-stream",
        )
        response["Cache-Control"] = "private, no-store"
        response["X-Content-Type-Options"] = "nosniff"
        return response
