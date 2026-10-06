"""Serializadores de mascotas (US-11, US-13)."""
from rest_framework import serializers
from .models import Mascota


class MascotaSerializer(serializers.ModelSerializer):
    """Serializador completo para lectura."""

    especie_display = serializers.CharField(source="get_especie_display", read_only=True)
    sexo_display = serializers.CharField(source="get_sexo_display", read_only=True)
    tamanio_display = serializers.CharField(source="get_tamanio_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    edad_texto = serializers.CharField(read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)

    class Meta:
        model = Mascota
        fields = [
            "id",
            "codigo",
            "nombre",
            "especie",
            "especie_display",
            "raza",
            "sexo",
            "sexo_display",
            "tamanio",
            "tamanio_display",
            "edad_anos",
            "edad_meses",
            "edad_texto",
            "estado",
            "estado_display",
            "descripcion",
            "esterilizado",
            "vacunas_al_dia",
            "desparasitado",
            "necesidades_especiales",
            "foto_principal",
            "fotos_adicionales",
            "fecha_ingreso",
            "fecha_adopcion",
            "albergue",
            "albergue_nombre",
            "creado_por",
            "creado_en",
            "actualizado_en",
        ]
        read_only_fields = [
            "id",
            "codigo",
            "fecha_ingreso",
            "fecha_adopcion",
            "albergue",
            "albergue_nombre",
            "creado_por",
            "creado_en",
            "actualizado_en",
            "especie_display",
            "sexo_display",
            "tamanio_display",
            "estado_display",
            "edad_texto",
        ]


class MascotaListSerializer(serializers.ModelSerializer):
    """Serializador ligero para listados."""

    especie_display = serializers.CharField(source="get_especie_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    edad_texto = serializers.CharField(read_only=True)

    class Meta:
        model = Mascota
        fields = [
            "id",
            "codigo",
            "nombre",
            "especie",
            "especie_display",
            "raza",
            "sexo",
            "tamanio",
            "edad_texto",
            "estado",
            "estado_display",
            "foto_principal",
            "fecha_ingreso",
        ]


class MascotaCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar mascota."""

    class Meta:
        model = Mascota
        fields = [
            "nombre",
            "especie",
            "raza",
            "sexo",
            "tamanio",
            "edad_anos",
            "edad_meses",
            "descripcion",
            "esterilizado",
            "vacunas_al_dia",
            "desparasitado",
            "necesidades_especiales",
            "foto_principal",
            "fotos_adicionales",
        ]

    def validate(self, attrs):
        """Validar que al menos edad_anos o edad_meses esté presente."""
        if not attrs.get("edad_anos") and not attrs.get("edad_meses"):
            raise serializers.ValidationError("Debe especificar al menos años o meses de edad.")
        return attrs


class MascotaEstadoSerializer(serializers.Serializer):
    """Serializador para cambio de estado (US-13)."""

    estado = serializers.ChoiceField(choices=Mascota.Estado.choices)

    def validate_estado(self, value):
        """Validar transiciones de estado permitidas."""
        # Lógica de transición se maneja en la vista
        return value