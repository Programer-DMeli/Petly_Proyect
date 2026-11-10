from django.db import transaction
from django.db.models import Q
from django.utils import timezone

from .models import AlertaIncumplimientoSeguimiento, SeguimientoPostAdopcion


@transaction.atomic
def generar_alertas_incumplimiento(ahora=None):
    """Crea alertas vencidas una sola vez y resuelve las que dejaron de estar pendientes."""
    ahora = ahora or timezone.now()

    vencidas = SeguimientoPostAdopcion.objects.filter(
        solicitud__estado="ENTREGADA",
        estado__in=(SeguimientoPostAdopcion.Estado.PROGRAMADO, SeguimientoPostAdopcion.Estado.REALIZADO),
        programado_para__lte=ahora,
        reporte_bienestar__isnull=True,
        solicitud__expediente_post_adopcion__isnull=True,
    ).order_by("pk")

    creadas = 0
    for seguimiento in vencidas.iterator():
        _, creada = AlertaIncumplimientoSeguimiento.objects.get_or_create(seguimiento=seguimiento)
        creadas += int(creada)

    pendientes = AlertaIncumplimientoSeguimiento.objects.filter(estado=AlertaIncumplimientoSeguimiento.Estado.PENDIENTE)
    ya_atendidas = pendientes.filter(
        Q(seguimiento__estado=SeguimientoPostAdopcion.Estado.CANCELADO)
        | Q(seguimiento__reporte_bienestar__isnull=False)
        | Q(seguimiento__solicitud__estado__in=("CANCELADA", "RECHAZADA"))
        | Q(seguimiento__solicitud__expediente_post_adopcion__isnull=False)
    )
    resueltas = ya_atendidas.update(
        estado=AlertaIncumplimientoSeguimiento.Estado.RESUELTA,
        resuelta_en=ahora,
    )
    return creadas, resueltas
