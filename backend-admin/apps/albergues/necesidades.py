"""API pública de necesidades y registro privado de donaciones (US-49)."""
from rest_framework import permissions, serializers, viewsets
from rest_framework.exceptions import PermissionDenied, ValidationError

from .models import Albergue, DonacionAlbergue, NecesidadAlbergue


class IsShelterRole(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.rol == "ALBERGUE"


class NecesidadSerializer(serializers.ModelSerializer):
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)

    class Meta:
        model = NecesidadAlbergue
        fields = [
            "id", "albergue", "albergue_nombre", "titulo", "descripcion",
            "cantidad_objetivo", "unidad", "estado", "creado_en", "actualizado_en",
        ]
        read_only_fields = ["id", "albergue", "albergue_nombre", "creado_en", "actualizado_en"]

    def validate_cantidad_objetivo(self, value):
        if value <= 0:
            raise serializers.ValidationError("La cantidad debe ser mayor que cero.")
        return value

    def validate_unidad(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("Indica la unidad de medida.")
        return value


class NecesidadViewSet(viewsets.ModelViewSet):
    serializer_class = NecesidadSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]
    http_method_names = ["get", "post", "patch", "put", "head", "options"]

    def get_queryset(self):
        qs = NecesidadAlbergue.objects.select_related("albergue")
        user = self.request.user
        if user.is_authenticated and user.rol == "ALBERGUE":
            return qs.filter(albergue__usuario=user)
        return qs.filter(albergue__activo=True, estado=NecesidadAlbergue.Estado.ABIERTA)

    def _albergue_propietario(self):
        user = self.request.user
        if not user.is_authenticated or user.rol != "ALBERGUE":
            raise PermissionDenied("Solo el albergue propietario puede gestionar sus necesidades.")
        try:
            return Albergue.objects.get(usuario=user, activo=True)
        except Albergue.DoesNotExist as exc:
            raise ValidationError({"albergue": "Completa y activa el perfil de tu albergue."}) from exc

    def perform_create(self, serializer):
        serializer.save(albergue=self._albergue_propietario())

    def perform_update(self, serializer):
        if serializer.instance.albergue.usuario_id != self.request.user.pk or self.request.user.rol != "ALBERGUE":
            raise PermissionDenied("Solo el albergue propietario puede modificar esta necesidad.")
        serializer.save()


class DonacionSerializer(serializers.ModelSerializer):
    necesidad_titulo = serializers.CharField(source="necesidad.titulo", read_only=True, default="")

    class Meta:
        model = DonacionAlbergue
        fields = [
            "id", "necesidad", "necesidad_titulo", "tipo", "descripcion",
            "cantidad", "unidad", "fecha", "creado_en",
        ]
        read_only_fields = ["id", "necesidad_titulo", "creado_en"]

    def validate_cantidad(self, value):
        if value <= 0:
            raise serializers.ValidationError("La cantidad debe ser mayor que cero.")
        return value

    def validate_descripcion(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("Describe el aporte recibido.")
        return value

    def validate_unidad(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("Indica la unidad de medida.")
        return value

    def validate(self, attrs):
        necesidad = attrs.get("necesidad")
        if necesidad:
            albergue = self.context.get("albergue")
            if albergue is None:
                raise serializers.ValidationError({"necesidad": "Completa y activa el perfil de tu albergue."})
            if necesidad.albergue_id != albergue.id:
                raise serializers.ValidationError({"necesidad": "La necesidad no pertenece a tu albergue."})
            if necesidad.estado != NecesidadAlbergue.Estado.ABIERTA:
                raise serializers.ValidationError({"necesidad": "No se pueden asociar aportes a una necesidad cubierta."})
            if necesidad.unidad.casefold() != attrs.get("unidad", "").strip().casefold():
                raise serializers.ValidationError({"unidad": "La unidad debe coincidir con la necesidad seleccionada."})
        return attrs


class DonacionViewSet(viewsets.ModelViewSet):
    serializer_class = DonacionSerializer
    permission_classes = [permissions.IsAuthenticated, IsShelterRole]
    http_method_names = ["get", "post", "head", "options"]

    def _albergue_propietario(self):
        user = self.request.user
        if user.rol != "ALBERGUE":
            raise PermissionDenied("Solo el albergue propietario puede registrar donaciones.")
        try:
            return Albergue.objects.get(usuario=user, activo=True)
        except Albergue.DoesNotExist as exc:
            raise ValidationError({"albergue": "Completa y activa el perfil de tu albergue."}) from exc

    def get_queryset(self):
        if not self.request.user.is_authenticated or self.request.user.rol != "ALBERGUE":
            return DonacionAlbergue.objects.none()
        return DonacionAlbergue.objects.filter(albergue__usuario=self.request.user).select_related("necesidad")

    def get_serializer_context(self):
        context = super().get_serializer_context()
        if self.request.user.is_authenticated and self.request.user.rol == "ALBERGUE":
            try:
                context["albergue"] = Albergue.objects.get(usuario=self.request.user, activo=True)
            except Albergue.DoesNotExist:
                pass
        return context

    def perform_create(self, serializer):
        albergue = self._albergue_propietario()
        serializer.save(albergue=albergue)
