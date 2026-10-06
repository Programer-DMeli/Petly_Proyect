"""Serializadores de albergues (US-46)."""
from rest_framework import serializers
from .models import Albergue, Infraestructura


class InfraestructuraSerializer(serializers.ModelSerializer):
    """Serializador para infraestructura del albergue."""

    tipo_display = serializers.CharField(source="get_tipo_display", read_only=True)

    class Meta:
        model = Infraestructura
        fields = [
            "id",
            "tipo",
            "tipo_display",
            "nombre",
            "descripcion",
            "metros_cuadrados",
            "capacidad",
            "equipamiento",
            "activo",
            "creado_en",
            "actualizado_en",
        ]
        read_only_fields = ["id", "creado_en", "actualizado_en", "tipo_display"]


class AlbergueSerializer(serializers.ModelSerializer):
    """Serializador para el perfil del albergue."""

    infraestructura = InfraestructuraSerializer(many=True, read_only=True)
    usuario_id = serializers.IntegerField(source="usuario.id", read_only=True)
    usuario_username = serializers.CharField(source="usuario.username", read_only=True)

    class Meta:
        model = Albergue
        fields = [
            "id",
            "nombre",
            "descripcion",
            "direccion",
            "telefono",
            "email",
            "sitio_web",
            "capacidad_maxima",
            "horario_atencion",
            "responsable_nombre",
            "responsable_telefono",
            "responsable_email",
            "usuario_id",
            "usuario_username",
            "activo",
            "creado_en",
            "actualizado_en",
            "infraestructura",
        ]
        read_only_fields = [
            "id",
            "usuario_id",
            "usuario_username",
            "creado_en",
            "actualizado_en",
            "infraestructura",
        ]


class AlbergueCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar albergue (sin campos de solo lectura)."""

    class Meta:
        model = Albergue
        fields = [
            "nombre",
            "descripcion",
            "direccion",
            "telefono",
            "email",
            "sitio_web",
            "capacidad_maxima",
            "horario_atencion",
            "responsable_nombre",
            "responsable_telefono",
            "responsable_email",
        ]


class InfraestructuraCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar infraestructura."""

    class Meta:
        model = Infraestructura
        fields = [
            "tipo",
            "nombre",
            "descripcion",
            "metros_cuadrados",
            "capacidad",
            "equipamiento",
            "activo",
        ]