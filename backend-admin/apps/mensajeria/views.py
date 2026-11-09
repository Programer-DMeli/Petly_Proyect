import mimetypes
import os

from django.db import transaction
from django.db.models import Q
from django.http import FileResponse, Http404
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.utils.http import content_disposition_header
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion
from .models import (AdjuntoMensaje, BienvenidaAlbergue, BloqueoConversacion,
                     Conversacion, Mensaje, ParticipanteConversacion, ReporteConversacion)
from .serializers import ConversacionSerializer, MensajeSerializer

MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
ALLOWED_TYPES = {"image/jpeg", "image/png", "application/pdf"}


def is_participant(conversation, user):
    return user.pk == conversation.adoptante_id or user.pk == conversation.albergue.usuario_id


class ConversacionViewSet(viewsets.ViewSet):
    permission_classes = [IsAuthenticated]

    def list(self, request):
        if request.user.rol not in ("ADOPTANTE", "ALBERGUE"):
            raise PermissionDenied()
        conversations = Conversacion.objects.filter(Q(adoptante=request.user) | Q(albergue__usuario=request.user)).select_related("albergue", "adoptante", "solicitud__mascota")
        return Response(ConversacionSerializer(conversations, many=True, context={"request": request}).data)

    def create(self, request):
        if request.user.rol not in ("ADOPTANTE", "ALBERGUE"):
            raise PermissionDenied()
        try:
            solicitud_id = int(request.data.get("solicitud_id"))
        except (TypeError, ValueError):
            raise ValidationError({"solicitud_id": "Indica una solicitud válida."})
        solicitud = get_object_or_404(SolicitudAdopcion.objects.select_related("albergue", "mascota"), pk=solicitud_id)
        allowed = (request.user.pk == solicitud.adoptante_id if request.user.rol == "ADOPTANTE"
                   else request.user.pk == solicitud.albergue.usuario_id)
        if not allowed:
            raise PermissionDenied("Solo los participantes de la solicitud pueden iniciar el chat.")
        with transaction.atomic():
            conversation, created = Conversacion.objects.get_or_create(
                solicitud=solicitud,
                defaults={"adoptante_id": solicitud.adoptante_id, "albergue": solicitud.albergue},
            )
            ParticipanteConversacion.objects.get_or_create(conversacion=conversation, usuario_id=conversation.adoptante_id)
            ParticipanteConversacion.objects.get_or_create(conversacion=conversation, usuario=conversation.albergue.usuario)
            if created:
                welcome = BienvenidaAlbergue.objects.filter(albergue=conversation.albergue).values_list("texto", flat=True).first()
                if welcome:
                    Mensaje.objects.create(conversacion=conversation, remitente=conversation.albergue.usuario, texto=welcome, bienvenida_automatica=True)
        return Response(ConversacionSerializer(conversation, context={"request": request}).data, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)

    def retrieve(self, request, pk=None):
        conversation = self._get(request, pk)
        messages = conversation.mensajes.prefetch_related("adjuntos").all()
        return Response({"conversacion": ConversacionSerializer(conversation, context={"request": request}).data,
                         "mensajes": MensajeSerializer(messages, many=True, context={"request": request}).data})

    def _get(self, request, pk):
        conversation = get_object_or_404(Conversacion.objects.select_related("albergue", "adoptante", "solicitud__mascota"), pk=pk)
        if not is_participant(conversation, request.user):
            raise PermissionDenied()
        return conversation

    @action(detail=True, methods=["post"], url_path="mensajes", parser_classes=[MultiPartParser, FormParser, JSONParser])
    def mensajes(self, request, pk=None):
        conversation = self._get(request, pk)
        if conversation.bloqueos.exists():
            return Response({"detail": "No se pueden enviar mensajes en una conversación bloqueada."}, status=423)
        text = str(request.data.get("texto", "")).strip()
        upload = request.FILES.get("archivo")
        if not text and not upload:
            raise ValidationError({"texto": "Escribe un mensaje o adjunta un archivo."})
        if len(text) > 4000:
            raise ValidationError({"texto": "El mensaje supera los 4000 caracteres."})
        if upload:
            content_type = (upload.content_type or mimetypes.guess_type(upload.name)[0] or "").lower()
            if content_type not in ALLOWED_TYPES or os.path.splitext(upload.name)[1].lower() not in {".jpg", ".jpeg", ".png", ".pdf"}:
                raise ValidationError({"archivo": "Adjunta una imagen JPEG/PNG o un PDF."})
            if upload.size > MAX_ATTACHMENT_BYTES:
                raise ValidationError({"archivo": "El archivo supera el límite de 10 MB."})
            signature = upload.read(8)
            upload.seek(0)
            valid_signature = {
                "image/jpeg": signature.startswith(b"\xff\xd8\xff"),
                "image/png": signature.startswith(b"\x89PNG\r\n\x1a\n"),
                "application/pdf": signature.startswith(b"%PDF-"),
            }.get(content_type, False)
            if not valid_signature:
                raise ValidationError({"archivo": "El contenido del archivo no coincide con su formato declarado."})
        message = Mensaje.objects.create(conversacion=conversation, remitente=request.user, texto=text)
        if upload:
            AdjuntoMensaje.objects.create(mensaje=message, archivo=upload, nombre_original=os.path.basename(upload.name)[:255], content_type=content_type, tamaño_bytes=upload.size)
        conversation.save(update_fields=["actualizada_en"])
        return Response(MensajeSerializer(message, context={"request": request}).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], url_path="leer")
    def leer(self, request, pk=None):
        conversation = self._get(request, pk)
        ParticipanteConversacion.objects.update_or_create(conversacion=conversation, usuario=request.user, defaults={"leido_hasta": timezone.now()})
        return Response(status=status.HTTP_204_NO_CONTENT)

    @action(detail=True, methods=["post"], url_path="bloquear")
    def bloquear(self, request, pk=None):
        conversation = self._get(request, pk)
        BloqueoConversacion.objects.get_or_create(conversacion=conversation, bloqueador=request.user, defaults={"motivo": str(request.data.get("motivo", ""))[:500]})
        return Response({"bloqueada": True}, status=status.HTTP_200_OK)

    @action(detail=True, methods=["post"], url_path="reportar")
    def reportar(self, request, pk=None):
        conversation = self._get(request, pk)
        reason = request.data.get("motivo")
        if reason not in ReporteConversacion.Motivo.values:
            raise ValidationError({"motivo": "Selecciona un motivo válido."})
        report = ReporteConversacion.objects.create(conversacion=conversation, reportante=request.user, motivo=reason, detalle=str(request.data.get("detalle", ""))[:1000])
        return Response({"id": report.pk, "registrado": True}, status=status.HTTP_201_CREATED)


