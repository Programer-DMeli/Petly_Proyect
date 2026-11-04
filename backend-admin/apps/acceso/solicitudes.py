"""Reglas transaccionales para las decisiones de solicitudes de adopción."""
from django.db import transaction
from django.conf import settings
from django.core.mail import send_mail
from django.db import connection
from django.utils.html import strip_tags
from django.utils import timezone
import logging
from rest_framework.exceptions import ValidationError

from apps.mascotas.models import Mascota
from .models import AdoptanteProfile, Autorizacion, SolicitudAdopcion, SolicitudEvento

logger = logging.getLogger(__name__)


def _notificar_cambio_estado_por_email(evento_id):
    """Envía email tras confirmar la transición, respetando la preferencia Spring."""
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT email FROM preferencias_notificacion WHERE usuario_id = %s",
                [SolicitudEvento.objects.select_related("solicitud").get(pk=evento_id).solicitud.adoptante_id],
            )
            preferencia = cursor.fetchone()
        evento = SolicitudEvento.objects.select_related(
            "solicitud__adoptante", "solicitud__mascota", "solicitud__albergue"
        ).get(pk=evento_id)
        usuario = evento.solicitud.adoptante
        if not usuario or not usuario.email:
            return
        # Si aún no hay fila de preferencias, coincide con el default de Spring: canales activos.
        if preferencia and not preferencia[0]:
            return
        mensaje = (
            f"{evento.get_tipo_display()} para {evento.solicitud.mascota.nombre}.\n\n"
            f"{evento.descripcion or 'El estado de tu solicitud cambió a ' + evento.estado_nuevo + '.'}\n"
            f"Albergue: {evento.solicitud.albergue.nombre}.\n"
            f"Consulta el detalle en Petly: {settings.FRONTEND_URL}/adoptante/solicitudes/{evento.solicitud_id}"
        )
        send_mail(
            subject=f"Actualización de tu solicitud de adopción — {evento.solicitud.mascota.nombre}",
            message=strip_tags(mensaje),
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[usuario.email],
            fail_silently=False,
        )
    except Exception:
        # Una falla del correo no debe revertir la transición ya guardada.
        logger.exception("No se pudo enviar el correo de actualización de solicitud evento=%s", evento_id)


TRANSICIONES = {
    SolicitudAdopcion.Estado.PENDIENTE: [
        SolicitudAdopcion.Estado.OBSERVADA,
        SolicitudAdopcion.Estado.APROBADA,
        SolicitudAdopcion.Estado.RECHAZADA,
        SolicitudAdopcion.Estado.CANCELADA,
    ],
    SolicitudAdopcion.Estado.OBSERVADA: [
        SolicitudAdopcion.Estado.APROBADA,
        SolicitudAdopcion.Estado.RECHAZADA,
        SolicitudAdopcion.Estado.CANCELADA,
    ],
    SolicitudAdopcion.Estado.APROBADA: [SolicitudAdopcion.Estado.ENTREGADA],
    SolicitudAdopcion.Estado.RECHAZADA: [],
    SolicitudAdopcion.Estado.CANCELADA: [],
    SolicitudAdopcion.Estado.ENTREGADA: [],
}


