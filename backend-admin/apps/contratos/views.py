import hashlib
import hmac
import re
import time

from django.conf import settings
from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework import permissions, status, viewsets
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion, User
from .models import ContratoAdopcion
from .providers import ErrorProveedorFirma, ProveedorNoConfigurado
from .serializers import ContratoAdopcionSerializer, EventoProveedorSerializer
from .services import (
    TerminosContratoNoConfigurados,
    crear_borrador,
    enviar_a_firma,
    procesar_evento_proveedor,
)


class ContratoViewSet(viewsets.ReadOnlyModelViewSet):
    """Lectura aislada al adoptante titular y al albergue de la solicitud."""

    serializer_class = ContratoAdopcionSerializer
    permission_classes = [permissions.IsAuthenticated]
    lookup_field = "public_id"

    def get_queryset(self):
        user = self.request.user
        qs = ContratoAdopcion.objects.select_related(
            "solicitud", "solicitud__adoptante", "solicitud__albergue"
        )
        if user.is_staff:
            return qs
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(solicitud__adoptante=user)
        if user.rol == User.Rol.ALBERGUE and hasattr(user, "albergue"):
            return qs.filter(solicitud__albergue=user.albergue)
        return qs.none()


class GenerarContratoView(APIView):
    """Genera una versión de contrato y la envía al proveedor si está configurado."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, solicitud_id):
        try:
            solicitud = SolicitudAdopcion.objects.select_related("albergue").get(pk=solicitud_id)
        except SolicitudAdopcion.DoesNotExist:
            return Response({"detail": "Solicitud no encontrada."}, status=status.HTTP_404_NOT_FOUND)
        if not request.user.is_staff and not (
            request.user.rol == User.Rol.ALBERGUE
            and solicitud.albergue.usuario_id == request.user.pk
        ):
            return Response({"detail": "Solo el albergue propietario puede generar este contrato."}, status=403)

        try:
            contrato, creado = crear_borrador(solicitud.pk, request.user)
        except DjangoValidationError as exc:
            raise ValidationError({"detail": exc.messages}) from exc
        try:
            contrato = enviar_a_firma(contrato.pk)
        except TerminosContratoNoConfigurados as exc:
            return Response({
                "detail": str(exc),
                "contrato": ContratoAdopcionSerializer(contrato).data,
            }, status=status.HTTP_409_CONFLICT)
        except DjangoValidationError as exc:
            raise ValidationError({"detail": exc.messages}) from exc
        except ProveedorNoConfigurado as exc:
            contrato.refresh_from_db()
            return Response({
                "detail": str(exc),
                "contrato": ContratoAdopcionSerializer(contrato).data,
            }, status=status.HTTP_503_SERVICE_UNAVAILABLE)
        except ErrorProveedorFirma as exc:
            contrato.refresh_from_db()
            return Response({
                "detail": str(exc),
                "contrato": ContratoAdopcionSerializer(contrato).data,
            }, status=status.HTTP_502_BAD_GATEWAY)
        return Response(
            ContratoAdopcionSerializer(contrato).data,
            status=status.HTTP_201_CREATED if creado else status.HTTP_200_OK,
        )


class WebhookFirmaView(APIView):
    """Webhook del proveedor protegido con HMAC, ventana temporal e idempotencia."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        secret = settings.CONTRACT_SIGNATURE_WEBHOOK_SECRET
        if not secret:
            return Response({"detail": "Webhook de firma no configurado."}, status=503)
        timestamp = request.headers.get("X-Provider-Timestamp", "")
        signature = request.headers.get("X-Provider-Signature", "").removeprefix("sha256=").lower()
        if not timestamp.isascii() or not timestamp.isdigit():
            return Response({"detail": "Timestamp de firma inválido."}, status=401)
        if not re.fullmatch(r"[0-9a-f]{64}", signature):
            return Response({"detail": "Firma de webhook inválida."}, status=401)
        try:
            epoch = int(timestamp)
        except (TypeError, ValueError):
            return Response({"detail": "Timestamp de firma inválido."}, status=401)
        if abs(time.time() - epoch) > 300:
            return Response({"detail": "Webhook fuera de la ventana temporal permitida."}, status=401)
        signed_content = timestamp.encode("ascii") + b"." + request.body
        expected = hmac.new(secret.encode("utf-8"), signed_content, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            return Response({"detail": "Firma de webhook inválida."}, status=401)

        serializer = EventoProveedorSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        payload = serializer.validated_data
        provider = settings.CONTRACT_SIGNATURE_PROVIDER
        try:
            contrato, creado = procesar_evento_proveedor(
                provider=provider,
                event_id=payload["event_id"],
                document_id=payload["document_id"],
                status=payload["status"],
                payload_sha256=hashlib.sha256(request.body).hexdigest(),
                occurred_at=payload.get("occurred_at"),
            )
        except ContratoAdopcion.DoesNotExist:
            return Response({"detail": "Documento de firma desconocido."}, status=404)
        return Response({
            "accepted": True,
            "duplicate": not creado,
            "contractId": str(contrato.public_id),
            "status": contrato.estado,
        })
