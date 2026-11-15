"""Consultas agregadas de impacto de adopciones para administración (US-66)."""
from django.db.models import Count, ExtractMonth, ExtractYear
from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.mascotas.models import Mascota
from .models import SolicitudAdopcion, User


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
        entregas = SolicitudAdopcion.objects.filter(
            estado=SolicitudAdopcion.Estado.ENTREGADA,
            fecha_entrega__isnull=False,
        )

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

        total_adopciones = entregas.values("mascota_id").distinct().count()
        return Response({
            "total_adopciones": total_adopciones,
            "por_mes": por_mes,
            "por_especie": por_especie,
        })
