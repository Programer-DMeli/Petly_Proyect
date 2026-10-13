"""Serializadores de acceso y postulantes (US-18, US-21)."""
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.db import models
from django.utils import timezone
from .models import Postulante, SolicitudAdopcion, User, AdoptanteProfile, Autorizacion
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
    """JWT token con información del usuario y rol (US-21).
    Incluye bloqueo por 2 intentos fallidos consecutivos -> 5 min (US-21).
    """

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token["rol"] = user.rol
        token["username"] = user.username
        token["email"] = user.email
        # Usar issuer/audience propios de Django para adoptante móvil
        token["iss"] = "petly-django"
        token["aud"] = "petly-mobile"
        # sub debe ser string para JWT spec
        token["sub"] = str(user.id)
        return token

    def validate(self, attrs):
        from django.utils import timezone
        from django.contrib.auth import authenticate
        from rest_framework import exceptions as drf_exceptions
        
        username = attrs.get("username", "").strip()
        password = attrs.get("password", "")
        
        # Buscar usuario por username o email
        user = User.objects.filter(models.Q(username__iexact=username) | models.Q(email__iexact=username)).first()
        
        if user:
            # Verificar si está bloqueado
            if user.bloqueado_hasta and user.bloqueado_hasta > timezone.now():
                raise serializers.ValidationError(
                    {"detail": "Cuenta bloqueada temporalmente. Intente más tarde."},
                    code="account_locked"
                )
            # Bloqueo vencido: resetear contador
            elif user.bloqueado_hasta and user.bloqueado_hasta <= timezone.now():
                user.intentos_fallidos = 0
                user.bloqueado_hasta = None
                user.save(update_fields=["intentos_fallidos", "bloqueado_hasta"])
        
        # Intentar autenticar
        try:
            data = super().validate(attrs)
        except drf_exceptions.AuthenticationFailed:
            # Login fallido: incrementar contador si el usuario existe
            if user:
                user.intentos_fallidos += 1
                if user.intentos_fallidos >= 2:
                    user.bloqueado_hasta = timezone.now() + timezone.timedelta(minutes=5)
                user.save(update_fields=["intentos_fallidos", "bloqueado_hasta"])
            raise
        
        # Login exitoso: resetear contador
        if self.user:
            self.user.intentos_fallidos = 0
            self.user.bloqueado_hasta = None
            self.user.save(update_fields=["intentos_fallidos", "bloqueado_hasta"])
        
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
            "tipo_vivienda", "tiene_patio", "tiene_ninos", "tiene_otras_mascotas",
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
            "tipo_vivienda", "tiene_patio", "tiene_ninos", "tiene_otras_mascotas",
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


class AutorizacionSerializer(serializers.ModelSerializer):
    """Serializador para autorización de consulta de perfil (US-18)."""

    adoptante_username = serializers.CharField(source="adoptante.username", read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)

    class Meta:
        model = Autorizacion
        fields = [
            "id",
            "adoptante",
            "adoptante_username",
            "albergue",
            "albergue_nombre",
            "estado",
            "estado_display",
            "campos_permitidos",
            "fecha_autorizacion",
            "fecha_revocacion",
            "fecha_expiracion",
        ]
        read_only_fields = [
            "id", "adoptante", "adoptante_username",
            "albergue", "albergue_nombre",
            "fecha_autorizacion", "fecha_revocacion",
            "estado_display",
        ]


class AutorizacionCreateSerializer(serializers.ModelSerializer):
    """Serializador para crear autorización (adoptante autoriza a albergue)."""

    class Meta:
        model = Autorizacion
        fields = [
            "albergue",
            "campos_permitidos",
            "fecha_expiracion",
        ]

    def validate_albergue(self, value):
        user = self.context["request"].user
        if Autorizacion.objects.filter(adoptante=user, albergue=value).exists():
            raise serializers.ValidationError("Ya existe una autorización para este albergue.")
        return value

    def create(self, validated_data):
        validated_data["adoptante"] = self.context["request"].user
        validated_data["estado"] = Autorizacion.Estado.VIGENTE
        if not validated_data.get("campos_permitidos"):
            validated_data["campos_permitidos"] = [
                "tipo_vivienda", "tiene_patio", "tiene_ninos", "tiene_otras_mascotas",
                "detalles_otras_mascotas", "experiencia_previa", "motivo_adopcion",
                "preferencia_especie", "preferencia_tamanio", "preferencia_edad",
                "horas_fuera", "presupuesto"
            ]
        return super().create(validated_data)


class AutorizacionRevokeSerializer(serializers.Serializer):
    """Serializador para revocar autorización."""
    pass