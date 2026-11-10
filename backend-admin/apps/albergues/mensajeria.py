"""API autenticada para conversaciones entre adoptantes y albergues (US-51)."""
from django.db import transaction
from django.db.models import Prefetch
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import permissions, serializers, status
from rest_framework.exceptions import PermissionDenied
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Albergue, ConversacionAdopcion, MensajeConversacion


class ConversacionSerializer(serializers.ModelSerializer):
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    interlocutor = serializers.SerializerMethodField()
    ultimo_mensaje = serializers.SerializerMethodField()

    class Meta:
        model = ConversacionAdopcion
        fields = ["id", "albergue_id", "albergue_nombre", "interlocutor", "ultimo_mensaje", "creada_en", "actualizada_en"]
        read_only_fields = fields

    def get_interlocutor(self, obj):
        if self.context["request"].user.rol == "ADOPTANTE":
            return obj.albergue.nombre
        nombre = obj.adoptante.get_full_name().strip()
        return nombre or "Adoptante"

    def get_ultimo_mensaje(self, obj):
        mensajes = getattr(obj, "_ultimos_mensajes", None)
        mensaje = mensajes[0] if mensajes else None
        return mensaje.contenido if mensaje else ""


class MensajeSerializer(serializers.ModelSerializer):
    remitente_spring_id = serializers.IntegerField(source="remitente.spring_id", read_only=True)
    remitente_nombre = serializers.SerializerMethodField()

    class Meta:
        model = MensajeConversacion
        fields = ["id", "remitente_spring_id", "remitente_nombre", "contenido", "creado_en"]
        read_only_fields = ["id", "remitente_spring_id", "remitente_nombre", "creado_en"]

    def validate_contenido(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("Escribe un mensaje antes de enviarlo.")
        return value

    def get_remitente_nombre(self, obj):
        return obj.remitente.get_full_name().strip() or (
            "Albergue" if obj.remitente.rol == "ALBERGUE" else "Adoptante"
        )


def _conversaciones_usuario(user):
    if user.rol == "ADOPTANTE":
        queryset = ConversacionAdopcion.objects.filter(adoptante=user)
    elif user.rol == "ALBERGUE" and hasattr(user, "albergue"):
        queryset = ConversacionAdopcion.objects.filter(albergue__usuario=user)
    else:
        raise PermissionDenied("Solo adoptantes y albergues pueden usar la mensajería.")
    return queryset.select_related("albergue", "adoptante").prefetch_related(
        Prefetch(
            "mensajes",
            queryset=MensajeConversacion.objects.order_by("-creado_en", "-id")[:1],
            to_attr="_ultimos_mensajes",
        )
    )


class ConversacionesView(APIView):
    """Lista las conversaciones propias; el adoptante puede iniciar una con un albergue."""

    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        conversaciones = _conversaciones_usuario(request.user)
        return Response(ConversacionSerializer(conversaciones, many=True, context={"request": request}).data)

    @transaction.atomic
    def post(self, request):
        if request.user.rol != "ADOPTANTE":
            raise PermissionDenied("Solo un adoptante puede iniciar una conversación.")
        albergue_id = request.data.get("albergue_id")
        if isinstance(albergue_id, bool) or not str(albergue_id or "").isdigit():
            raise serializers.ValidationError({"albergue_id": "Selecciona un albergue válido."})
        albergue = get_object_or_404(Albergue, pk=int(albergue_id), activo=True)
        conversacion, creada = ConversacionAdopcion.objects.get_or_create(
            adoptante=request.user,
            albergue=albergue,
        )
        conversacion = _conversaciones_usuario(request.user).get(pk=conversacion.pk)
        data = ConversacionSerializer(conversacion, context={"request": request}).data
        return Response(data, status=status.HTTP_201_CREATED if creada else status.HTTP_200_OK)


class MensajesConversacionView(APIView):
    """Lee y envía mensajes; el filtro de participante devuelve 404 a terceros."""

    permission_classes = [permissions.IsAuthenticated]

    def get_conversation(self, request, pk):
        return get_object_or_404(_conversaciones_usuario(request.user), pk=pk)

    def get(self, request, pk):
        conversacion = self.get_conversation(request, pk)
        mensajes = conversacion.mensajes.select_related("remitente").all()
        return Response(MensajeSerializer(mensajes, many=True).data)

    @transaction.atomic
    def post(self, request, pk):
        conversacion = self.get_conversation(request, pk)
        serializer = MensajeSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        mensaje = serializer.save(conversacion=conversacion, remitente=request.user)
        conversacion.actualizada_en = timezone.now()
        conversacion.save(update_fields=["actualizada_en"])
        return Response(MensajeSerializer(mensaje).data, status=status.HTTP_201_CREATED)
