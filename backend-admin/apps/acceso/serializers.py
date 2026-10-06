"""Serializadores de acceso y postulantes (US-18, US-21)."""
from rest_framework import serializers
from .models import Postulante, SolicitudAdopcion
from apps.mascotas.serializers import MascotaListSerializer


class PostulanteSerializer(serializers.ModelSerializer):
    """Serializador completo para postulante."""

    nombre_completo = serializers.CharField(read_only=True)
    total_solicitudes = serializers.IntegerField(read_only=True)
    solicitudes_aprobadas = serializers.IntegerField(read_only=True)
    solicitudes_pendientes = serializers.IntegerField(read_only=True)

    class Meta:
        model = Postulante
        fields = [
            "id",
            "documento_tipo",
            "documento_numero",
            "nombres",
            "apellidos",
            "nombre_completo",
            "email",
            "telefono",
            "fecha_nacimiento",
            "direccion",
            "distrito",
            "provincia",
            "departamento",
            "tipo_vivienda",
            "tiene_patio",
            "tiene_otras_mascotas",
            "detalles_otras_mascotas",
            "experiencia_previa",
            "motivo_adopcion",
            "preferencia_especie",
            "preferencia_tamanio",
            "preferencia_edad",
            "estado",
            "observaciones_internas",
            "total_solicitudes",
            "solicitudes_aprobadas",
            "solicitudes_pendientes",
            "creado_por",
            "creado_en",
            "actualizado_en",
        ]
        read_only_fields = [
            "id", "nombre_completo", "total_solicitudes",
            "solicitudes_aprobadas", "solicitudes_pendientes",
            "creado_por", "creado_en", "actualizado_en",
        ]


class PostulanteListSerializer(serializers.ModelSerializer):
    """Serializador ligero para listados."""

    nombre_completo = serializers.CharField(read_only=True)
    total_solicitudes = serializers.IntegerField(read_only=True)
    solicitudes_aprobadas = serializers.IntegerField(read_only=True)

    class Meta:
        model = Postulante
        fields = [
            "id",
            "documento_numero",
            "nombre_completo",
            "email",
            "telefono",
            "estado",
            "total_solicitudes",
            "solicitudes_aprobadas",
            "creado_en",
        ]


class PostulanteCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar postulante."""

    class Meta:
        model = Postulante
        fields = [
            "documento_tipo",
            "documento_numero",
            "nombres",
            "apellidos",
            "email",
            "telefono",
            "fecha_nacimiento",
            "direccion",
            "distrito",
            "provincia",
            "departamento",
            "tipo_vivienda",
            "tiene_patio",
            "tiene_otras_mascotas",
            "detalles_otras_mascotas",
            "experiencia_previa",
            "motivo_adopcion",
            "preferencia_especie",
            "preferencia_tamanio",
            "preferencia_edad",
            "estado",
            "observaciones_internas",
        ]


class SolicitudAdopcionSerializer(serializers.ModelSerializer):
    """Serializador completo para solicitud de adopción."""

    postulante_nombre = serializers.CharField(source="postulante.nombre_completo", read_only=True)
    postulante_documento = serializers.CharField(source="postulante.documento_numero", read_only=True)
    mascota_nombre = serializers.CharField(source="mascota.nombre", read_only=True)
    mascota_codigo = serializers.CharField(source="mascota.codigo", read_only=True)
    mascota_especie = serializers.CharField(source="mascota.get_especie_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    revisado_por_nombre = serializers.CharField(source="revisado_por.get_full_name", read_only=True)

    class Meta:
        model = SolicitudAdopcion
        fields = [
            "id",
            "postulante",
            "postulante_nombre",
            "postulante_documento",
            "mascota",
            "mascota_nombre",
            "mascota_codigo",
            "mascota_especie",
            "albergue",
            "estado",
            "estado_display",
            "mensaje_postulante",
            "respuestas_cuestionario",
            "revisado_por",
            "revisado_por_nombre",
            "fecha_revision",
            "observaciones_revision",
            "fecha_solicitud",
            "fecha_entrega",
            "actualizado_en",
        ]
        read_only_fields = [
            "id", "albergue", "fecha_solicitud", "actualizado_en",
            "postulante_nombre", "postulante_documento",
            "mascota_nombre", "mascota_codigo", "mascota_especie",
            "revisado_por_nombre", "estado_display",
        ]


class SolicitudAdopcionListSerializer(serializers.ModelSerializer):
    """Serializador ligero para listados."""

    postulante_nombre = serializers.CharField(source="postulante.nombre_completo", read_only=True)
    postulante_documento = serializers.CharField(source="postulante.documento_numero", read_only=True)
    mascota_nombre = serializers.CharField(source="mascota.nombre", read_only=True)
    mascota_codigo = serializers.CharField(source="mascota.codigo", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)

    class Meta:
        model = SolicitudAdopcion
        fields = [
            "id",
            "postulante",
            "postulante_nombre",
            "postulante_documento",
            "mascota",
            "mascota_nombre",
            "mascota_codigo",
            "estado",
            "estado_display",
            "fecha_solicitud",
            "fecha_revision",
        ]


class SolicitudEstadoSerializer(serializers.Serializer):
    """Serializador para cambio de estado de solicitud."""

    estado = serializers.ChoiceField(choices=SolicitudAdopcion.Estado.choices)
    observaciones_revision = serializers.CharField(required=False, allow_blank=True)

    def validate_estado(self, value):
        # Validación de transiciones se hace en la vista
        return value


class SolicitudCreateSerializer(serializers.ModelSerializer):
    """Serializador para crear solicitud (desde frontend público)."""

    class Meta:
        model = SolicitudAdopcion
        fields = [
            "mascota",
            "mensaje_postulante",
            "respuestas_cuestionario",
        ]

    def create(self, validated_data):
        # El postulante se asigna desde el usuario autenticado o sesión
        # El albergue se deduce de la mascota
        return super().create(validated_data)