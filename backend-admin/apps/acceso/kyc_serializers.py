from django.db import transaction
from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from .models import VerificacionKyc


MAX_DOCUMENT_BYTES = 5 * 1024 * 1024


class VerificacionKycSerializer(serializers.ModelSerializer):
    documento_anverso = serializers.ImageField(write_only=True, required=True)
    documento_reverso = serializers.ImageField(write_only=True, required=True)

    class Meta:
        model = VerificacionKyc
        fields = [
            "id", "dni", "nombres", "apellidos", "estado", "coincide",
            "proveedor", "referencia_reniec", "fecha_consulta", "error_servicio",
            "ultimo_intento_en",
            "documento_anverso", "documento_reverso", "creado_en", "actualizado_en",
        ]
        read_only_fields = [
            "id", "estado", "coincide", "proveedor", "referencia_reniec",
            "fecha_consulta", "error_servicio", "creado_en", "actualizado_en",
            "ultimo_intento_en",
        ]

    def validate_dni(self, value):
        value = value.strip()
        if len(value) != 8 or not value.isascii() or not value.isdigit():
            raise serializers.ValidationError("El DNI debe contener exactamente ocho dígitos.")
        return value

    def validate(self, attrs):
        for field in ("documento_anverso", "documento_reverso"):
            document = attrs.get(field)
            if document and document.size > MAX_DOCUMENT_BYTES:
                raise ValidationError({field: "Cada imagen debe pesar como máximo 5 MB."})
        attrs["nombres"] = attrs["nombres"].strip()
        attrs["apellidos"] = attrs["apellidos"].strip()
        if not attrs["nombres"] or not attrs["apellidos"]:
            raise ValidationError("Ingresa los nombres y apellidos tal como aparecen en el DNI.")
        return attrs

    @transaction.atomic
    def create(self, validated_data):
        adoptante = validated_data.pop("adoptante")
        previous = VerificacionKyc.objects.select_for_update().filter(adoptante=adoptante).first()
        if previous and previous.estado == VerificacionKyc.Estado.VERIFICADO:
            raise ValidationError({"detail": "La identidad ya está verificada."})

        old_files = []
        if previous:
            for field in ("documento_anverso", "documento_reverso"):
                old_file = getattr(previous, field)
                if old_file:
                    old_files.append((old_file.storage, old_file.name))
            for key, value in validated_data.items():
                setattr(previous, key, value)
            previous.estado = VerificacionKyc.Estado.PENDIENTE
            previous.coincide = None
            previous.proveedor = ""
            previous.referencia_reniec = ""
            previous.fecha_consulta = None
            previous.ultimo_intento_en = None
            previous.error_servicio = ""
            previous.save()
            instance = previous
        else:
            instance = VerificacionKyc.objects.create(adoptante=adoptante, **validated_data)

        for storage, name in old_files:
            storage.delete(name)
        return instance
