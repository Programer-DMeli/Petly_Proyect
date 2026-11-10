import mimetypes
import os

from django.db import IntegrityError, transaction
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
from apps.albergues.models import Albergue
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
        conversations = Conversacion.objects.filter(Q(adoptante=request.user) | Q(albergue__usuario=request.user)).select_related("albergue", "albergue__usuario", "adoptante", "solicitud__mascota").prefetch_related("bloqueos", "participantes")
        return Response(ConversacionSerializer(conversations, many=True, context={"request": request}).data)

    def create(self, request):
        if request.user.rol not in ("ADOPTANTE", "ALBERGUE"):
            raise PermissionDenied()
        solicitud_id = request.data.get("solicitud_id")
        shelter_id = request.data.get("albergue_id")
        solicitud = None
        if solicitud_id not in (None, ""):
            try:
                solicitud_id = int(solicitud_id)
            except (TypeError, ValueError):
                raise ValidationError({"solicitud_id": "Indica una solicitud válida."})
            solicitud = get_object_or_404(SolicitudAdopcion.objects.select_related("albergue", "mascota"), pk=solicitud_id)
            if not solicitud.adoptante_id:
                raise ValidationError({"solicitud_id": "Esta solicitud no está vinculada a una cuenta adoptante."})
            allowed = (request.user.pk == solicitud.adoptante_id if request.user.rol == "ADOPTANTE"
                       else request.user.pk == solicitud.albergue.usuario_id)
            if not allowed:
                raise PermissionDenied("Solo los participantes de la solicitud pueden iniciar el chat.")
            albergue = solicitud.albergue
            adoptante = solicitud.adoptante
        else:
            if request.user.rol != "ADOPTANTE":
                raise ValidationError({"solicitud_id": "Un albergue debe iniciar el chat desde una solicitud."})
            try:
                shelter_id = int(shelter_id)
            except (TypeError, ValueError):
                raise ValidationError({"albergue_id": "Indica un albergue válido."})
            albergue = get_object_or_404(Albergue, pk=shelter_id, activo=True)
            adoptante = request.user
        with transaction.atomic():
            conversation = Conversacion.objects.filter(adoptante=adoptante, albergue=albergue).order_by("id").first()
            if conversation:
                if solicitud and conversation.solicitud_id is None:
                    conversation.solicitud = solicitud
                    conversation.save(update_fields=["solicitud", "actualizada_en"])
                created = False
            else:
                try:
                    with transaction.atomic():
                        conversation = Conversacion.objects.create(
                            solicitud=solicitud,
                            adoptante=adoptante,
                            albergue=albergue,
                        )
                    created = True
                except IntegrityError:
                    conversation = Conversacion.objects.filter(adoptante=adoptante, albergue=albergue).order_by("id").first()
                    if conversation is None:
                        raise
                    created = False
            ParticipanteConversacion.objects.get_or_create(conversacion=conversation, usuario_id=conversation.adoptante_id)
            ParticipanteConversacion.objects.get_or_create(conversacion=conversation, usuario=conversation.albergue.usuario)
            if created:
                welcome = BienvenidaAlbergue.objects.filter(albergue=conversation.albergue).values_list("texto", flat=True).first()
                if welcome:
                    Mensaje.objects.create(conversacion=conversation, remitente=conversation.albergue.usuario, texto=welcome, bienvenida_automatica=True)
        return Response(ConversacionSerializer(conversation, context={"request": request}).data, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)

    def retrieve(self, request, pk=None):
        conversation = self._get(request, pk)
        messages = conversation.mensajes.select_related("conversacion__albergue__usuario").prefetch_related("adjuntos", "conversacion__participantes").all()
        return Response({"conversacion": ConversacionSerializer(conversation, context={"request": request}).data,
                         "mensajes": MensajeSerializer(messages, many=True, context={"request": request}).data})

    def _get(self, request, pk):
        conversation = get_object_or_404(Conversacion.objects.select_related("albergue", "albergue__usuario", "adoptante", "solicitud__mascota").prefetch_related("bloqueos", "participantes"), pk=pk)
        if not is_participant(conversation, request.user):
            raise PermissionDenied()
        return conversation

    @transaction.atomic
    @action(detail=True, methods=["get", "post"], url_path="mensajes", parser_classes=[MultiPartParser, FormParser, JSONParser])
    def mensajes(self, request, pk=None):
        conversation = self._get(request, pk)
        if request.method == "GET":
            raw_cursor = request.query_params.get("despues_de_id", "0")
            try:
                cursor = int(raw_cursor)
            except (TypeError, ValueError):
                raise ValidationError({"despues_de_id": "El cursor debe ser un identificador entero no negativo."})
            if cursor < 0:
                raise ValidationError({"despues_de_id": "El cursor debe ser un identificador entero no negativo."})

            nuevos = conversation.mensajes.filter(pk__gt=cursor).select_related(
                "remitente", "conversacion__albergue__usuario"
            ).prefetch_related("adjuntos", "conversacion__participantes")
            destinatario_id = (
                conversation.albergue.usuario_id
                if request.user.pk == conversation.adoptante_id
                else conversation.adoptante_id
            )
            leido_hasta = conversation.participantes.filter(
                usuario_id=destinatario_id
            ).values_list("leido_hasta", flat=True).first()
            leidos_hasta_id = None
            if leido_hasta:
                leidos_hasta_id = conversation.mensajes.filter(
                    remitente=request.user,
                    creado_en__lte=leido_hasta,
                ).order_by("-pk").values_list("pk", flat=True).first()
            response = Response({
                "mensajes": MensajeSerializer(nuevos, many=True, context={"request": request}).data,
                "leidos_hasta_id": leidos_hasta_id,
            })
            response["Cache-Control"] = "private, no-store"
            return response

        if conversation.bloqueos.exclude(bloqueador=request.user).exists():
            return Response({"detail": "No puedes enviar mensajes a una persona que te bloqueó."}, status=423)
        text = str(request.data.get("texto", request.data.get("contenido", ""))).strip()
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

    @transaction.atomic
    @action(detail=True, methods=["post"], url_path="leer")
    def leer(self, request, pk=None):
        conversation = self._get(request, pk)
        raw_cursor = request.data.get("hasta_id")
        if raw_cursor is None:
            leido_hasta = timezone.now()
        else:
            try:
                message_id = int(raw_cursor)
            except (TypeError, ValueError):
                raise ValidationError({"hasta_id": "Indica el identificador del último mensaje visible."})
            if message_id < 1:
                raise ValidationError({"hasta_id": "El identificador debe ser positivo."})
            leido_hasta = conversation.mensajes.filter(pk=message_id).values_list(
                "creado_en", flat=True
            ).first()
            if leido_hasta is None:
                raise ValidationError({"hasta_id": "El mensaje no pertenece a esta conversación."})

        participante, creado = ParticipanteConversacion.objects.get_or_create(
            conversacion=conversation,
            usuario=request.user,
            defaults={"leido_hasta": leido_hasta},
        )
        if not creado:
            ParticipanteConversacion.objects.filter(pk=participante.pk).filter(
                Q(leido_hasta__isnull=True) | Q(leido_hasta__lt=leido_hasta)
            ).update(leido_hasta=leido_hasta)
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
