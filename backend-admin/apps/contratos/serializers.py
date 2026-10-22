from rest_framework import serializers

from .models import ContratoAdopcion


class ContratoAdopcionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    documento_disponible = serializers.SerializerMethodField()

    def get_documento_disponible(self, obj):
        return bool(obj.documento_firmado)

    class Meta:
        model = ContratoAdopcion
        fields = [
            "public_id", "solicitud_id", "estado", "estado_display", "proveedor",
            "proveedor_documento_id", "url_firma", "contenido_html", "contenido_sha256",
            "snapshot", "documento_disponible", "generado_en", "enviado_en", "firmado_en", "expira_en",
            "error_integracion", "actualizado_en",
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
