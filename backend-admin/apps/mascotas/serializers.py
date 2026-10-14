"""Serializadores de mascotas (US-11, US-12, US-13)."""
from rest_framework import serializers
from django_filters.rest_framework import DjangoFilterBackend
from .models import Mascota


def validate_image_file(value):
    """Valida que el archivo sea JPEG/PNG y no supere 5 MB."""
    max_size = 5 * 1024 * 1024  # 5 MB
    if value.size > max_size:
        raise serializers.ValidationError("La imagen no debe superar 5 MB.")
    
    content_type = getattr(value, 'content_type', '')
    if content_type not in ['image/jpeg', 'image/png']:
        # Fallback: verificar por extensión si no hay content_type
        name = value.name.lower()
        if not (name.endswith('.jpg') or name.endswith('.jpeg') or name.endswith('.png')):
            raise serializers.ValidationError("Solo se permiten archivos JPEG o PNG.")
    
    return value


class MascotaFilter(DjangoFilterBackend):
    """Filtros personalizados para Mascota (US-12)."""
    
    def filter_queryset(self, request, queryset, view):
        # Especie
        especie = request.query_params.get("especie")
        if especie:
            queryset = queryset.filter(especie=especie)
        
        # Tamaño
        tamanio = request.query_params.get("tamanio")
        if tamanio:
            queryset = queryset.filter(tamanio=tamanio)
        
        # Temperamento
        temperamento = request.query_params.get("temperamento")
        if temperamento:
            queryset = queryset.filter(temperamento=temperamento)
        
        # Mismos meses totales que el catálogo Spring; edad desconocida no coincide.
        from django.db.models import Case, When, Value, IntegerField, F, Q
        from django.db.models.functions import Coalesce
        total = Case(
            When(Q(edad_anos__isnull=True) & Q(edad_meses__isnull=True), then=Value(None)),
            default=Coalesce(F("edad_anos"), Value(0)) * 12 + Coalesce(F("edad_meses"), Value(0)),
            output_field=IntegerField(),
        )
        bounds = {}
        for key, lookup in [("edad_min_meses", "gte"), ("edad_max_meses", "lte")]:
            raw = request.query_params.get(key)
            if raw is not None and raw != "":
                try:
                    number = int(raw)
                    if not 0 <= number <= 600:
                        raise ValueError()
                    bounds[lookup] = number
                except ValueError:
                    raise serializers.ValidationError({key: "Usa meses enteros entre 0 y 600."})
        if bounds.get("gte", 0) > bounds.get("lte", 600):
            raise serializers.ValidationError({"edad_min_meses": "La edad mínima supera la máxima."})
        if bounds:
            queryset = queryset.annotate(edad_total=total)
            for lookup, number in bounds.items():
                queryset = queryset.filter(**{"edad_total__" + lookup: number})

        # Estado
        estado = request.query_params.get("estado")
        if estado:
            queryset = queryset.filter(estado=estado)
        
        return queryset


class MascotaSerializer(serializers.ModelSerializer):
    """Serializador completo para lectura."""

    especie_display = serializers.CharField(source="get_especie_display", read_only=True)
    sexo_display = serializers.CharField(source="get_sexo_display", read_only=True)
    tamanio_display = serializers.CharField(source="get_tamanio_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    temperamento_display = serializers.CharField(source="get_temperamento_display", read_only=True)
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
            "temperamento",
            "energia",
            "temperamento_display",
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
            "temperamento_display",
            "estado_display",
            "edad_texto",
        ]


class MascotaListSerializer(serializers.ModelSerializer):
    """Serializador ligero para listados."""

    especie_display = serializers.CharField(source="get_especie_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    edad_texto = serializers.CharField(read_only=True)
    temperamento_display = serializers.CharField(source="get_temperamento_display", read_only=True)

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
            "temperamento",
            "energia",
            "temperamento_display",
            "estado",
            "estado_display",
            "foto_principal",
            "fecha_ingreso",
        ]


class MascotaCompatibilidadSerializer(serializers.ModelSerializer):
    """Datos existentes que Spring Boot necesita para evaluar compatibilidad."""

    class Meta:
        model = Mascota
        fields = [
            "id", "especie", "raza", "sexo", "tamanio", "edad_anos",
            "edad_meses", "temperamento", "energia", "estado",
            "esterilizado", "vacunas_al_dia", "desparasitado",
            "necesidades_especiales",
        ]
        read_only_fields = fields


class MascotaCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar mascota."""

    foto_principal = serializers.ImageField(
        required=False,
        allow_null=True,
        validators=[validate_image_file]
    )

    class Meta:
        model = Mascota
        fields = [
            "nombre",
            "especie",
            "raza",
            "sexo",
            "tamanio",
            "temperamento",
            "energia",
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

    def validate_foto_principal(self, photo):
        if photo is None:
            raise serializers.ValidationError("La fotografía es obligatoria.")
        if photo.size > 5 * 1024 * 1024:
            raise serializers.ValidationError("Máximo 5 MB.")
        from PIL import Image
        try:
            image = Image.open(photo)
            if image.format not in {"JPEG", "PNG"}:
                raise ValueError()
            image.verify()
            photo.seek(0)
        except Exception:
            raise serializers.ValidationError("Usa una fotografía JPEG o PNG válida.")
        return photo

    def validate(self, attrs):
        years = attrs.get("edad_anos", getattr(self.instance, "edad_anos", None))
        months = attrs.get("edad_meses", getattr(self.instance, "edad_meses", None))
        if years is not None and not 0 <= years <= 50:
            raise serializers.ValidationError({"edad_anos": "Entre 0 y 50."})
        if months is not None and not 0 <= months <= 11:
            raise serializers.ValidationError({"edad_meses": "Resto de meses entre 0 y 11."})
        if (years or 0) * 12 + (months or 0) > 600:
            raise serializers.ValidationError({"edad_anos": "Edad total máxima: 600 meses."})
        if self.instance is None and not attrs.get("foto_principal"):
            raise serializers.ValidationError({"foto_principal": "Una fotografía es obligatoria."})
        if attrs.get("fotos_adicionales"):
            raise serializers.ValidationError({"fotos_adicionales": "Sprint 1 admite una sola fotografía."})
        return attrs


class MascotaEstadoSerializer(serializers.Serializer):
    """Serializador para cambio de estado (US-13)."""

    estado = serializers.ChoiceField(choices=Mascota.Estado.choices)

    def validate_estado(self, value):
        """Validar transiciones de estado permitidas."""
        # Lógica de transición se maneja en la vista
        return value
