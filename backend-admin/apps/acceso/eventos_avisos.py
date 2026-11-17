"""Notificaciones de cambio sin datos privados, persistidas junto al evento de adopción."""
import asyncio
import json
import time

from asgiref.sync import sync_to_async
from django.core.handlers.asgi import ASGIRequest
from django.db import transaction
from django.db.models.signals import post_save
from django.dispatch import receiver
from django.http import StreamingHttpResponse
from rest_framework.decorators import api_view, permission_classes, renderer_classes
from rest_framework.exceptions import PermissionDenied
from rest_framework.permissions import IsAuthenticated
from rest_framework.renderers import JSONRenderer
from rest_framework_simplejwt.exceptions import InvalidToken

from .authentication import ComprobacionSesionNoDisponible, SpringBootJWTAuthentication

from .models import CanalAvisos, SolicitudEvento, User
from .notificaciones import MENSAJES

MAX_CAMBIOS = 64
INTERVALO = 0.25
LATIDO = 15
DURACION_MAXIMA = 600


@receiver(post_save, sender=SolicitudEvento, dispatch_uid="registrar_cambio_avisos")
def registrar_cambio_avisos(sender, instance, created, raw=False, using=None, **kwargs):
    if not created or raw or not instance.visible_adoptante or instance.tipo not in MENSAJES:
        return
    if instance.tipo == SolicitudEvento.Tipo.NUEVA_COINCIDENCIA:
        adoptante_id = instance.destinatario_id
    elif instance.solicitud_id:
        adoptante_id = instance.solicitud.adoptante_id
    else:
        return
    if not adoptante_id:
        return

    # La fila del canal serializa cambios concurrentes del mismo destinatario.
    # El lector solo ve revisiones confirmadas por la transacción.
    with transaction.atomic(using=using):
        canal, _ = CanalAvisos.objects.using(using).select_for_update().get_or_create(adoptante_id=adoptante_id)
        canal.solicitudes = [*canal.solicitudes, instance.solicitud_id][-MAX_CAMBIOS:]
        canal.revision += 1
        canal.save(using=using, update_fields=["solicitudes", "revision"])


def mensaje(tipo, datos=None):
    return f"event: {tipo}\ndata: {json.dumps(datos or {}, separators=(',', ':'))}\n\n"


class FlujoAvisos:
    def __init__(self, adoptante_id, exp, auth=None):
        self.canal = CanalAvisos.objects.filter(pk=adoptante_id).values_list("revision", "solicitudes")
        self.revision = None
        self.vencimiento = min(exp, time.time() + DURACION_MAXIMA)
        self.ultimo_envio = time.monotonic()
        self.auth = auth

    def trama(self, canal):
        revision, solicitudes = canal or (0, [])
        if self.revision is None:
            self.revision = revision
            return mensaje("sincronizar")

        diferencia = revision - self.revision
        if diferencia:
            self.revision = revision
            self.ultimo_envio = time.monotonic()
            if diferencia < 0 or diferencia > len(solicitudes):
                return mensaje("sincronizar")
            afectadas = sorted({pk for pk in solicitudes[-diferencia:] if pk is not None})
            return mensaje("avisos", {"solicitudes": afectadas})
        if time.monotonic() - self.ultimo_envio >= LATIDO:
            self.ultimo_envio = time.monotonic()
            return ": latido\n\n"
        return None

    def sesion_vigente(self):
        sid = self.auth.get("sid") if self.auth is not None else None
        if sid is None:
            return True
        try:
            SpringBootJWTAuthentication.comprobar_sesion(sid, self.auth)
        except (InvalidToken, ComprobacionSesionNoDisponible):
            return False
        return True

    def iterar(self):
        while time.time() < self.vencimiento:
            trama = self.trama(self.canal.first())
            if trama:
                if not self.sesion_vigente():
                    return
                yield trama
            time.sleep(INTERVALO)

    async def iterar_async(self):
        sesion_vigente = sync_to_async(self.sesion_vigente)
        while time.time() < self.vencimiento:
            trama = self.trama(await self.canal.afirst())
            if trama:
                if not await sesion_vigente():
                    return
                yield trama
            await asyncio.sleep(INTERVALO)


class EventStreamRenderer(JSONRenderer):
    # La respuesta exitosa se transmite directamente. DRF negocia los errores HTTP.
    media_type = "text/event-stream"
    format = "sse"


@api_view(["GET"])
@permission_classes([IsAuthenticated])
@renderer_classes([JSONRenderer, EventStreamRenderer])
def eventos_avisos(request):
    if request.user.rol != User.Rol.ADOPTANTE or not request.user.is_active:
        raise PermissionDenied()
    flujo = FlujoAvisos(request.user.pk, request.auth["exp"], request.auth)
    iterador = flujo.iterar_async() if isinstance(request._request, ASGIRequest) else flujo.iterar()
    return StreamingHttpResponse(iterador, content_type="text/event-stream", headers={
        "Cache-Control": "private, no-store, no-transform",
        "X-Accel-Buffering": "no",
    })
