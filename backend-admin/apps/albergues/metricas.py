"""Métricas operativas del albergue autenticado."""
from django.db.models import Count, Exists, Min, OuterRef, Q
from rest_framework import permissions
from rest_framework.exceptions import NotFound, PermissionDenied
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento, User
from .models import Albergue


def _albergue_para_metricas(usuario, nombre):
    if getattr(usuario, "rol", None) != User.Rol.ALBERGUE:
        raise PermissionDenied(f"Solo el albergue puede consultar sus métricas de {nombre}.")

    try:
        return Albergue.objects.only("id").get(usuario=usuario)
    except Albergue.DoesNotExist as exc:
        raise NotFound("La cuenta no tiene un albergue asociado.") from exc


class PermanenciaAlbergueView(APIView):
    """Promedio de días entre ingreso y entrega para las mascotas del albergue."""

    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        albergue = _albergue_para_metricas(request.user, "permanencia")

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


class ConversionAlbergueView(APIView):
    """Solicitudes que alguna vez se aprobaron sobre todas las solicitudes recibidas (US-69)."""

    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        albergue = _albergue_para_metricas(request.user, "conversión")
        estados_aprobados = (SolicitudAdopcion.Estado.APROBADA, SolicitudAdopcion.Estado.ENTREGADA)

        # REGISTRO_HISTORICO conserva el estado de las solicitudes previas a la línea de tiempo.
        historial_aprobado = SolicitudEvento.objects.filter(solicitud_id=OuterRef("pk")).filter(
            Q(tipo=SolicitudEvento.Tipo.APROBADA)
            | Q(tipo=SolicitudEvento.Tipo.REGISTRO_HISTORICO, estado_nuevo__in=estados_aprobados)
        )
        # Una entrega requiere aprobación. El estado actual cubre solicitudes antiguas sin eventos.
        conteos = SolicitudAdopcion.objects.filter(albergue=albergue).aggregate(
            solicitudes_recibidas=Count("pk"),
            solicitudes_aprobadas=Count("pk", filter=Q(Exists(historial_aprobado)) | Q(estado__in=estados_aprobados)),
        )
        recibidas = conteos["solicitudes_recibidas"]
        aprobadas = conteos["solicitudes_aprobadas"]
        return Response({
            **conteos,
            "tasa_conversion": round(aprobadas * 100 / recibidas, 2) if recibidas else None,
        }, headers={"Cache-Control": "private, no-store"})
