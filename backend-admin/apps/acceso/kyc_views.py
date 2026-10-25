import mimetypes

from django.http import FileResponse
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

    @action(detail=True, methods=["get"], url_path=r"documentos/(?P<tipo>anverso|reverso)")
    def documento(self, request, pk=None, tipo=None):
        verificacion = self.get_object()
        archivo = getattr(verificacion, f"documento_{tipo}")
        if not archivo:
            return Response({"detail": "El documento solicitado no está disponible."}, status=404)
        return FileResponse(
            archivo.open("rb"),
            as_attachment=True,
            filename=f"dni-{tipo}{'.png' if archivo.name.lower().endswith('.png') else '.jpg'}",
            content_type=mimetypes.guess_type(archivo.name)[0] or "application/octet-stream",
        )
