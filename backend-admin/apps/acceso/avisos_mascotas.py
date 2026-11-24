"""Genera avisos al ingresar mascotas, nunca al consultar o cambiar criterios."""
from django.db.models import Case, Q, Value, When
from django.db.models.signals import post_save
from django.dispatch import receiver
from apps.mascotas.models import Mascota
from .models import SolicitudEvento, User


@receiver(post_save, sender=Mascota, dispatch_uid="avisar_nueva_mascota")
def avisar_nueva_mascota(sender, instance, created, raw=False, using=None, **kwargs):
    if raw or not created or instance.estado != Mascota.Estado.DISPONIBLE:
        return
    if not instance.albergue.activo:
        return
    activos = Q(criteriosaviso__activo=True)
    sin_filtros = activos & Q(
        criteriosaviso__especie="", criteriosaviso__tamanio="", criteriosaviso__temperamento="",
        criteriosaviso__edad_min_meses__isnull=True, criteriosaviso__edad_max_meses__isnull=True,
    )
    aviso_general = Q(criteriosaviso__isnull=True) | sin_filtros
    coincidencia = activos
    for campo in ("especie", "tamanio", "temperamento"):
        filtro = f"criteriosaviso__{campo}"
        coincidencia &= Q(**{filtro: ""}) | Q(**{filtro: getattr(instance, campo)})
    edad_minima = Q(criteriosaviso__edad_min_meses__isnull=True)
    edad_maxima = Q(criteriosaviso__edad_max_meses__isnull=True)
    if instance.edad_anos is not None or instance.edad_meses is not None:
        meses = int(instance.edad_anos or 0) * 12 + int(instance.edad_meses or 0)
        edad_minima |= Q(criteriosaviso__edad_min_meses__lte=meses)
        edad_maxima |= Q(criteriosaviso__edad_max_meses__gte=meses)
    destinatarios = User.objects.using(using).filter(
        aviso_general | (coincidencia & edad_minima & edad_maxima),
        rol=User.Rol.ADOPTANTE, is_active=True,
    ).annotate(
        tipo_aviso=Case(
            When(aviso_general, then=Value(SolicitudEvento.Tipo.NUEVA_MASCOTA)),
            default=Value(SolicitudEvento.Tipo.NUEVA_COINCIDENCIA),
        ),
    ).order_by("pk").values_list("pk", "tipo_aviso")
    for destinatario_id, tipo in destinatarios.iterator():
        SolicitudEvento.objects.using(using).get_or_create(
            destinatario_id=destinatario_id, mascota_coincidente=instance,
            defaults={"tipo": tipo},
        )
