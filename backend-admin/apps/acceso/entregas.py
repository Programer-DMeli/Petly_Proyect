"""Procesamiento de avisos confirmados, separado de la transacción de adopción."""
import json
import re
from datetime import timedelta
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from django.conf import settings
from django.core.mail import send_mail
from django.db import connection, transaction
from django.utils import timezone

from .dispositivos import huella
from .models import AvisoExterno, DispositivoPush, EntregaExterna

Estado = EntregaExterna.Estado
CANALES = ("email", "whatsapp", "push")
MAX_INTENTOS = 5


def destinos(aviso):
    usuario = aviso.evento.solicitud.adoptante
    if not usuario or not usuario.is_active or not usuario.usuario_spring_id:
        return {}
    # Spring y Django comparten el esquema de usuarios, pero sus PK son independientes.
    with connection.cursor() as cursor:
        cursor.execute(
            """SELECT u.correo, u.telefono, COALESCE(p.email, TRUE),
                      COALESCE(p.whatsapp, TRUE), COALESCE(p.push, TRUE)
               FROM usuarios u LEFT JOIN preferencias_notificacion p ON p.usuario_id=u.id
               WHERE u.id=%s AND u.rol='ADOPTANTE'""",
            [usuario.usuario_spring_id],
        )
        cuenta = cursor.fetchone()
    if not cuenta:
        return {}
    correo, telefono, email, whatsapp, push = cuenta
    telefono = re.sub(r"[\s()-]", "", telefono or "")
    dispositivos = DispositivoPush.objects.filter(adoptante=usuario, revocado=False, token_hash__isnull=False)
    return {
        "email": {huella(correo): correo} if email and correo else {},
        "whatsapp": {huella(telefono): telefono} if whatsapp and re.fullmatch(r"\+[1-9]\d{7,14}", telefono) else {},
        # La identidad del destino se conserva al rotar el token.
        "push": {d.pk: d.token for d in dispositivos} if push else {},
    }


@transaction.atomic
def planificar(aviso_id):
    aviso = AvisoExterno.objects.select_for_update().get(pk=aviso_id)
    if aviso.planificado:
        return
    candidatos = destinos(aviso)
    for canal in CANALES:
        destinos_canal = candidatos.get(canal, {})
        if destinos_canal:
            EntregaExterna.objects.bulk_create([
                EntregaExterna(aviso=aviso, canal=canal, destino=destino)
                for destino in destinos_canal
            ])
        else:
            EntregaExterna.objects.create(
                aviso=aviso, canal=canal, estado=Estado.OMITIDA, resultado="sin_destino_habilitado",
            )
    aviso.planificado = True
    aviso.save(update_fields=["planificado"])


class ReintentoSeguro(Exception):
    pass


class SinRedireccion(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def enviar_json(url, token, cuerpo, clave):
    request = Request(
        url, data=json.dumps(cuerpo).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "Idempotency-Key": clave,
        },
    )
    with build_opener(SinRedireccion()).open(request, timeout=10) as response:
        if not 200 <= response.status < 300:
            raise RuntimeError("Respuesta inesperada")


def credencial_fcm():
    try:
        import google.auth
        from google.auth.transport.requests import Request as GoogleRequest

        credencial, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/firebase.messaging"])
        transporte = GoogleRequest()
        credencial.refresh(lambda *args, **kwargs: transporte(*args, **{**kwargs, "timeout": 10}))
        return credencial.token
    except Exception as exc:
        # Aún no se ha hecho una solicitud de entrega a FCM.
        raise ReintentoSeguro("credencial_fcm") from exc


