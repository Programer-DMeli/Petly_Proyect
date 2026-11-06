"""Bandeja del adoptante: solicitudes y nuevas mascotas con criterios coincidentes.

El evento y la transición se guardan en la misma transacción: no se generan
avisos duplicados ni se notifican decisiones que hayan sido revertidas.
"""
from django.db.models import Q
from django.utils import timezone
from rest_framework import permissions, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from .models import CriteriosAviso, SolicitudEvento, User


MENSAJES = {
    "NUEVA_COINCIDENCIA": "Una nueva mascota coincide con tus criterios guardados. ¡Conócela!",
    "SOLICITUD_CREADA": "El albergue recibió tu solicitud y está pendiente de revisión.",
    "OBSERVADA": "El albergue necesita que revises las observaciones de tu solicitud.",
    "APROBADA": "¡Tu solicitud fue aprobada! Coordina los siguientes pasos con el albergue.",
    "RECHAZADA": "El albergue no aprobó tu solicitud. Revisa el motivo de la decisión.",
    "CANCELADA": "Tu solicitud fue cancelada.",
    "CONTRATO_GENERADO": "Se ha preparado el contrato de tu adopción.",
    "CONTRATO_ENVIADO": "Tu contrato de adopción está listo para firmar.",
    "CONTRATO_FIRMADO": "Se registró la firma del contrato de adopción.",
    "CONTRATO_RECHAZADO": "El contrato de adopción fue rechazado.",
    "CONTRATO_EXPIRADO": "El contrato de adopción venció. Contacta con el albergue.",
    "ENTREGADA": "La entrega de tu mascota quedó registrada. ¡Gracias por adoptar!",
}


class NotificacionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    mascota_id = serializers.SerializerMethodField()
    mascota_nombre = serializers.SerializerMethodField()
    albergue_nombre = serializers.SerializerMethodField()
    estado_actual = serializers.SerializerMethodField()
    titulo = serializers.CharField(source="get_tipo_display", read_only=True)
    mensaje = serializers.SerializerMethodField()

    class Meta:
        model = SolicitudEvento
        fields = ["id", "solicitud_id", "mascota_id", "mascota_nombre", "albergue_nombre",
                  "tipo", "titulo", "mensaje", "descripcion", "estado_nuevo", "estado_actual",
                  "ocurrido_en", "leido_en"]

    def get_mascota_id(self, evento):
        return evento.mascota_coincidente_id or evento.solicitud.mascota_id

    def get_mascota_nombre(self, evento):
        return (evento.mascota_coincidente or evento.solicitud.mascota).nombre

    def get_albergue_nombre(self, evento):
        return (evento.mascota_coincidente.albergue if evento.mascota_coincidente_id else evento.solicitud.albergue).nombre

    def get_estado_actual(self, evento):
        return evento.mascota_coincidente.estado if evento.mascota_coincidente_id else evento.solicitud.estado

    def get_mensaje(self, evento):
        return MENSAJES[evento.tipo]


class CriteriosAvisoSerializer(serializers.ModelSerializer):
    class Meta:
        model = CriteriosAviso
        fields = ["activo", "especie", "tamanio", "temperamento", "edad_min_meses", "edad_max_meses"]
        extra_kwargs = {field: {"required": True} for field in fields}

    def validate(self, attrs):
        low, high = attrs.get("edad_min_meses"), attrs.get("edad_max_meses")
        if any(n is not None and n > 600 for n in (low, high)):
            raise serializers.ValidationError("La edad debe estar entre 0 y 600 meses.")
        if low is not None and high is not None and low > high:
            raise serializers.ValidationError("La edad mínima supera la máxima.")
        if attrs.get("activo") and not any(attrs.get(k) not in (None, "") for k in ("especie", "tamanio", "temperamento", "edad_min_meses", "edad_max_meses")):
            raise serializers.ValidationError("Guarda al menos un criterio para activar los avisos.")
        return attrs


class NotificacionPagination(PageNumberPagination):
    page_size = 30


class NotificacionViewSet(viewsets.GenericViewSet):
    permission_classes = [permissions.IsAuthenticated]
    serializer_class = NotificacionSerializer
    pagination_class = NotificacionPagination

    def get_queryset(self):
        qs = SolicitudEvento.objects.select_related("solicitud__mascota", "solicitud__albergue", "mascota_coincidente__albergue")
        if self.request.user.rol != User.Rol.ADOPTANTE:
            return qs.none()
        return qs.filter(
            Q(solicitud__adoptante=self.request.user) | Q(destinatario=self.request.user, tipo="NUEVA_COINCIDENCIA"),
            visible_adoptante=True,
            tipo__in=MENSAJES,
        ).order_by("-ocurrido_en", "-id")

    @action(detail=False, methods=["get", "put"], url_path="criterios")
    def criterios(self, request):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo los adoptantes pueden guardar criterios.")
        instance = CriteriosAviso.objects.filter(adoptante=request.user).first()
        if request.method == "GET":
            return Response(CriteriosAvisoSerializer(instance or CriteriosAviso()).data)
        serializer = CriteriosAvisoSerializer(instance, data=request.data)
        serializer.is_valid(raise_exception=True)
        instance, _ = CriteriosAviso.objects.update_or_create(
            adoptante=request.user, defaults=serializer.validated_data,
        )
        return Response(CriteriosAvisoSerializer(instance).data)

    def list(self, request):
        qs = self.get_queryset()
        page = self.paginate_queryset(qs)
        response = self.get_paginated_response(self.get_serializer(page, many=True).data)
        response.data["no_leidas"] = qs.filter(leido_en__isnull=True).count()
        response["Cache-Control"] = "private, no-store"
        return response

    @action(detail=True, methods=["post"], url_path="leer")
    def leer(self, request, pk=None):
        evento = self.get_object()
        # Reintentar conserva la fecha de la primera lectura.
        self.get_queryset().filter(pk=evento.pk, leido_en__isnull=True).update(leido_en=timezone.now())
        evento.refresh_from_db()
        return Response(self.get_serializer(evento).data)

    @action(detail=False, methods=["post"], url_path="leer-todas")
    def leer_todas(self, request):
        actualizadas = self.get_queryset().filter(leido_en__isnull=True).update(leido_en=timezone.now())
        return Response({"actualizadas": actualizadas})
