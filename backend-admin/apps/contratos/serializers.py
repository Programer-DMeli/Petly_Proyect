from rest_framework import serializers

from .models import AprobacionContratoCorreo, ContratoAdopcion


class ContratoAdopcionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    documento_disponible = serializers.SerializerMethodField()
    aprobacion_correo = serializers.SerializerMethodField()

    def get_documento_disponible(self, obj):
        return bool(obj.documento_firmado) or (
            obj.proveedor == "email-approval" and obj.estado == ContratoAdopcion.Estado.APROBADO
        )

    def get_aprobacion_correo(self, obj):
        approval = getattr(obj, "aprobacion_correo", None)
        if approval is None:
            return None
        return {
            "correo": approval.correo,
            "estado": approval.estado,
            "expira_en": approval.expira_en,
            "intentos_fallidos": approval.intentos_fallidos,
        }

    class Meta:
        model = ContratoAdopcion
        fields = [
            "public_id", "solicitud_id", "estado", "estado_display", "proveedor",
            "proveedor_documento_id", "url_firma", "contenido_html", "contenido_sha256",
            "snapshot", "generado_en", "enviado_en", "firmado_en", "expira_en",
            "aprobado_en", "aprobacion_correo", "error_integracion", "actualizado_en", "documento_disponible",
        ]
        read_only_fields = fields


class EventoProveedorSerializer(serializers.Serializer):
    event_id = serializers.CharField(max_length=160)
    document_id = serializers.CharField(max_length=160)
    status = serializers.CharField(max_length=32)
    occurred_at = serializers.DateTimeField(required=False)

    def validate_status(self, value):
        normalized = value.strip().lower()
        allowed = {
            "sent", "pending", "viewed", "in_progress", "signed", "completed",
            "declined", "rejected", "expired",
        }
        if normalized not in allowed:
            raise serializers.ValidationError("Estado de firma no reconocido.")
        return normalized