@transaction.atomic
def cambiar_estado_solicitud(solicitud_id, nuevo_estado, usuario, observaciones=None):
    """Aplica una transición bajo bloqueo para evitar dobles aprobaciones."""
    # Todas las transiciones y envíos toman primero la misma mascota.
    mascota_id = SolicitudAdopcion.objects.values_list("mascota_id", flat=True).get(pk=solicitud_id)
    mascota = Mascota.objects.select_for_update().get(pk=mascota_id)
    solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=solicitud_id)
    actual = solicitud.estado

    if nuevo_estado == SolicitudAdopcion.Estado.CANCELADA and (
        getattr(usuario, "rol", None) != "ADOPTANTE"
        or solicitud.adoptante_id != usuario.pk
    ):
        raise ValidationError({
            "estado": "Solo el adoptante titular puede cancelar esta solicitud.",
        })

    if nuevo_estado not in TRANSICIONES.get(actual, []):
        raise ValidationError({
            "estado": f"Transición no permitida: {actual} -> {nuevo_estado}.",
            "transiciones_validas": TRANSICIONES.get(actual, []),
        })

    if nuevo_estado == SolicitudAdopcion.Estado.ENTREGADA:
        from apps.contratos.models import ContratoAdopcion

        contrato = getattr(solicitud, "contrato_adopcion", None)
        if (
            contrato is None
            or contrato.estado != ContratoAdopcion.Estado.FIRMADO
            or not contrato.documento_firmado
        ):
            raise ValidationError({
                "estado": "La adopción solo puede cerrarse con un contrato firmado y entregado.",
            })

    requiere_motivo = nuevo_estado in (
        SolicitudAdopcion.Estado.OBSERVADA,
        SolicitudAdopcion.Estado.RECHAZADA,
    )
    motivo = (observaciones or "").strip()
    if requiere_motivo and not motivo:
        raise ValidationError({"observaciones_revision": "Indica el motivo de la observación o el rechazo."})

    ahora = timezone.now()
    if nuevo_estado == SolicitudAdopcion.Estado.APROBADA:
        if mascota.estado != Mascota.Estado.DISPONIBLE:
            raise ValidationError({"estado": "La mascota ya no está disponible para aprobación."})
        otra_aprobada = SolicitudAdopcion.objects.filter(
            mascota=mascota,
            estado__in=[SolicitudAdopcion.Estado.APROBADA, SolicitudAdopcion.Estado.ENTREGADA],
        ).exclude(pk=solicitud.pk).exists()
        if otra_aprobada:
            raise ValidationError({"estado": "Ya existe otra solicitud aprobada para esta mascota."})

        from .eligibilidad import comprobar_elegibilidad
        if not solicitud.adoptante:
            # Compatibilidad con solicitudes históricas vinculadas por documento.
            perfil = AdoptanteProfile.objects.filter(documento_numero=solicitud.postulante.documento_numero).first() if solicitud.postulante else None
            if not perfil:
                raise ValidationError({"perfil": "No se encontró el titular de la solicitud."})
            titular = perfil.user
        else:
            titular = solicitud.adoptante
        comprobar_elegibilidad(titular, solicitud.albergue_id, bloquear=True)

    solicitud.estado = nuevo_estado
    update_fields = ["estado", "actualizado_en"]
    if nuevo_estado != SolicitudAdopcion.Estado.CANCELADA:
        solicitud.revisado_por = usuario
        solicitud.fecha_revision = ahora
        update_fields.extend(["revisado_por", "fecha_revision"])
    if observaciones is not None:
        solicitud.observaciones_revision = motivo
        update_fields.append("observaciones_revision")

    if nuevo_estado == SolicitudAdopcion.Estado.APROBADA:
        # La mascota queda reservada mientras se coordina la entrega.
        mascota.estado = Mascota.Estado.EN_PROCESO
        mascota.save(update_fields=["estado", "actualizado_en"])
    elif nuevo_estado == SolicitudAdopcion.Estado.RECHAZADA and actual == SolicitudAdopcion.Estado.APROBADA:
        # Liberar la mascota al cancelar una aprobación, si no hay otra adopción activa.
        hay_otra = SolicitudAdopcion.objects.filter(
            mascota=mascota,
            estado__in=[SolicitudAdopcion.Estado.APROBADA, SolicitudAdopcion.Estado.ENTREGADA],
        ).exclude(pk=solicitud.pk).exists()
        if not hay_otra and mascota.estado == Mascota.Estado.EN_PROCESO:
            mascota.estado = Mascota.Estado.DISPONIBLE
            mascota.save(update_fields=["estado", "actualizado_en"])
    elif nuevo_estado == SolicitudAdopcion.Estado.ENTREGADA:
        solicitud.fecha_entrega = ahora.date()
        mascota.estado = Mascota.Estado.ADOPTADO
        mascota.fecha_adopcion = ahora.date()
        mascota.save(update_fields=["estado", "fecha_adopcion", "actualizado_en"])

    solicitud.save(update_fields=update_fields)
    tipo_evento = {
        SolicitudAdopcion.Estado.OBSERVADA: SolicitudEvento.Tipo.OBSERVADA,
        SolicitudAdopcion.Estado.APROBADA: SolicitudEvento.Tipo.APROBADA,
        SolicitudAdopcion.Estado.RECHAZADA: SolicitudEvento.Tipo.RECHAZADA,
        SolicitudAdopcion.Estado.CANCELADA: SolicitudEvento.Tipo.CANCELADA,
        SolicitudAdopcion.Estado.ENTREGADA: SolicitudEvento.Tipo.ENTREGADA,
    }[nuevo_estado]
    evento = SolicitudEvento.objects.create(
        solicitud=solicitud,
        tipo=tipo_evento,
        estado_anterior=actual,
        estado_nuevo=nuevo_estado,
        descripcion=motivo,
        ocurrido_en=ahora,
        actor=usuario,
        visible_adoptante=True,
    )
    transaction.on_commit(lambda evento_id=evento.pk: _notificar_cambio_estado_por_email(evento_id))
    return solicitud
