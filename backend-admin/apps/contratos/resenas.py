import secrets

from django.conf import settings
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion, User


class ElegibilidadResenaView(APIView):
    """Valida si el albergue cerró una adopción entregada del titular."""

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
            adoptante_id = int(request.query_params["adoptante_id"])
            albergue_id = int(request.query_params["albergue_id"])
        except (KeyError, TypeError, ValueError):
            return Response({"detail": "adoptante_id y albergue_id deben ser enteros positivos."}, status=400)
        if adoptante_id <= 0 or albergue_id <= 0:
            return Response({"detail": "adoptante_id y albergue_id deben ser enteros positivos."}, status=400)

        elegible = SolicitudAdopcion.objects.filter(
            adoptante__usuario_spring_id=adoptante_id,
            adoptante__rol=User.Rol.ADOPTANTE,
            albergue_id=albergue_id,
            estado=SolicitudAdopcion.Estado.ENTREGADA,
            expediente_post_adopcion__cerrado_en__isnull=False,
        ).exists()
        if not elegible:
            return Response({"detail": "El albergue todavía no ha validado el cierre de una adopción entregada."}, status=403)
        return Response({"elegible": True})
