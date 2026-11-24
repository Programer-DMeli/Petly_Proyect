"""Creación local de eventos de auditoría de acciones sensibles."""

from apps.acceso.models import EventoAuditoria


def registrar_evento_auditoria(actor, accion, recurso_tipo, recurso_id, ocurrido_en):
    """Persiste un evento dentro de la transacción de la acción que lo originó."""
    rol = getattr(actor, "rol", None) or "SISTEMA"
    actor_id = getattr(actor, "usuario_spring_id", None)

    return EventoAuditoria.objects.create(
        actor_usuario_id=actor_id,
        actor_rol=rol,
        accion=accion,
        origen=EventoAuditoria.Origen.DJANGO,
        recurso_tipo=recurso_tipo,
        recurso_id=str(recurso_id),
        ocurrido_en=ocurrido_en,
    )
