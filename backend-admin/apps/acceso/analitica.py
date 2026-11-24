"""Consultas agregadas de impacto de adopciones para administración (US-66)."""
from django.db.models import Count
from django.db.models.functions import ExtractMonth, ExtractYear
from rest_framework import permissions
from rest_framework import serializers
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.mascotas.models import Mascota
from .models import SolicitudAdopcion, User


class FiltroRangoAnaliticaSerializer(serializers.Serializer):
    """Valida límites de fecha opcionales para los reportes de adopción."""

    desde = serializers.DateField(required=False)
    hasta = serializers.DateField(required=False)

    def validate(self, attrs):
        desde = attrs.get("desde")
        hasta = attrs.get("hasta")
        if desde and hasta and desde > hasta:
            raise serializers.ValidationError({"hasta": "Debe ser igual o posterior a la fecha inicial."})
        return attrs


def entregas_adopcion(*, desde=None, hasta=None):
    """Retorna entregas confirmadas, con límites inclusivos opcionales."""
    entregas = SolicitudAdopcion.objects.filter(
        estado=SolicitudAdopcion.Estado.ENTREGADA,
        fecha_entrega__isnull=False,
    )
    if desde:
        entregas = entregas.filter(fecha_entrega__gte=desde)
    if hasta:
        entregas = entregas.filter(fecha_entrega__lte=hasta)
    return entregas


def resumir_adopciones(entregas):
    """Agrupa un mismo conjunto filtrado por mes y especie para la analítica."""
    por_mes = list(
        entregas.annotate(
            anio=ExtractYear("fecha_entrega"),
            mes=ExtractMonth("fecha_entrega"),
        )
        .values("anio", "mes")
        .annotate(total=Count("mascota_id", distinct=True))
        .order_by("anio", "mes")
    )

    conteos_especie = {
        fila["mascota__especie"]: fila["total"]
        for fila in entregas.values("mascota__especie")
        .annotate(total=Count("mascota_id", distinct=True))
    }
    por_especie = [
        {
            "especie": especie,
            "nombre": etiqueta,
            "total": conteos_especie.get(especie, 0),
        }
        for especie, etiqueta in Mascota.Especie.choices
    ]

    return {
        "total_adopciones": entregas.values("mascota_id").distinct().count(),
        "por_mes": por_mes,
        "por_especie": por_especie,
    }


class EsAdministradorPetly(permissions.BasePermission):
    """El panel global de métricas solo está disponible para ADMIN."""

    def has_permission(self, request, view):
        usuario = request.user
        return bool(
            usuario
            and usuario.is_authenticated
            and (usuario.rol == User.Rol.ADMIN or usuario.is_superuser)
        )


class AnaliticaAdopcionesView(APIView):
    """Entrega conteos de mascotas adoptadas por mes de entrega y especie."""

    permission_classes = [permissions.IsAuthenticated, EsAdministradorPetly]

    def get(self, request):
        filtros = FiltroRangoAnaliticaSerializer(data=request.query_params)
        filtros.is_valid(raise_exception=True)
        rango = filtros.validated_data
        return Response(resumir_adopciones(
            entregas_adopcion(desde=rango.get("desde"), hasta=rango.get("hasta"))
        ))
