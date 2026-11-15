import secrets

from django.conf import settings
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import CasoExitoAdopcion


class CasoExitoVisibleInternaView(APIView):
    """Confirma a Spring que una historia puede recibir interacciones públicas."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def get(self, request, caso_id):
        secreto_configurado = settings.INTERNAL_API_SECRET
        secreto_recibido = request.headers.get("X-Internal-Secret", "")
        if not secreto_configurado:
            return Response(
                {"detail": "La integración interna de historias no está configurada."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        if not secreto_recibido or not secrets.compare_digest(secreto_recibido, secreto_configurado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)

        visible = CasoExitoAdopcion.objects.filter(
            pk=caso_id,
            estado=CasoExitoAdopcion.Estado.APROBADO,
            publicado_en__isnull=False,
        ).exists()
        if not visible:
            return Response({"detail": "La historia no está publicada."}, status=status.HTTP_404_NOT_FOUND)
        return Response({"visible": True})
