from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils import timezone

from .models import Mascota


@transaction.atomic
def cambiar_estado_mascota(mascota_id, nuevo_estado):
    """Actualiza el estado manteniendo la reserva de solicitudes aprobadas."""
    mascota = Mascota.objects.select_for_update().get(pk=mascota_id)
    if nuevo_estado not in Mascota.Estado.values:
        raise ValidationError("Estado de mascota no válido.")

    if nuevo_estado == Mascota.Estado.DISPONIBLE:
        from apps.acceso.models import SolicitudAdopcion
        if SolicitudAdopcion.objects.filter(
            mascota=mascota,
            estado__in=[SolicitudAdopcion.Estado.APROBADA, SolicitudAdopcion.Estado.ENTREGADA],
        ).exists():
            raise ValidationError("No se puede liberar una mascota con una solicitud aprobada.")

    if nuevo_estado == Mascota.Estado.ADOPTADO:
        from apps.acceso.models import SolicitudAdopcion
        if not SolicitudAdopcion.objects.filter(
            mascota=mascota, estado=SolicitudAdopcion.Estado.ENTREGADA
        ).exists():
            raise ValidationError("La adopción solo se finaliza después de las firmas y la entrega del contrato.")

    mascota.estado = nuevo_estado
    mascota.fecha_adopcion = timezone.localdate() if nuevo_estado == Mascota.Estado.ADOPTADO else None
    mascota.save(update_fields=["estado", "fecha_adopcion", "actualizado_en"])
    return mascota
