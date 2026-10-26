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
    propietario_spring_id = serializers.IntegerField(source="usuario.spring_id", read_only=True)
    propietario_correo = serializers.CharField(source="usuario.email", read_only=True)

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
            "propietario_spring_id",
            "propietario_correo",
            "activo",
            "creado_en",
            "actualizado_en",
            "infraestructura",
        ]
        read_only_fields = [
            "id",
            "usuario_id",
            "usuario_username",
            "propietario_spring_id",
            "propietario_correo",
            "creado_en",
            "actualizado_en",
            "infraestructura",
        ]


class OptionalBooleanField(serializers.BooleanField):
    default_empty_html = serializers.empty


class AlbergueCreateUpdateSerializer(serializers.ModelSerializer):
    """Serializador para crear/actualizar albergue (sin campos de solo lectura)."""

    propietario_spring_id = serializers.IntegerField(min_value=1, write_only=True, required=False)
    activo = OptionalBooleanField(required=False)

    def validate(self, attrs):
        request = self.context['request']
        admin = request.user.rol == 'ADMIN'
        if not admin and ('propietario_spring_id' in attrs or 'activo' in attrs):
            raise serializers.ValidationError({'propietario_spring_id':'Solo ADMIN asigna propietarios o cambia la activación.'})
        if admin and self.instance is None and 'propietario_spring_id' not in attrs:
            raise serializers.ValidationError({'propietario_spring_id':'Selecciona una cuenta ALBERGUE.'})
        if 'propietario_spring_id' in attrs:
            from apps.acceso.authentication import spring_get, AuthorityUnavailable
            import urllib.error
            uid = attrs.pop('propietario_spring_id')
            if self.instance and self.instance.usuario.spring_id != uid:
                raise serializers.ValidationError({'propietario_spring_id':'El propietario no se transfiere desde este formulario.'})
            try:
                identity = spring_get('/api/admin/cuentas-albergue/'+str(uid), request.headers.get('Authorization'))
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    raise serializers.ValidationError({'propietario_spring_id':'La cuenta no tiene rol ALBERGUE.'})
                raise AuthorityUnavailable()
            from django.contrib.auth import get_user_model
            User = get_user_model()
            owner = User.objects.filter(spring_id=uid).first()
            if owner is not None and (owner.is_staff or owner.is_superuser):
                raise serializers.ValidationError({'propietario_spring_id':'No vincules una cuenta técnica.'})
            if owner is not None:
                existing = Albergue.objects.filter(usuario=owner)
                if self.instance: existing = existing.exclude(pk=self.instance.pk)
                if existing.exists():
                    raise serializers.ValidationError({'propietario_spring_id':'Esta cuenta ya tiene un albergue.'})
            # Solo se resuelve la identidad después de validar el rol con Spring.
            self.owner_identity = identity
        return attrs

    def _owner(self):
        from django.contrib.auth import get_user_model
        identity = self.owner_identity
        owner, _ = get_user_model().objects.get_or_create(spring_id=identity['id'], defaults={
            'username':'spring_'+str(identity['id']), 'email':identity['correo'], 'rol':'ALBERGUE'})
        owner.email = identity['correo']; owner.rol = 'ALBERGUE'; owner.set_unusable_password()
        owner.save(update_fields=['email','rol','password'])
        return owner

    def create(self, validated_data):
        if hasattr(self, 'owner_identity'): validated_data['usuario'] = self._owner()
        return super().create(validated_data)

    def update(self, instance, validated_data):
        if hasattr(self, 'owner_identity'):
            owner = self._owner()
            if owner.pk != instance.usuario_id:
                raise serializers.ValidationError({'propietario_spring_id':'El propietario no se transfiere desde este formulario.'})
        return super().update(instance, validated_data)

    class Meta:
        model = Albergue
        fields = [
            "propietario_spring_id",
            "activo",
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
    def validate_metros_cuadrados(self, value):
        if value is not None and value < 0:
            raise serializers.ValidationError("No puede ser negativo.")
        return value

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
