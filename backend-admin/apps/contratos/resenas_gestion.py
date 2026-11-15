import secrets

from django.conf import settings
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.albergues.models import Albergue


class AlberguePropioResenasView(APIView):
    """Resuelve la identidad Spring al albergue Django propietario."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        secreto_configurado = settings.INTERNAL_API_SECRET
        secreto_recibido = request.headers.get("X-Internal-Secret", "")
        if not secreto_configurado:
            return Response(
                {"detail": "La integración interna de reseñas no está configurada."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        if not secreto_recibido or not secrets.compare_digest(secreto_recibido, secreto_configurado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)

        try:
            usuario_spring_id = int(request.query_params["usuario_spring_id"])
        except (KeyError, TypeError, ValueError):
            return Response({"detail": "usuario_spring_id debe ser un entero positivo."}, status=400)
        if usuario_spring_id <= 0:
            return Response({"detail": "usuario_spring_id debe ser un entero positivo."}, status=400)

        albergue = Albergue.objects.filter(usuario__usuario_spring_id=usuario_spring_id).only("id").first()
        if albergue is None:
            return Response({"detail": "La cuenta no tiene un albergue asociado."}, status=status.HTTP_404_NOT_FOUND)
        return Response({"albergue_id": albergue.pk})
