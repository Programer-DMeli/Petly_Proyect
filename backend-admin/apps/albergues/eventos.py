from django.utils import timezone
from rest_framework import serializers, viewsets
from rest_framework.permissions import AllowAny
from rest_framework.exceptions import PermissionDenied, ValidationError
from .models import Albergue, EventoAdopcion


class EventoSerializer(serializers.ModelSerializer):
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)
    class Meta:
        model = EventoAdopcion
        fields = ["id", "albergue", "albergue_nombre", "titulo", "descripcion", "inicio", "fin", "ubicacion", "contacto_publico", "cancelado"]
        read_only_fields = ["id", "albergue", "albergue_nombre"]

    def validate(self, attrs):
        inicio = attrs.get("inicio", getattr(self.instance, "inicio", None))
        fin = attrs.get("fin", getattr(self.instance, "fin", None))
        if inicio and fin and inicio >= fin:
            raise serializers.ValidationError({"fin": "El fin debe ser posterior al inicio."})
        # Cancelar un evento conserva su registro incluso si ya comenzó.
        if inicio and (not self.instance or "inicio" in attrs) and inicio <= timezone.now():
            raise serializers.ValidationError({"inicio": "Publica una fecha y hora futuras."})
        return attrs


class EventoViewSet(viewsets.ModelViewSet):
    serializer_class = EventoSerializer
    permission_classes = [AllowAny]
    http_method_names = ["get", "post", "patch", "put", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = EventoAdopcion.objects.select_related("albergue")
        if user.is_authenticated and user.rol == "ALBERGUE":
            return qs.filter(albergue__usuario=user)
        return qs.filter(albergue__activo=True, fin__gte=timezone.now())

    def propietario(self):
        user = self.request.user
        if not user.is_authenticated or user.rol != "ALBERGUE":
            raise PermissionDenied("Solo el albergue propietario puede publicar o editar eventos.")
        try:
            albergue = Albergue.objects.get(usuario=user, activo=True)
        except Albergue.DoesNotExist:
            raise ValidationError({"albergue": "Completa y activa el perfil de tu albergue."})
        return albergue

    def perform_create(self, serializer):
        serializer.save(albergue=self.propietario())

    def perform_update(self, serializer):
        if serializer.instance.albergue_id != self.propietario().pk:
            raise PermissionDenied("No puedes modificar eventos de otro albergue.")
        if serializer.instance.cancelado and serializer.validated_data.get("cancelado") is False:
            raise ValidationError({"cancelado": "Un evento cancelado conserva su estado."})
        serializer.save()
