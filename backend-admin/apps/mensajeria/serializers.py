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
    remitente_spring_id = serializers.IntegerField(source="remitente.spring_id", read_only=True)
    remitente_nombre = serializers.SerializerMethodField()
    adjuntos = AdjuntoSerializer(many=True, read_only=True)
    propio = serializers.SerializerMethodField()

    class Meta:
        model = Mensaje
        fields = ["id", "remitente_id", "remitente_spring_id", "remitente_nombre", "texto", "contenido", "creado_en", "bienvenida_automatica", "adjuntos", "propio"]

    def get_contenido(self, obj):
        return obj.texto

    contenido = serializers.SerializerMethodField()

    def get_propio(self, obj):
        request = self.context.get("request")
        return bool(request and obj.remitente_id == request.user.pk)

    def get_remitente_nombre(self, obj):
        return obj.remitente.get_full_name().strip() or ("Albergue" if obj.remitente.rol == "ALBERGUE" else "Adoptante")


class ConversacionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    albergue_id = serializers.IntegerField(read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    interlocutor = serializers.SerializerMethodField()
    nombre_contacto = serializers.SerializerMethodField()
    mascota = serializers.SerializerMethodField()
    ultimo_mensaje = serializers.SerializerMethodField()
    no_leidos = serializers.SerializerMethodField()
    promedio_respuesta_horas = serializers.SerializerMethodField()

    class Meta:
        model = Conversacion
        fields = ["id", "solicitud_id", "mascota", "nombre_contacto", "albergue_id", "albergue_nombre", "interlocutor", "creada_en", "actualizada_en", "ultimo_mensaje", "no_leidos", "promedio_respuesta_horas"]

    def get_nombre_contacto(self, obj):
        request = self.context["request"]
        return obj.albergue.nombre if request.user.pk == obj.adoptante_id else (obj.adoptante.get_full_name() or obj.adoptante.email or obj.adoptante.username)

    def get_mascota(self, obj):
        return obj.solicitud.mascota.nombre if obj.solicitud_id else "Consulta general"

    def get_interlocutor(self, obj):
        return self.get_nombre_contacto(obj)

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
        for current, following in zip(runs, runs[1:]):
            waits.append((following["primero"] - current["ultimo"]).total_seconds() / 3600)
        return round(sum(waits) / len(waits), 1) if waits else None