def enviar(entrega, destino):
    evento = entrega.aviso.evento
    enlace = f"{settings.FRONTEND_URL}/adoptante/solicitudes/{evento.solicitud_id}"
    titulo = "Actualización de tu solicitud de adopción"
    mensaje = f"{evento.get_tipo_display()}. Consulta el detalle en Petly: {enlace}"
    clave = f"petly-solicitud-{evento.pk}-{entrega.canal}-{entrega.pk}"
    if entrega.canal == "email":
        if send_mail(titulo, mensaje, settings.DEFAULT_FROM_EMAIL, [destino]) != 1:
            raise ReintentoSeguro("correo_no_aceptado")
    elif entrega.canal == "whatsapp":
        if not settings.AVISOS_WHATSAPP_URL or not settings.AVISOS_WHATSAPP_TOKEN:
            raise ReintentoSeguro("proveedor_whatsapp_sin_configurar")
        enviar_json(settings.AVISOS_WHATSAPP_URL, settings.AVISOS_WHATSAPP_TOKEN, {
            "messaging_product": "whatsapp",
            "to": destino.removeprefix("+"),
            "type": "template",
            "template": {
                "name": settings.AVISOS_WHATSAPP_PLANTILLA,
                "language": {"code": settings.AVISOS_WHATSAPP_IDIOMA},
                "components": [{
                    "type": "body",
                    "parameters": [
                        {"type": "text", "text": evento.get_tipo_display()},
                        {"type": "text", "text": enlace},
                    ],
                }],
            },
        }, clave)
    else:
        if not re.fullmatch(r"[a-z0-9-]+", settings.AVISOS_FCM_PROJECT):
            raise ReintentoSeguro("proyecto_fcm_sin_configurar")
        enviar_json(
            f"https://fcm.googleapis.com/v1/projects/{settings.AVISOS_FCM_PROJECT}/messages:send",
            credencial_fcm(),
            {"message": {"token": destino, "android": {"priority": "HIGH", "ttl": "86400s"}, "data": {
                "evento": str(evento.pk), "vinculo": entrega.destino,
            }}},
            clave,
        )


@transaction.atomic
def reclamar(entrega_id):
    entrega = EntregaExterna.objects.select_related("aviso__evento__solicitud__adoptante").get(pk=entrega_id)
    destino = destinos(entrega.aviso).get(entrega.canal, {}).get(entrega.destino)
    ahora = timezone.now()
    # Una sola instancia puede reclamar cada intento. Tras un resultado incierto no reenviamos.
    reclamada = EntregaExterna.objects.filter(
        pk=entrega_id, estado__in=[Estado.PENDIENTE, Estado.REINTENTAR],
        intentos=entrega.intentos, proximo_intento__lte=ahora,
    ).update(estado=Estado.EN_CURSO, intentos=entrega.intentos + 1, actualizado_en=ahora)
    if not reclamada:
        return
    entrega.intentos += 1
    return entrega, destino


def procesar(entrega_id):
    reclamo = reclamar(entrega_id)
    if not reclamo:
        return
    entrega, destino = reclamo
    estado, resultado = Estado.ACEPTADA, "aceptada_por_proveedor"
    try:
        if destino:
            enviar(entrega, destino)
        else:
            estado, resultado = Estado.OMITIDA, "destino_ya_no_habilitado"
    except ReintentoSeguro as exc:
        estado, resultado = Estado.REINTENTAR, str(exc)
    except HTTPError as exc:
        resultado = f"http_{exc.code}"
        if exc.code == 429:
            estado = Estado.REINTENTAR
        elif 400 <= exc.code < 500 and exc.code != 408:
            estado = Estado.FALLIDA
        else:
            estado = Estado.INCIERTA
        exc.close()
    except Exception as exc:
        estado, resultado = Estado.INCIERTA, type(exc).__name__[:60]
    if estado == Estado.REINTENTAR and entrega.intentos >= MAX_INTENTOS:
        estado = Estado.FALLIDA
    ahora = timezone.now()
    EntregaExterna.objects.filter(pk=entrega.pk, estado=Estado.EN_CURSO).update(
        estado=estado, resultado=resultado, actualizado_en=ahora,
        proximo_intento=ahora + timedelta(minutes=2 ** (entrega.intentos - 1)),
    )


def procesar_lote(limite=100):
    ahora = timezone.now()
    EntregaExterna.objects.filter(estado=Estado.EN_CURSO, actualizado_en__lt=ahora - timedelta(minutes=10)).update(
        estado=Estado.INCIERTA, resultado="proceso_interrumpido", actualizado_en=ahora,
    )
    for aviso_id in AvisoExterno.objects.filter(planificado=False).order_by("pk").values_list("pk", flat=True)[:limite]:
        planificar(aviso_id)
    pendientes = EntregaExterna.objects.filter(
        estado__in=[Estado.PENDIENTE, Estado.REINTENTAR], proximo_intento__lte=timezone.now(),
    ).order_by("proximo_intento", "pk").values_list("pk", flat=True)
    for entrega_id in list(pendientes[:limite]):
        procesar(entrega_id)
