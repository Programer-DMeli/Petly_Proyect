from rest_framework import serializers
from .models import AdjuntoMensaje, Conversacion, Mensaje


class AdjuntoSerializer(serializers.ModelSerializer):
    descarga = serializers.SerializerMethodField()

    class Meta:
        model = AdjuntoMensaje
        fields = ["id", "nombre_original", "content_type", "tamaño_bytes", "descarga"]

    def get_descarga(self, obj):
        return f"/api/mensajeria/adjuntos/{obj.pk}/"


class MensajeSerializer(serializers.ModelSerializer):
    remitente_id = serializers.IntegerField(read_only=True)
    adjuntos = AdjuntoSerializer(many=True, read_only=True)
    propio = serializers.SerializerMethodField()
    leido = serializers.SerializerMethodField()

    class Meta:
        model = Mensaje
        fields = ["id", "remitente_id", "texto", "creado_en", "bienvenida_automatica", "adjuntos", "propio", "leido"]

    def get_propio(self, obj):
        request = self.context.get("request")
        return bool(request and obj.remitente_id == request.user.pk)

    def get_leido(self, obj):
        request = self.context.get("request")
        if not request:
            return False
        conversation = obj.conversacion
        recipient_id = (
            conversation.albergue.usuario_id
            if request.user.pk == conversation.adoptante_id
            else conversation.adoptante_id
        )
        read_states_by_conversation = self.context.setdefault("read_states_by_conversation", {})
        read_states = read_states_by_conversation.get(conversation.pk)
        if read_states is None:
            read_states = dict(conversation.participantes.values_list("usuario_id", "leido_hasta"))
            read_states_by_conversation[conversation.pk] = read_states
        read_at = read_states.get(recipient_id)
        return bool(read_at and obj.creado_en <= read_at)


class ConversacionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    nombre_contacto = serializers.SerializerMethodField()
    mascota = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    ultimo_mensaje = serializers.SerializerMethodField()
    no_leidos = serializers.SerializerMethodField()
    promedio_respuesta_horas = serializers.SerializerMethodField()
    bloqueada = serializers.SerializerMethodField()
    puede_enviar = serializers.SerializerMethodField()

    class Meta:
        model = Conversacion
        fields = ["id", "solicitud_id", "mascota", "nombre_contacto", "creada_en", "actualizada_en", "ultimo_mensaje", "no_leidos", "promedio_respuesta_horas", "bloqueada", "puede_enviar"]

    def get_nombre_contacto(self, obj):
        request = self.context["request"]
        if request.user.pk == obj.adoptante_id:
            return obj.albergue.nombre
        return obj.adoptante.get_full_name().strip() or "Adoptante"

    def get_bloqueada(self, obj):
        if hasattr(obj, "tiene_bloqueos"):
            return obj.tiene_bloqueos
        return bool(obj.bloqueos.all())

    def get_puede_enviar(self, obj):
        request = self.context.get("request")
        if not request:
            return False
        return not obj.bloqueos.exclude(bloqueador=request.user).exists()

    def get_ultimo_mensaje(self, obj):
        message = obj.mensajes.order_by("-creado_en", "-id").first()
        return MensajeSerializer(message, context=self.context).data if message else None

    def get_no_leidos(self, obj):
        request = self.context["request"]
        participant = obj.participantes.filter(usuario=request.user).first()
        if not participant:
            return 0
        unread = obj.mensajes.exclude(remitente=request.user)
        if participant.leido_hasta:
            unread = unread.filter(creado_en__gt=participant.leido_hasta)
        return unread.count()

    def get_promedio_respuesta_horas(self, obj):
        messages = list(obj.mensajes.exclude(bienvenida_automatica=True).order_by("creado_en", "id").values("remitente_id", "creado_en"))
        waits = []
        runs = []
        for message in messages:
            if not runs or runs[-1]["remitente_id"] != message["remitente_id"]:
                runs.append({"remitente_id": message["remitente_id"], "primero": message["creado_en"], "ultimo": message["creado_en"]})
            else:
                runs[-1]["ultimo"] = message["creado_en"]
        adoptante_id = obj.adoptante_id
        albergue_id = obj.albergue.usuario_id
        for current, following in zip(runs, runs[1:]):
            if current["remitente_id"] != adoptante_id or following["remitente_id"] != albergue_id:
                continue
            elapsed = (following["primero"] - current["ultimo"]).total_seconds() / 3600
            if elapsed >= 0:
                waits.append(elapsed)
        return round(sum(waits) / len(waits), 1) if waits else None
