"""Rutas compatibles con el primer contrato de mensajería.

Todas las operaciones delegan al almacenamiento canónico de ``apps.mensajeria``.
"""
from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.mensajeria.serializers import MensajeSerializer
from apps.mensajeria.views import ConversacionViewSet


def _legacy_conversation(data):
    latest = data.get("ultimo_mensaje")
    return {
        "id": data["id"],
        "albergue_id": data["albergue_id"],
        "albergue_nombre": data["albergue_nombre"],
        "interlocutor": data["interlocutor"],
        "ultimo_mensaje": (latest or {}).get("texto", "") if isinstance(latest, dict) else "",
        "creada_en": data["creada_en"],
        "actualizada_en": data["actualizada_en"],
    }


def _legacy_message(data):
    return {
        **data,
        "contenido": data.get("texto", ""),
    }


class ConversacionesView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        result = ConversacionViewSet().list(request)
        if result.status_code >= 400:
            return result
        return Response([_legacy_conversation(row) for row in result.data])

    def post(self, request):
        result = ConversacionViewSet().create(request)
        if result.status_code >= 400:
            return result
        return Response(_legacy_conversation(result.data), status=result.status_code)


class MensajesConversacionView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, pk):
        conversation = ConversacionViewSet()._get(request, pk)
        messages = conversation.mensajes.select_related("remitente").prefetch_related("adjuntos").all()
        return Response([
            _legacy_message(row)
            for row in MensajeSerializer(messages, many=True, context={"request": request}).data
        ])

    def post(self, request, pk):
        result = ConversacionViewSet().mensajes(request, pk=pk)
        if result.status_code >= 400:
            return result
        return Response(_legacy_message(result.data), status=result.status_code)


class MensajeLeidoView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        return ConversacionViewSet().leer(request, pk=pk)


class BloquearConversacionView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        return ConversacionViewSet().bloquear(request, pk=pk)


class ReportarConversacionView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        return ConversacionViewSet().reportar(request, pk=pk)
