"""Serializadores de mascotas (US-11, US-12, US-13)."""
from datetime import date

from django.utils import timezone
from rest_framework import serializers
from django_filters.rest_framework import DjangoFilterBackend
from .models import Mascota, RegistroClinico


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


def validate_evidence_image(value):
    """Validate real JPEG/PNG content as well as the upload size."""
    validate_image_file(value)
    from PIL import Image
    try:
        image = Image.open(value)
        if image.format not in {"JPEG", "PNG"}:
            raise ValueError()
        image.verify()
        value.seek(0)
    except Exception:
        raise serializers.ValidationError("Usa una imagen JPEG o PNG válida.")
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


class RegistroClinicoSerializer(serializers.ModelSerializer):
    id = serializers.IntegerField(required=False, min_value=1)
    fecha = serializers.DateField(input_formats=["iso-8601"])

    class Meta:
        model = RegistroClinico
        fields = ["id", "tipo", "fecha"]

    def validate_fecha(self, fecha):
        if not date(1000, 1, 1) <= fecha <= timezone.localdate():
            raise serializers.ValidationError("Ingresa una fecha válida que no sea futura.")
        return fecha


class MascotaSerializer(serializers.ModelSerializer):
    """Serializador completo para lectura."""

    especie_display = serializers.CharField(source="get_especie_display", read_only=True)
    sexo_display = serializers.CharField(source="get_sexo_display", read_only=True)
    tamanio_display = serializers.CharField(source="get_tamanio_display", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    temperamento_display = serializers.CharField(source="get_temperamento_display", read_only=True)
    edad_texto = serializers.CharField(read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    foto_original_ia = serializers.ImageField(read_only=True)
    foto_mejorada_ia = serializers.ImageField(read_only=True)
    evidencia_vacunacion = serializers.ImageField(read_only=True)
    evidencia_esterilizacion = serializers.ImageField(read_only=True)
    registros_clinicos = serializers.SerializerMethodField()
    alerta_clinica = serializers.SerializerMethodField()

    def _es_albergue_propietario(self, mascota):
        request = self.context.get("request")
        return bool(request and getattr(request.user, "rol", None) == "ALBERGUE"
                    and mascota.albergue.usuario_id == request.user.pk)

    def get_registros_clinicos(self, mascota):
        if not self._es_albergue_propietario(mascota):
            return []
        return RegistroClinicoSerializer(mascota.registros_clinicos.all(), many=True).data

    def get_alerta_clinica(self, mascota) -> str:
        return mascota.alerta_clinica if self._es_albergue_propietario(mascota) else ""

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
            "estado_salud_general",
            "alerta_clinica",
            "evidencia_vacunacion",
            "evidencia_esterilizacion",
            "registros_clinicos",
            "foto_principal",
            "foto_original_ia",
            "foto_mejorada_ia",
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
            "id", "nombre", "especie", "raza", "sexo", "tamanio", "edad_anos",
            "edad_meses", "temperamento", "energia", "estado",
            "esterilizado", "vacunas_al_dia", "desparasitado",
            "necesidades_especiales", "actualizado_en",
        ]
        read_only_fields = fields


class MascotaCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar mascota."""

    foto_principal = serializers.ImageField(
        required=True,
        allow_null=False,
        validators=[validate_image_file]
    )
    foto_mejorada_confirmada = serializers.BooleanField(write_only=True, required=False, default=False)
    evidencia_vacunacion = serializers.ImageField(required=False, allow_null=True, validators=[validate_evidence_image])
    evidencia_esterilizacion = serializers.ImageField(required=False, allow_null=True, validators=[validate_evidence_image])
    eliminar_evidencia_vacunacion = serializers.BooleanField(write_only=True, required=False, default=False)
    eliminar_evidencia_esterilizacion = serializers.BooleanField(write_only=True, required=False, default=False)
    # El formulario multipart envía la lista como JSON junto con las imágenes.
    registros_clinicos = serializers.JSONField(write_only=True, required=False)

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
            "estado_salud_general",
            "alerta_clinica",
            "evidencia_vacunacion",
            "evidencia_esterilizacion",
            "eliminar_evidencia_vacunacion",
            "eliminar_evidencia_esterilizacion",
            "registros_clinicos",
            "foto_principal",
            "foto_mejorada_confirmada",
            "fotos_adicionales",
        ]

    def validate(self, attrs):
        confirmed = attrs.get("foto_mejorada_confirmada", False)
        if confirmed and not attrs.get("foto_principal"):
            raise serializers.ValidationError({
                "foto_principal": "Envía la foto mejorada para registrar su referencia."
            })
        if confirmed and self.instance is None:
            raise serializers.ValidationError({
                "foto_mejorada_confirmada": "La mejora solo se puede confirmar al editar una mascota existente."
            })
        years = attrs.get("edad_anos", getattr(self.instance, "edad_anos", None))
        months = attrs.get("edad_meses", getattr(self.instance, "edad_meses", None))
        if years is not None and not 0 <= years <= 50:
            raise serializers.ValidationError({"edad_anos": "Entre 0 y 50."})
        if months is not None and not 0 <= months <= 11:
            raise serializers.ValidationError({"edad_meses": "Resto de meses entre 0 y 11."})
        if (years or 0) * 12 + (months or 0) > 600:
            raise serializers.ValidationError({"edad_anos": "Edad total máxima: 600 meses."})
        if attrs.get("fotos_adicionales"):
            raise serializers.ValidationError({"fotos_adicionales": "Sprint 1 admite una sola fotografía."})
        return attrs

    def validate_registros_clinicos(self, value):
        if not isinstance(value, list):
            raise serializers.ValidationError("Envía una lista de registros clínicos.")
        serializer = RegistroClinicoSerializer(data=value, many=True)
        if not serializer.is_valid():
            raise serializers.ValidationError([
                f"Registro {index}: {message}"
                for index, errors in enumerate(serializer.errors, start=1)
                for messages in errors.values()
                for message in messages
            ])

        registros = serializer.validated_data
        ids = [registro["id"] for registro in registros if "id" in registro]
        if len(ids) != len(set(ids)):
            raise serializers.ValidationError("Envía cada registro existente una sola vez.")
        if ids and (self.instance is None or len(self.instance.registros_clinicos.in_bulk(ids)) != len(ids)):
            raise serializers.ValidationError("Los registros que se editan deben pertenecer a esta mascota.")
        return registros

    def create(self, validated_data):
        registros = validated_data.pop("registros_clinicos", [])
        validated_data.pop("foto_mejorada_confirmada", None)
        validated_data.pop("eliminar_evidencia_vacunacion", None)
        validated_data.pop("eliminar_evidencia_esterilizacion", None)
        mascota = super().create(validated_data)
        for registro in registros:
            mascota.registros_clinicos.create(**registro)
        return mascota

    def update(self, instance, validated_data):
        registros = validated_data.pop("registros_clinicos", [])
        improved_confirmed = validated_data.pop("foto_mejorada_confirmada", False)
        photo_replaced = "foto_principal" in validated_data
        remove_vaccination = validated_data.pop("eliminar_evidencia_vacunacion", False)
        remove_sterilization = validated_data.pop("eliminar_evidencia_esterilizacion", False)
        if remove_vaccination:
            instance.evidencia_vacunacion.delete(save=False)
            validated_data.pop("evidencia_vacunacion", None)
        if remove_sterilization:
            instance.evidencia_esterilizacion.delete(save=False)
            validated_data.pop("evidencia_esterilizacion", None)
        original_photo_name = instance.foto_principal.name if improved_confirmed and instance.foto_principal else None
        instance = super().update(instance, validated_data)
        for registro in registros:
            registro_id = registro.pop("id", None)
            if registro_id is None:
                instance.registros_clinicos.create(**registro)
            else:
                instance.registros_clinicos.filter(pk=registro_id).update(**registro)
        if improved_confirmed:
            # Reuse the stored names: the original file remains in storage and
            # the improved upload is already saved as foto_principal.
            instance.foto_original_ia.name = original_photo_name
            instance.foto_mejorada_ia.name = instance.foto_principal.name
            instance.save(update_fields=["foto_original_ia", "foto_mejorada_ia"])
        elif photo_replaced:
            # A manually uploaded replacement is not the previously accepted
            # IA proposal; avoid exposing stale provenance for the new photo.
            instance.foto_original_ia = None
            instance.foto_mejorada_ia = None
            instance.save(update_fields=["foto_original_ia", "foto_mejorada_ia"])
        return instance

    def validate_foto_principal(self, photo):
        if photo is None:
            return None
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


class MascotaDescripcionBorradorSerializer(serializers.Serializer):
    """Rasgos del formulario usados para generar una descripción antes de guardar."""

    nombre = serializers.CharField(max_length=100)
    especie = serializers.ChoiceField(choices=Mascota.Especie.choices)
    raza = serializers.CharField(max_length=100, required=False, allow_blank=True)
    tamanio = serializers.ChoiceField(choices=Mascota.Tamanio.choices, required=False, allow_blank=True)
    edad_anos = serializers.IntegerField(required=False, allow_null=True, min_value=0, max_value=50)
    edad_meses = serializers.IntegerField(required=False, allow_null=True, min_value=0, max_value=11)
    temperamento = serializers.ChoiceField(choices=Mascota.Temperamento.choices, required=False)
    energia = serializers.ChoiceField(
        choices=[("BAJA", "Baja"), ("MEDIA", "Media"), ("ALTA", "Alta"), ("POR_EVALUAR", "Por evaluar")],
        required=False,
    )
    descripcion = serializers.CharField(max_length=2000, required=False, allow_blank=True)

    def validate(self, attrs):
        years = attrs.get("edad_anos", getattr(self.instance, "edad_anos", None))
        months = attrs.get("edad_meses", getattr(self.instance, "edad_meses", None))
        if years is not None and not 0 <= years <= 50:
            raise serializers.ValidationError({"edad_anos": "Entre 0 y 50."})
        if months is not None and not 0 <= months <= 11:
            raise serializers.ValidationError({"edad_anos": "Resto de meses entre 0 y 11."})
        if (years or 0) * 12 + (months or 0) > 600:
            raise serializers.ValidationError({"edad_anos": "Edad total máxima: 600 meses."})
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
