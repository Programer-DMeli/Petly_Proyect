"""Métricas operativas del albergue autenticado."""
from django.db.models import Min
from rest_framework import permissions
from rest_framework.exceptions import NotFound, PermissionDenied
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion
from .models import Albergue


class PermanenciaAlbergueView(APIView):
    """Promedio de días entre ingreso y entrega para las mascotas del albergue."""

    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        if getattr(request.user, "rol", None) != "ALBERGUE":
            raise PermissionDenied("Solo el albergue puede consultar sus métricas de permanencia.")

        try:
            albergue = Albergue.objects.only("id").get(usuario=request.user)
        except Albergue.DoesNotExist as exc:
            raise NotFound("La cuenta no tiene un albergue asociado.") from exc

        # Agrupar por mascota evita duplicar su peso si existen varias solicitudes
        # entregadas históricas. La primera entrega válida define el cierre del ciclo.
        entregas_por_mascota = (
            SolicitudAdopcion.objects.filter(
                albergue=albergue,
                mascota__albergue=albergue,
                estado=SolicitudAdopcion.Estado.ENTREGADA,
                fecha_entrega__isnull=False,
                mascota__fecha_ingreso__isnull=False,
            )
            .values("mascota_id")
            .annotate(
                fecha_entrega=Min("fecha_entrega"),
                fecha_ingreso=Min("mascota__fecha_ingreso"),
            )
        )
        dias_acumulados = 0
        mascotas_entregadas = 0
        for entrega in entregas_por_mascota.iterator(chunk_size=500):
            dias = (entrega["fecha_entrega"] - entrega["fecha_ingreso"]).days
            if dias < 0:
                continue
            dias_acumulados += dias
            mascotas_entregadas += 1

        return Response({
            "dias_promedio": round(dias_acumulados / mascotas_entregadas, 1) if mascotas_entregadas else None,
            "mascotas_entregadas": mascotas_entregadas,
        })
