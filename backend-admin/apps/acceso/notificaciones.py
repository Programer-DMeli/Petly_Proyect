"""Bandeja del adoptante: solicitudes y nuevas mascotas con criterios coincidentes.

El evento y la transición se guardan en la misma transacción: no se generan
avisos duplicados ni se notifican decisiones que hayan sido revertidas.
"""
import secrets

from django.conf import settings
from django.db.models import Q
from django.utils import timezone
from rest_framework import permissions, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import CriteriosAviso, SolicitudEvento, User
from apps.mascotas.models import Mascota


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


class FeedInternoCoincidenciasView(APIView):
    """Feed privado por cursor para que Spring entregue avisos nuevos por email."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        secreto_configurado = settings.INTERNAL_API_SECRET
        secreto_recibido = request.headers.get("X-Internal-Secret", "")
        if not secreto_configurado:
            return Response(
                {"detail": "La integración interna de avisos no está configurada."},
                status=503,
            )
        if not secreto_recibido or not secrets.compare_digest(secreto_recibido, secreto_configurado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=401)

        try:
            after_id = max(0, int(request.query_params.get("after_id", "0")))
            limit = int(request.query_params.get("limit", "100"))
        except (TypeError, ValueError):
            return Response({"detail": "El cursor y el límite deben ser números enteros."}, status=400)
        if not 1 <= limit <= 200:
            return Response({"detail": "El límite debe estar entre 1 y 200."}, status=400)

        eventos = (
            SolicitudEvento.objects.filter(
                pk__gt=after_id,
                tipo=SolicitudEvento.Tipo.NUEVA_COINCIDENCIA,
                destinatario__spring_id__isnull=False,
                mascota_coincidente__estado=Mascota.Estado.DISPONIBLE,
            )
            .select_related("destinatario", "mascota_coincidente", "mascota_coincidente__albergue")
            .order_by("pk")[:limit]
        )
        return Response([
            {
                "evento_id": evento.pk,
                "adoptante_spring_id": evento.destinatario.spring_id,
                "mascota_id": evento.mascota_coincidente_id,
                "mascota_nombre": evento.mascota_coincidente.nombre,
                "albergue_nombre": evento.mascota_coincidente.albergue.nombre,
                "ocurrido_en": evento.ocurrido_en,
            }
            for evento in eventos
        ])


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
