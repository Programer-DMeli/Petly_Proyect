from django.utils import timezone
from rest_framework import permissions, serializers, status, viewsets
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response

from apps.acceso.models import SolicitudAdopcion, User
from .models import SeguimientoPostAdopcion, SolicitudAsesoriaVeterinaria


class SolicitudAsesoriaVeterinariaSerializer(serializers.ModelSerializer):
    class Meta:
        model = SolicitudAsesoriaVeterinaria
        fields = ["id", "seguimiento", "problema", "creado_en"]
        read_only_fields = ["id", "creado_en"]

    def get_fields(self):
        fields = super().get_fields()
        request = self.context.get("request")
        queryset = SeguimientoPostAdopcion.objects.none()
        if request and request.user.is_authenticated:
            queryset = SeguimientoPostAdopcion.objects.filter(
                solicitud__adoptante=request.user,
                solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
            ).exclude(estado=SeguimientoPostAdopcion.Estado.CANCELADO)
        fields["seguimiento"].queryset = queryset
        return fields


class SolicitudAsesoriaVeterinariaViewSet(viewsets.ModelViewSet):
    serializer_class = SolicitudAsesoriaVeterinariaSerializer
    permission_classes = [permissions.IsAuthenticated]
    http_method_names = ["get", "post", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = SolicitudAsesoriaVeterinaria.objects.select_related("seguimiento__solicitud")
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(seguimiento__solicitud__adoptante=user)
        if user.rol == User.Rol.ALBERGUE:
            return qs.filter(seguimiento__solicitud__albergue__usuario=user)
        return qs.none()

    def create(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante puede solicitar asesoría veterinaria.")
        return super().create(request, *args, **kwargs)


class GestionarSeguimientoPermission(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.method in permissions.SAFE_METHODS or request.user.rol == User.Rol.ALBERGUE


class SeguimientoPostAdopcionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField()
    mascota_nombre = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    adoptante_email = serializers.EmailField(source="solicitud.adoptante.email", read_only=True, allow_null=True)
    albergue_nombre = serializers.CharField(source="solicitud.albergue.nombre", read_only=True)
    asesorias_veterinarias = SolicitudAsesoriaVeterinariaSerializer(many=True, read_only=True)

    class Meta:
        model = SeguimientoPostAdopcion
        fields = ["id", "solicitud_id", "mascota_nombre", "adoptante_email", "programado_para",
                  "modalidad", "estado", "notas", "creado_en", "actualizado_en",
                  "albergue_nombre", "asesorias_veterinarias"]
        read_only_fields = ["id", "estado", "creado_en", "actualizado_en"]

    def validate_solicitud_id(self, value):
        request = self.context["request"]
        qs = SolicitudAdopcion.objects.select_related("albergue", "adoptante", "mascota")
        if request.user.rol == User.Rol.ALBERGUE and hasattr(request.user, "albergue"):
            qs = qs.filter(albergue=request.user.albergue)
        solicitud = qs.filter(pk=value, estado=SolicitudAdopcion.Estado.ENTREGADA).first()
        if solicitud is None:
            raise serializers.ValidationError("Debe corresponder a una adopción entregada de tu albergue.")
        return value

    def validate_programado_para(self, value):
        if value <= timezone.now():
            raise serializers.ValidationError("La fecha y hora deben ser futuras.")
        return value

    def validate(self, attrs):
        solicitud_id = attrs.get("solicitud_id", getattr(self.instance, "solicitud_id", None))
        if self.instance and solicitud_id != self.instance.solicitud_id and self.instance.asesorias_veterinarias.exists():
            raise ValidationError({"solicitud_id": "No puedes cambiar la adopción de un seguimiento con solicitudes de asesoría."})
        if solicitud_id:
            user = self.context["request"].user
            solicitud = SolicitudAdopcion.objects.filter(pk=solicitud_id).first()
            if not solicitud or solicitud.estado != SolicitudAdopcion.Estado.ENTREGADA:
                raise ValidationError({"solicitud_id": "El seguimiento requiere una adopción entregada."})
            if user.rol == User.Rol.ALBERGUE and solicitud.albergue.usuario_id != user.pk:
                raise PermissionDenied("La adopción no pertenece a tu albergue.")
        return attrs


class SeguimientoPostAdopcionViewSet(viewsets.ModelViewSet):
    serializer_class = SeguimientoPostAdopcionSerializer
    permission_classes = [permissions.IsAuthenticated, GestionarSeguimientoPermission]
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = SeguimientoPostAdopcion.objects.select_related(
            "solicitud__mascota", "solicitud__adoptante", "solicitud__albergue"
        ).prefetch_related("asesorias_veterinarias")
        if user.rol == User.Rol.ALBERGUE and hasattr(user, "albergue"):
            return qs.filter(solicitud__albergue=user.albergue)
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(solicitud__adoptante=user)
        return qs.none()

    def perform_create(self, serializer):
        serializer.save(creado_por=self.request.user)

    def perform_update(self, serializer):
        serializer.save()

    def destroy(self, request, *args, **kwargs):
        seguimiento = self.get_object()
        if seguimiento.estado != SeguimientoPostAdopcion.Estado.PROGRAMADO:
            return Response({"detail": "Solo se pueden cancelar seguimientos programados."}, status=409)
        seguimiento.estado = SeguimientoPostAdopcion.Estado.CANCELADO
        seguimiento.save(update_fields=["estado", "actualizado_en"])
        return Response(status=status.HTTP_204_NO_CONTENT)
