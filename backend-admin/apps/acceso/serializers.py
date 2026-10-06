"""Serializadores de acceso y postulantes (US-18, US-21)."""
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from .models import Postulante, SolicitudAdopcion, User, AdoptanteProfile
from apps.mascotas.serializers import MascotaListSerializer

User = get_user_model()


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


class CustomTokenObtainPairSerializer(TokenObtainPairSerializer):
    """JWT token con información del usuario y rol (US-21)."""

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token["rol"] = user.rol
        token["username"] = user.username
        token["email"] = user.email
        return token

    def validate(self, attrs):
        data = super().validate(attrs)
        data["user"] = {
            "id": self.user.id,
            "username": self.user.username,
            "email": self.user.email,
            "rol": self.user.rol,
            "first_name": self.user.first_name,
            "last_name": self.user.last_name,
        }
        return data


class UserRegistrationSerializer(serializers.ModelSerializer):
    """Registro de usuario (adoptante o albergue)."""

    password = serializers.CharField(write_only=True, validators=[validate_password])
    password_confirm = serializers.CharField(write_only=True)
    rol = serializers.ChoiceField(choices=User.Rol.choices, default=User.Rol.ADOPTANTE)

    class Meta:
        model = User
        fields = [
            "username", "email", "password", "password_confirm",
            "rol", "first_name", "last_name", "telefono", "fecha_nacimiento",
        ]

    def validate(self, attrs):
        if attrs["password"] != attrs["password_confirm"]:
            raise serializers.ValidationError({"password_confirm": "Las contraseñas no coinciden."})
        return attrs

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError("El nombre de usuario ya existe.")
        return value

    def validate_email(self, value):
        if User.objects.filter(email=value).exists():
            raise serializers.ValidationError("El email ya está registrado.")
        return value

    def create(self, validated_data):
        validated_data.pop("password_confirm")
        user = User.objects.create_user(**validated_data)
        return user


class UserSerializer(serializers.ModelSerializer):
    """Serializador de usuario para perfil."""

    class Meta:
        model = User
        fields = [
            "id", "username", "email", "first_name", "last_name",
            "rol", "telefono", "fecha_nacimiento",
            "acepto_terminos", "fecha_aceptacion_terminos",
            "date_joined", "last_login",
        ]
        read_only_fields = [
            "id", "username", "rol", "date_joined", "last_login",
            "acepto_terminos", "fecha_aceptacion_terminos",
        ]


class AdoptanteProfileSerializer(serializers.ModelSerializer):
    """Serializador del perfil de adoptante (US-21, US-16, US-17)."""

    user = UserSerializer(read_only=True)
    nombre_completo = serializers.CharField(read_only=True)

    class Meta:
        model = AdoptanteProfile
        fields = [
            "id", "user", "nombre_completo",
            "documento_tipo", "documento_numero", "nombres", "apellidos",
            "direccion", "distrito", "provincia", "departamento",
            "tipo_vivienda", "tiene_patio", "tiene_otras_mascotas",
            "detalles_otras_mascotas", "experiencia_previa",
            "motivo_adopcion", "preferencia_especie", "preferencia_tamanio",
            "preferencia_edad", "cuestionario_completado", "fecha_cuestionario",
            "creado_en", "actualizado_en",
        ]
        read_only_fields = ["id", "user", "nombre_completo", "creado_en", "actualizado_en"]


class AdoptanteProfileCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar perfil de adoptante."""

    class Meta:
        model = AdoptanteProfile
        fields = [
            "documento_tipo", "documento_numero", "nombres", "apellidos",
            "direccion", "distrito", "provincia", "departamento",
            "tipo_vivienda", "tiene_patio", "tiene_otras_mascotas",
            "detalles_otras_mascotas", "experiencia_previa",
            "motivo_adopcion", "preferencia_especie", "preferencia_tamanio",
            "preferencia_edad",
        ]

    def validate_documento_numero(self, value):
        if AdoptanteProfile.objects.filter(documento_numero=value).exclude(
            pk=self.instance.pk if self.instance else None
        ).exists():
            raise serializers.ValidationError("El número de documento ya está registrado.")
        return value


class PasswordResetRequestSerializer(serializers.Serializer):
    """Solicitud de recuperación de contraseña (US-24)."""

    email = serializers.EmailField()

    def validate_email(self, value):
        if not User.objects.filter(email=value).exists():
            raise serializers.ValidationError("No existe una cuenta con este email.")
        return value


class PasswordResetConfirmSerializer(serializers.Serializer):
    """Confirmación de recuperación de contraseña (US-24)."""

    token = serializers.CharField()
    new_password = serializers.CharField(validators=[validate_password])
    new_password_confirm = serializers.CharField()

    def validate(self, attrs):
        if attrs["new_password"] != attrs["new_password_confirm"]:
            raise serializers.ValidationError({"new_password_confirm": "Las contraseñas no coinciden."})
        return attrs