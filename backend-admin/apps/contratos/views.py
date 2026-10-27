import hashlib
import hmac
import re
import time
from django.core.files.base import ContentFile
from django.http import FileResponse, HttpResponse
from django.template.loader import render_to_string
from django.utils import timezone
from rest_framework.decorators import action

from django.conf import settings
from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework import permissions, status, viewsets
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion, User
from .models import ContratoAdopcion
from .providers import ErrorProveedorFirma, ProveedorNoConfigurado, obtener_adaptador_firma
from .serializers import ContratoAdopcionSerializer, EventoProveedorSerializer
from .services import (
    TerminosContratoNoConfigurados,
    crear_borrador,
    enviar_a_firma,
    procesar_evento_proveedor,
)
from .models import AprobacionContratoCorreo


class ContratoViewSet(viewsets.ReadOnlyModelViewSet):
    """Lectura aislada al adoptante titular y al albergue de la solicitud."""

    serializer_class = ContratoAdopcionSerializer
    permission_classes = [permissions.IsAuthenticated]
    lookup_field = "public_id"

    def get_queryset(self):
        user = self.request.user
        qs = ContratoAdopcion.objects.select_related(
            "solicitud", "solicitud__adoptante", "solicitud__albergue", "aprobacion_correo"
        )
        if user.is_staff:
            return qs
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(solicitud__adoptante=user)
        if user.rol == User.Rol.ALBERGUE and hasattr(user, "albergue"):
            return qs.filter(solicitud__albergue=user.albergue)
        return qs.none()

    @action(detail=True, methods=["get"], url_path="descargar")
    def descargar(self, request, public_id=None):
        contrato = self.get_object()
        if contrato.proveedor == "email-approval" and contrato.estado == ContratoAdopcion.Estado.APROBADO:
            aprobacion = contrato.aprobacion_correo
            content = render_to_string("contratos/aprobado_correo.html", {
                "contrato": contrato,
                "aprobacion": aprobacion,
            })
            response = HttpResponse(content, content_type="text/html; charset=utf-8")
            response["Content-Disposition"] = (
                f'attachment; filename="constancia-aprobacion-{contrato.solicitud_id}.html"'
            )
            return response
        if contrato.estado != ContratoAdopcion.Estado.FIRMADO:
            return Response({"detail": "El contrato aún no está firmado por ambas partes."}, status=409)
        if not contrato.documento_firmado:
            return Response({"detail": "El documento firmado todavía no está disponible."}, status=503)
        return FileResponse(
            contrato.documento_firmado.open("rb"), as_attachment=True,
            filename=f"contrato-adopcion-{contrato.solicitud_id}.pdf", content_type="application/pdf",
        )


class AprobarContratoTokenView(APIView):
    """El albergue propietario registra el código que recibió el adoptante."""

    permission_classes = [permissions.IsAuthenticated]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "contract_approval"

    def post(self, request, solicitud_id):
        from django.db import transaction
        from apps.acceso.models import SolicitudEvento

        codigo = str(request.data.get("token", "")).strip()
        if not re.fullmatch(r"\d{8}", codigo):
            return Response({"detail": "Ingresa el código de 8 dígitos."}, status=400)
        with transaction.atomic():
            contrato = ContratoAdopcion.objects.select_for_update().select_related(
                "solicitud__albergue"
            ).filter(solicitud_id=solicitud_id).first()
            if contrato is None:
                return Response({"detail": "Código inválido o vencido."}, status=400)
            solicitud = contrato.solicitud
            if request.user.rol != User.Rol.ALBERGUE or solicitud.albergue.usuario_id != request.user.pk:
                return Response({"detail": "Solo el albergue propietario puede registrar la aprobación."}, status=403)
            if contrato.proveedor != "email-approval" or contrato.estado != ContratoAdopcion.Estado.EN_FIRMA:
                return Response({"detail": "El contrato no está pendiente de aprobación por código."}, status=409)

            try:
                aprobacion = AprobacionContratoCorreo.objects.select_for_update().get(contrato=contrato)
            except AprobacionContratoCorreo.DoesNotExist:
                return Response({"detail": "Código inválido o vencido."}, status=400)
            if aprobacion.estado != AprobacionContratoCorreo.Estado.PENDIENTE:
                return Response({"detail": "Código inválido o vencido."}, status=400)
            if aprobacion.expira_en <= timezone.now():
                aprobacion.estado = AprobacionContratoCorreo.Estado.EXPIRADO
                aprobacion.save(update_fields=["estado"])
                contrato.estado = ContratoAdopcion.Estado.EXPIRADO
                contrato.save(update_fields=["estado", "actualizado_en"])
                SolicitudEvento.objects.create(
                    solicitud=solicitud, tipo=SolicitudEvento.Tipo.CONTRATO_EXPIRADO,
                    estado_nuevo=solicitud.estado, descripcion="Venció el código de aprobación enviado al correo.",
                )
                return Response({"detail": "El código venció. Envía uno nuevo al adoptante."}, status=410)

            expected = hmac.new(
                settings.SECRET_KEY.encode("utf-8"),
                f"{contrato.public_id}:{codigo}".encode("ascii"), hashlib.sha256,
            ).hexdigest()
            if not hmac.compare_digest(expected, aprobacion.token_sha256):
                aprobacion.intentos_fallidos += 1
                if aprobacion.intentos_fallidos >= 5:
                    aprobacion.estado = AprobacionContratoCorreo.Estado.INVALIDADO
                aprobacion.save(update_fields=["intentos_fallidos", "estado"])
                return Response({"detail": "Código inválido o vencido."}, status=400)
            if aprobacion.contenido_sha256 != contrato.contenido_sha256:
                aprobacion.estado = AprobacionContratoCorreo.Estado.INVALIDADO
                aprobacion.save(update_fields=["estado"])
                return Response({"detail": "El contrato cambió. Genera un código nuevo."}, status=409)

            now = timezone.now()
            aprobacion.estado = AprobacionContratoCorreo.Estado.CONSUMIDO
            aprobacion.consumido_en = now
            aprobacion.aprobado_por_albergue = request.user
            aprobacion.direccion_ip = request.META.get("REMOTE_ADDR")
            aprobacion.agente_usuario = request.META.get("HTTP_USER_AGENT", "")[:500]
            aprobacion.save(update_fields=[
                "estado", "consumido_en", "aprobado_por_albergue", "direccion_ip", "agente_usuario",
            ])
            contrato.estado = ContratoAdopcion.Estado.APROBADO
            contrato.aprobado_en = now
            contrato.save(update_fields=["estado", "aprobado_en", "actualizado_en"])
            SolicitudEvento.objects.create(
                solicitud=solicitud, tipo=SolicitudEvento.Tipo.CONTRATO_APROBADO_CORREO,
                estado_nuevo=solicitud.estado,
                descripcion="El adoptante confirmó el contrato con un código enviado a su correo; el albergue autenticado registró la aprobación.",
                actor=request.user,
            )
        return Response({"estado": contrato.estado, "estado_display": contrato.get_estado_display()})


