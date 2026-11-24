"""Tasa de adopción y permanencia por cohorte de ingreso (US-70)."""
from dataclasses import dataclass
from fractions import Fraction

from django.db.models import OuterRef, Subquery
from django.utils import timezone
from rest_framework import permissions, serializers
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.mascotas.models import Mascota
from .analitica import FiltroRangoAnaliticaSerializer, entregas_adopcion
from .models import User


class FiltroRankingAdopcionesSerializer(FiltroRangoAnaliticaSerializer):
    agrupacion = serializers.ChoiceField(choices=("especie", "raza"), default="especie")


class EsAdministradorRanking(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.rol == User.Rol.ADMIN


@dataclass
class ConteoAdopciones:
    ingresadas: int = 0
    entregadas: int = 0
    dias_acumulados: int = 0

    def contar(self, dias_permanencia):
        self.ingresadas += 1
        if dias_permanencia is not None:
            self.entregadas += 1
            self.dias_acumulados += dias_permanencia

    def tasa(self):
        return Fraction(self.entregadas, self.ingresadas)

    def resumen(self):
        return {
            "mascotas_ingresadas": self.ingresadas,
            "mascotas_entregadas": self.entregadas,
            "tasa_adopcion": round(float(self.tasa() * 100), 2) if self.ingresadas else None,
            "dias_promedio": round(self.dias_acumulados / self.entregadas, 1) if self.entregadas else None,
        }


def ranking_adopciones(*, agrupacion, desde, hasta):
    primera_entrega = entregas_adopcion(hasta=hasta).filter(
        mascota_id=OuterRef("pk"), fecha_entrega__gte=OuterRef("fecha_ingreso"),
    ).order_by("fecha_entrega").values("fecha_entrega")[:1]
    mascotas = Mascota.objects.filter(fecha_ingreso__lte=hasta)
    if desde is not None:
        mascotas = mascotas.filter(fecha_ingreso__gte=desde)
    filas = mascotas.order_by("pk").annotate(primera_entrega=Subquery(primera_entrega)).values_list(
        "especie", "raza", "fecha_ingreso", "primera_entrega",
    )

    totales = ConteoAdopciones()
    grupos = {}
    nombres_especies = dict(Mascota.Especie.choices)
    for especie, raza, ingreso, entrega in filas.iterator(chunk_size=500):
        raza = " ".join(raza.split()) if agrupacion == "raza" else ""
        clave = (especie, raza.casefold())
        if clave not in grupos:
            grupos[clave] = (raza or None, ConteoAdopciones())
        dias = (entrega - ingreso).days if entrega is not None else None
        grupos[clave][1].contar(dias)
        totales.contar(dias)

    ordenados = sorted(grupos.items(), key=lambda grupo: (grupo[1][1].tasa(), *grupo[0]))
    return {
        "agrupacion": agrupacion,
        "desde": desde.isoformat() if desde else None,
        "hasta": hasta.isoformat(),
        "totales": totales.resumen(),
        "grupos": [
            {"especie": especie, "nombre_especie": nombres_especies.get(especie, especie),
             "raza": raza, **conteo.resumen()}
            for (especie, _), (raza, conteo) in ordenados
        ],
    }


class RankingAdopcionesView(APIView):
    permission_classes = [permissions.IsAuthenticated, EsAdministradorRanking]

    def get(self, request):
        filtros = FiltroRankingAdopcionesSerializer(data=request.query_params)
        filtros.is_valid(raise_exception=True)
        rango = filtros.validated_data
        return Response(ranking_adopciones(
            agrupacion=rango["agrupacion"], desde=rango.get("desde"),
            hasta=rango.get("hasta") or timezone.localdate(),
        ))