class BienvenidaView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        if request.user.rol != "ALBERGUE":
            raise PermissionDenied()
        try:
            welcome = BienvenidaAlbergue.objects.get(albergue__usuario=request.user)
            return Response({"texto": welcome.texto})
        except BienvenidaAlbergue.DoesNotExist:
            return Response({"texto": ""})

    def put(self, request):
        if request.user.rol != "ALBERGUE":
            raise PermissionDenied()
        text = str(request.data.get("texto", "")).strip()
        if len(text) > 1000:
            raise ValidationError({"texto": "Máximo 1000 caracteres."})
        if not text:
            BienvenidaAlbergue.objects.filter(albergue__usuario=request.user).delete()
            return Response({"texto": ""})
        from apps.albergues.models import Albergue
        albergue = get_object_or_404(Albergue, usuario=request.user)
        welcome, _ = BienvenidaAlbergue.objects.update_or_create(albergue=albergue, defaults={"texto": text})
        return Response({"texto": welcome.texto})


class AdjuntoView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, pk):
        attachment = get_object_or_404(AdjuntoMensaje.objects.select_related("mensaje__conversacion__albergue"), pk=pk)
        if not is_participant(attachment.mensaje.conversacion, request.user):
            raise PermissionDenied()
        try:
            file_handle = attachment.archivo.open("rb")
        except (FileNotFoundError, OSError):
            raise Http404()
        response = FileResponse(file_handle, content_type=attachment.content_type)
        response["Content-Disposition"] = content_disposition_header(True, attachment.nombre_original)
        response["X-Content-Type-Options"] = "nosniff"
        response["Cache-Control"] = "private, no-store"
        return response