class GenerarContratoView(APIView):
    """Genera una versión de contrato y la envía al proveedor si está configurado."""

    permission_classes = [permissions.IsAuthenticated]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "contract_approval"

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
        if contrato.estado == ContratoAdopcion.Estado.FIRMADO and not contrato.documento_firmado:
            try:
                adaptador = obtener_adaptador_firma()
                descargar = getattr(adaptador, "descargar", None)
                if not callable(descargar):
                    return Response({"detail": "El adaptador no permite descargar el contrato firmado."}, status=503)
                pdf = descargar(contrato)
                contrato.documento_firmado.save(f"{contrato.public_id}.pdf", ContentFile(pdf), save=True)
            except (ErrorProveedorFirma, ProveedorNoConfigurado) as exc:
                return Response({"detail": str(exc)}, status=502)
        return Response({
            "accepted": True,
            "duplicate": not creado,
            "contractId": str(contrato.public_id),
            "status": contrato.estado,
        })


class RegistrarEntregaContratoView(APIView):
    """Finaliza la adopción tras las firmas del proveedor o las dos aprobaciones por correo."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, solicitud_id):
        from django.db import transaction
        from apps.acceso.solicitudes import cambiar_estado_solicitud

        with transaction.atomic():
            contrato = ContratoAdopcion.objects.select_for_update().select_related(
                "solicitud__albergue"
            ).filter(solicitud_id=solicitud_id).first()
            if contrato is None:
                return Response({"detail": "Contrato no encontrado."}, status=404)
            solicitud = contrato.solicitud
            if request.user.rol != User.Rol.ALBERGUE or solicitud.albergue.usuario_id != request.user.pk:
                return Response({"detail": "Solo el albergue propietario registra la entrega."}, status=403)
            if contrato.proveedor == "email-approval":
                approval = getattr(contrato, "aprobacion_correo", None)
                ready = (
                    contrato.estado == ContratoAdopcion.Estado.APROBADO
                    and approval is not None
                    and approval.estado == AprobacionContratoCorreo.Estado.CONSUMIDO
                    and approval.aprobado_por_albergue_id is not None
                )
            else:
                ready = (
                    contrato.estado == ContratoAdopcion.Estado.FIRMADO
                    and bool(contrato.documento_firmado)
                )
            if not ready:
                return Response({"detail": "Se requiere registrar la aprobación del contrato antes de la entrega."}, status=409)
            try:
                solicitud = cambiar_estado_solicitud(
                    solicitud.pk, SolicitudAdopcion.Estado.ENTREGADA, request.user
                )
            except DjangoValidationError as exc:
                raise ValidationError({"detail": exc.messages}) from exc
        return Response({"solicitudId": solicitud.pk, "estado": solicitud.estado, "fechaEntrega": solicitud.fecha_entrega})
