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
    remitente_spring_id = serializers.IntegerField(source="remitente.usuario_spring_id", read_only=True)
    remitente_nombre = serializers.SerializerMethodField()
    adjuntos = AdjuntoSerializer(many=True, read_only=True)
    propio = serializers.SerializerMethodField()
    leido = serializers.SerializerMethodField()

    class Meta:
        model = Mensaje
        fields = ["id", "remitente_id", "remitente_spring_id", "remitente_nombre", "texto", "contenido", "creado_en", "bienvenida_automatica", "adjuntos", "propio", "leido"]

    def get_contenido(self, obj):
        return obj.texto

    contenido = serializers.SerializerMethodField()

    def get_propio(self, obj):
        request = self.context.get("request")
        return bool(request and obj.remitente_id == request.user.pk)

    def get_remitente_nombre(self, obj):
        return obj.remitente.get_full_name().strip() or ("Albergue" if obj.remitente.rol == "ALBERGUE" else "Adoptante")

    def get_leido(self, obj):
        request = self.context.get("request")
        if not request:
            return False
        conversation = obj.conversacion
        recipient_id = (
            conversation.albergue.usuario_id
            if obj.remitente_id == conversation.adoptante_id
            else conversation.adoptante_id
        )
        read_states_by_conversation = self.context.setdefault("read_states_by_conversation", {})
        read_states = read_states_by_conversation.get(conversation.pk)
        if read_states is None:
            read_states = {participant.usuario_id: participant.leido_hasta for participant in conversation.participantes.all()}
            read_states_by_conversation[conversation.pk] = read_states
        read_at = read_states.get(recipient_id)
        return bool(read_at and obj.creado_en <= read_at)


class ConversacionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    mascota_id = serializers.IntegerField(source="solicitud.mascota_id", read_only=True, default=None)
    albergue_id = serializers.IntegerField(read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    interlocutor = serializers.SerializerMethodField()
    nombre_contacto = serializers.SerializerMethodField()
    mascota = serializers.SerializerMethodField()
    ultimo_mensaje = serializers.SerializerMethodField()
    no_leidos = serializers.SerializerMethodField()
    promedio_respuesta_horas = serializers.SerializerMethodField()
    bloqueada = serializers.SerializerMethodField()
    bloqueada_por_mi = serializers.SerializerMethodField()
    puede_enviar = serializers.SerializerMethodField()

    class Meta:
        model = Conversacion
        fields = ["id", "solicitud_id", "mascota_id", "mascota", "nombre_contacto", "albergue_id", "albergue_nombre", "interlocutor", "creada_en", "actualizada_en", "ultimo_mensaje", "no_leidos", "promedio_respuesta_horas", "bloqueada", "bloqueada_por_mi", "puede_enviar"]

    def get_nombre_contacto(self, obj):
        request = self.context["request"]
        if request.user.pk == obj.adoptante_id:
            return obj.albergue.nombre
        return obj.adoptante.get_full_name().strip() or "Adoptante"

    def get_bloqueada(self, obj):
        return bool(self._bloqueadores(obj))

    def _bloqueadores(self, obj):
        pair = (obj.adoptante_id, obj.albergue_id)
        if "bloqueadores_por_par" in self.context:
            return self.context["bloqueadores_por_par"].get(pair, set())
        cache = getattr(self, "_bloqueadores_por_par", None)
        if cache is None:
            cache = self._bloqueadores_por_par = {}
        if pair not in cache:
            cache[pair] = set(obj.bloqueos_entre_participantes().values_list("bloqueador_id", flat=True))
        return cache[pair]

    def get_bloqueada_por_mi(self, obj):
        request = self.context.get("request")
        return bool(request and request.user.pk in self._bloqueadores(obj))

    def get_puede_enviar(self, obj):
        request = self.context.get("request")
        if not request:
            return False
        return not (self._bloqueadores(obj) - {request.user.pk})

    def get_mascota(self, obj):
        return obj.solicitud.mascota.nombre if obj.solicitud_id else "Consulta general"

    def get_interlocutor(self, obj):
        return self.get_nombre_contacto(obj)

    def get_ultimo_mensaje(self, obj):
        if "ultimos" in self.context:
            message = self.context["ultimos"].get(obj.ultimo_id)
        elif "mensajes_del_hilo" in self.context:
            messages = self.context["mensajes_del_hilo"]
            message = messages[-1] if messages else None
        else:
            message = obj.mensajes.select_related("remitente").prefetch_related("adjuntos").order_by("-creado_en", "-id").first()
        return MensajeSerializer(message, context=self.context).data if message else None

    def get_no_leidos(self, obj):
        if "no_leidos_por_conversacion" in self.context:
            return self.context["no_leidos_por_conversacion"].get(obj.pk, 0)
        request = self.context["request"]
        if "mensajes_del_hilo" in self.context:
            states = self.context["read_states_by_conversation"][obj.pk]
            if request.user.pk not in states:
                return 0
            read_at = states[request.user.pk]
            return sum(
                message.remitente_id != request.user.pk and (not read_at or message.creado_en > read_at)
                for message in self.context["mensajes_del_hilo"]
            )
        participant = obj.participantes.filter(usuario=request.user).first()
        if not participant:
            return 0
        unread = obj.mensajes.exclude(remitente=request.user)
        if participant.leido_hasta:
            unread = unread.filter(creado_en__gt=participant.leido_hasta)
        return unread.count()

    def get_promedio_respuesta_horas(self, obj):
        if "ultimos" in self.context:
            return None
        if "mensajes_del_hilo" in self.context:
            messages = (
                (message.remitente_id, message.creado_en) for message in self.context["mensajes_del_hilo"]
                if not message.bienvenida_automatica
            )
        else:
            messages = obj.mensajes.exclude(bienvenida_automatica=True).order_by("creado_en", "id").values_list("remitente_id", "creado_en")
        waits = []
        runs = []
        for sender_id, sent_at in messages:
            if not runs or runs[-1]["remitente_id"] != sender_id:
                runs.append({"remitente_id": sender_id, "primero": sent_at, "ultimo": sent_at})
            else:
                runs[-1]["ultimo"] = sent_at
        adoptante_id = obj.adoptante_id
        albergue_id = obj.albergue.usuario_id
        for current, following in zip(runs, runs[1:]):
            if current["remitente_id"] != adoptante_id or following["remitente_id"] != albergue_id:
                continue
            elapsed = (following["primero"] - current["ultimo"]).total_seconds() / 3600
            if elapsed >= 0:
                waits.append(elapsed)
        return round(sum(waits) / len(waits), 1) if waits else None
