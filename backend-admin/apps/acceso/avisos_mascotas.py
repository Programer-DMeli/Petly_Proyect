"""Genera avisos al ingresar mascotas, nunca al consultar o cambiar criterios."""
from django.db.models import Q
from django.db.models.signals import post_save
from django.dispatch import receiver
from apps.mascotas.models import Mascota
from .models import CriteriosAviso, SolicitudEvento


@receiver(post_save, sender=Mascota, dispatch_uid="avisar_nueva_mascota")
def avisar_nueva_mascota(sender, instance, created, raw=False, **kwargs):
    if raw or not created or instance.estado != Mascota.Estado.DISPONIBLE:
        return
    criterios = CriteriosAviso.objects.filter(activo=True, adoptante__is_active=True, adoptante__rol="ADOPTANTE")
    for campo in ("especie", "tamanio", "temperamento"):
        criterios = criterios.filter(Q(**{campo: ""}) | Q(**{campo: getattr(instance, campo)}))
    meses = None if instance.edad_anos is None and instance.edad_meses is None else (instance.edad_anos or 0) * 12 + (instance.edad_meses or 0)
    for criterio in criterios.iterator():
        if not any((criterio.especie, criterio.tamanio, criterio.temperamento)) and criterio.edad_min_meses is None and criterio.edad_max_meses is None:
            continue
        if criterio.edad_min_meses is not None and (meses is None or meses < criterio.edad_min_meses):
            continue
        if criterio.edad_max_meses is not None and (meses is None or meses > criterio.edad_max_meses):
            continue
        SolicitudEvento.objects.get_or_create(
            destinatario_id=criterio.adoptante_id, mascota_coincidente=instance,
            defaults={"tipo": "NUEVA_COINCIDENCIA"},
        )
