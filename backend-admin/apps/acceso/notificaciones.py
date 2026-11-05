"""Bandeja del adoptante basada en los eventos confirmados de sus solicitudes.

El evento y la transición se guardan en la misma transacción: no se generan
avisos duplicados ni se notifican decisiones que hayan sido revertidas.
"""
from django.utils import timezone
from rest_framework import permissions, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from .models import SolicitudEvento, User


MENSAJES = {
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
    mascota_id = serializers.IntegerField(source="solicitud.mascota_id", read_only=True)
    mascota_nombre = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    albergue_nombre = serializers.CharField(source="solicitud.albergue.nombre", read_only=True)
    estado_actual = serializers.CharField(source="solicitud.estado", read_only=True)
    titulo = serializers.CharField(source="get_tipo_display", read_only=True)
    mensaje = serializers.SerializerMethodField()

    class Meta:
        model = SolicitudEvento
        fields = ["id", "solicitud_id", "mascota_id", "mascota_nombre", "albergue_nombre",
                  "tipo", "titulo", "mensaje", "descripcion", "estado_nuevo", "estado_actual",
                  "ocurrido_en", "leido_en"]

    def get_mensaje(self, evento):
        return MENSAJES[evento.tipo]


class NotificacionPagination(PageNumberPagination):
    page_size = 30


class NotificacionViewSet(viewsets.GenericViewSet):
    permission_classes = [permissions.IsAuthenticated]
    serializer_class = NotificacionSerializer
    pagination_class = NotificacionPagination

    def get_queryset(self):
        qs = SolicitudEvento.objects.select_related("solicitud__mascota", "solicitud__albergue")
        if self.request.user.rol != User.Rol.ADOPTANTE:
            return qs.none()
        return qs.filter(
            solicitud__adoptante=self.request.user, visible_adoptante=True,
            tipo__in=MENSAJES,
        ).order_by("-ocurrido_en", "-id")

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
