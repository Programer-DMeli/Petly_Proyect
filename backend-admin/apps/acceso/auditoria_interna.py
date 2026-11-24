"""Feed interno para sincronizar auditoría Django hacia Spring Boot."""

import secrets

from django.conf import settings
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import EventoAuditoria

TAMANO_LOTE = 100


class ConfirmarEventosAuditoriaSerializer(serializers.Serializer):
    evento_ids = serializers.ListField(
        child=serializers.UUIDField(),
        allow_empty=False,
        max_length=TAMANO_LOTE,
    )


class FeedEventosAuditoriaInternoView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def _autorizar_servicio(self, request):
        esperado = settings.INTERNAL_API_SECRET
        recibido = request.headers.get("X-Internal-Secret", "")
        if not esperado:
            return Response(
                {"detail": "La integración interna de auditoría no está configurada."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        if not recibido or not secrets.compare_digest(recibido, esperado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)
        return None

    def get(self, request):
        no_autorizado = self._autorizar_servicio(request)
        if no_autorizado:
            return no_autorizado

        eventos = EventoAuditoria.objects.filter(
            sincronizado_spring_en__isnull=True,
        ).order_by("ocurrido_en", "id")[:TAMANO_LOTE]
        datos = [{
            "evento_id": str(evento.evento_id),
            "actor_usuario_id": evento.actor_usuario_id,
            "actor_rol": evento.actor_rol,
            "accion": evento.accion,
            "origen": evento.origen,
            "recurso_tipo": evento.recurso_tipo,
            "recurso_id": evento.recurso_id,
            "ocurrido_en": evento.ocurrido_en,
        } for evento in eventos]
        return Response(datos)

    def post(self, request):
        no_autorizado = self._autorizar_servicio(request)
        if no_autorizado:
            return no_autorizado

        serializer = ConfirmarEventosAuditoriaSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        ids = serializer.validated_data["evento_ids"]
        actualizados = EventoAuditoria.objects.filter(
            evento_id__in=ids,
            sincronizado_spring_en__isnull=True,
        ).update(sincronizado_spring_en=timezone.now())
        return Response({"confirmados": actualizados}, status=status.HTTP_200_OK)
