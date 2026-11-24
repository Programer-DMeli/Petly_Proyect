from django.db import transaction
from rest_framework import serializers
from rest_framework.exceptions import ValidationError
from config.parametros_sistema import obtener_limite_imagen_bytes

from .models import VerificacionKyc


ESTADOS_KYC_TERMINALES = {
    VerificacionKyc.Estado.VERIFICADO,
    VerificacionKyc.Estado.APROBADO_MANUAL,
    VerificacionKyc.Estado.RECHAZADO_MANUAL,
}
ESTADOS_KYC_BLOQUEADOS = ESTADOS_KYC_TERMINALES | {VerificacionKyc.Estado.PENDIENTE_REVISION}


class VerificacionKycSerializer(serializers.ModelSerializer):
    documento_anverso = serializers.ImageField(write_only=True, required=True)
    documento_reverso = serializers.ImageField(write_only=True, required=True)

    class Meta:
        model = VerificacionKyc
        fields = [
            "id", "dni", "nombres", "apellidos", "estado", "coincide",
            "proveedor", "referencia_reniec", "fecha_consulta", "error_servicio",
            "ultimo_intento_en",
            "motivo_revision", "fecha_revision", "revisado_por",
            "documento_anverso", "documento_reverso", "creado_en", "actualizado_en",
        ]
        read_only_fields = [
            "id", "estado", "coincide", "proveedor", "referencia_reniec",
            "fecha_consulta", "error_servicio", "creado_en", "actualizado_en",
            "ultimo_intento_en",
            "motivo_revision", "fecha_revision", "revisado_por",
        ]

    def validate_dni(self, value):
        value = value.strip()
        if len(value) != 8 or not value.isascii() or not value.isdigit():
            raise serializers.ValidationError("El DNI debe contener exactamente ocho dígitos.")
        return value

    def validate(self, attrs):
        limite_imagen = obtener_limite_imagen_bytes()
        for field in ("documento_anverso", "documento_reverso"):
            document = attrs.get(field)
            if document and document.size > limite_imagen:
                raise ValidationError({
                    field: f"La imagen no debe superar {limite_imagen // (1024 * 1024)} MB, según la configuración global."
                })
        attrs["nombres"] = attrs["nombres"].strip()
        attrs["apellidos"] = attrs["apellidos"].strip()
        if not attrs["nombres"] or not attrs["apellidos"]:
            raise ValidationError("Ingresa los nombres y apellidos tal como aparecen en el DNI.")
        return attrs

    @transaction.atomic
    def create(self, validated_data):
        adoptante = validated_data.pop("adoptante")
        previous = VerificacionKyc.objects.select_for_update().filter(adoptante=adoptante).first()
        if previous and previous.estado in ESTADOS_KYC_BLOQUEADOS:
            raise ValidationError({"detail": "La verificación está resuelta o pendiente de revisión y no puede reemplazarse."})

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
