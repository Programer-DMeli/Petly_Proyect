"""Reglas transaccionales para las decisiones de solicitudes de adopción."""
from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from apps.mascotas.models import Mascota
from .models import AdoptanteProfile, Autorizacion, SolicitudAdopcion, SolicitudEvento


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
    solicitud = SolicitudAdopcion.objects.select_for_update().select_related(
        "mascota", "albergue"
    ).get(pk=solicitud_id)
    mascota = Mascota.objects.select_for_update().get(pk=solicitud.mascota_id)
    actual = solicitud.estado

    if nuevo_estado not in TRANSICIONES.get(actual, []):
        raise ValidationError({
            "estado": f"Transición no permitida: {actual} -> {nuevo_estado}.",
            "transiciones_validas": TRANSICIONES.get(actual, []),
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

        perfil = AdoptanteProfile.objects.filter(
            documento_numero=solicitud.postulante.documento_numero,
            cuestionario_completado=True,
        ).first()
        if perfil is None:
            raise ValidationError({"autorizacion": "El perfil del adoptante ya no está finalizado."})
        autorizacion = Autorizacion.objects.select_for_update().filter(
            adoptante=perfil.user,
            albergue=solicitud.albergue,
            estado=Autorizacion.Estado.VIGENTE,
        ).first()
        if autorizacion is None or not autorizacion.esta_vigente():
            raise ValidationError({"autorizacion": "Se requiere autorización vigente para aprobar esta solicitud."})

    solicitud.estado = nuevo_estado
    solicitud.revisado_por = usuario
    solicitud.fecha_revision = ahora
    if observaciones is not None:
        solicitud.observaciones_revision = motivo

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

    solicitud.save(update_fields=[
        "estado", "revisado_por", "fecha_revision", "observaciones_revision",
        "fecha_entrega", "actualizado_en",
    ])
    tipo_evento = {
        SolicitudAdopcion.Estado.OBSERVADA: SolicitudEvento.Tipo.OBSERVADA,
        SolicitudAdopcion.Estado.APROBADA: SolicitudEvento.Tipo.APROBADA,
        SolicitudAdopcion.Estado.RECHAZADA: SolicitudEvento.Tipo.RECHAZADA,
        SolicitudAdopcion.Estado.CANCELADA: SolicitudEvento.Tipo.CANCELADA,
        SolicitudAdopcion.Estado.ENTREGADA: SolicitudEvento.Tipo.ENTREGADA,
    }[nuevo_estado]
    SolicitudEvento.objects.create(
        solicitud=solicitud,
        tipo=tipo_evento,
        estado_anterior=actual,
        estado_nuevo=nuevo_estado,
        descripcion=motivo,
        ocurrido_en=ahora,
        actor=usuario,
        visible_adoptante=True,
    )
    return solicitud
