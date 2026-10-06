"""Vistas de mascotas (US-11, US-12, US-13)."""
from rest_framework import viewsets, permissions, status, filters
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from django.shortcuts import get_object_or_404
from django.db.models import Q

from apps.albergues.models import Albergue
from .models import Mascota
from .serializers import (
    MascotaSerializer,
    MascotaListSerializer,
    MascotaCreateUpdateSerializer,
    MascotaEstadoSerializer,
)


class IsAlbergueOwnerOrReadOnly(permissions.BasePermission):
    """Permiso: solo el dueño del albergue puede modificar sus mascotas."""

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        return obj.albergue.usuario == request.user

    def has_permission(self, request, view):
        if request.method in permissions.SAFE_METHODS:
            return True
        # Para crear, verificar que el usuario tiene un albergue
        return hasattr(request.user, 'albergue')


class MascotaViewSet(viewsets.ModelViewSet):
    """CRUD de mascotas + endpoint cambio de estado."""

    queryset = Mascota.objects.select_related("albergue", "albergue__usuario", "creado_por").all()
    permission_classes = [permissions.IsAuthenticated, IsAlbergueOwnerOrReadOnly]
    parser_classes = [MultiPartParser, FormParser, JSONParser]
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ["nombre", "codigo", "raza", "descripcion"]
    ordering_fields = ["creado_en", "fecha_ingreso", "nombre", "estado"]
    ordering = ["-creado_en"]

    def get_serializer_class(self):
        if self.action == "list":
            return MascotaListSerializer
        if self.action in ["create", "update", "partial_update"]:
            return MascotaCreateUpdateSerializer
        if self.action == "cambiar_estado":
            return MascotaEstadoSerializer
        return MascotaSerializer

    def get_queryset(self):
        """Filtrar por albergue del usuario (excepto staff)."""
        user = self.request.user
        qs = self.queryset

        if user.is_staff:
            return qs

        # Usuario normal: solo sus mascotas
        try:
            albergue = user.albergue
            return qs.filter(albergue=albergue)
        except Albergue.DoesNotExist:
            return qs.none()

    def perform_create(self, serializer):
        """Asignar albergue del usuario y creador."""
        serializer.save(
            albergue=self.request.user.albergue,
            creado_por=self.request.user,
        )

    @action(detail=True, methods=["post"], url_path="cambiar-estado")
    def cambiar_estado(self, request, pk=None):
        """
        Cambia el estado de la mascota (US-13).
        
        Transiciones válidas:
        - DISPONIBLE -> EN_PROCESO
        - EN_PROCESO -> DISPONIBLE (si se cancela)
        - EN_PROCESO -> ADOPTADO
        - DISPONIBLE -> ADOPTADO (adopción directa)
        
        No se puede volver de ADOPTADO a otro estado.
        """
        mascota = self.get_object()
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        nuevo_estado = serializer.validated_data["estado"]
        actual = mascota.estado

        # Validar transiciones permitidas
        transiciones_validas = {
            Mascota.Estado.DISPONIBLE: [Mascota.Estado.EN_PROCESO, Mascota.Estado.ADOPTADO],
            Mascota.Estado.EN_PROCESO: [Mascota.Estado.DISPONIBLE, Mascota.Estado.ADOPTADO],
            Mascota.Estado.ADOPTADO: [],  # Estado terminal
        }

        if nuevo_estado not in transiciones_validas.get(actual, []):
            return Response(
                {
                    "error": f"Transición no permitida: {actual} -> {nuevo_estado}",
                    "transiciones_validas": transiciones_validas.get(actual, []),
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Actualizar estado
        mascota.estado = nuevo_estado
        if nuevo_estado == Mascota.Estado.ADOPTADO and not mascota.fecha_adopcion:
            from django.utils import timezone
            mascota.fecha_adopcion = timezone.now().date()
        mascota.save(update_fields=["estado", "fecha_adopcion", "actualizado_en"])

        return Response(MascotaSerializer(mascota, context={"request": request}).data)

    @action(detail=False, methods=["get"], url_path="mis-mascotas")
    def mis_mascotas(self, request):
        """Lista paginada de mascotas del albergue del usuario."""
        queryset = self.get_queryset()
        page = self.paginate_queryset(queryset)
        if page is not None:
            serializer = self.get_serializer(page, many=True)
            return self.get_paginated_response(serializer.data)
        serializer = self.get_serializer(queryset, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=["get"], url_path="estadisticas")
    def estadisticas(self, request):
        """Estadísticas rápidas para el dashboard."""
        queryset = self.get_queryset()
        stats = {
            "total": queryset.count(),
            "disponibles": queryset.filter(estado=Mascota.Estado.DISPONIBLE).count(),
            "en_proceso": queryset.filter(estado=Mascota.Estado.EN_PROCESO).count(),
            "adoptados": queryset.filter(estado=Mascota.Estado.ADOPTADO).count(),
            "por_especie": {
                "perros": queryset.filter(especie=Mascota.Especie.PERRO).count(),
                "gatos": queryset.filter(especie=Mascota.Especie.GATO).count(),
                "otros": queryset.filter(especie=Mascota.Especie.OTRO).count(),
            },
        }
        return Response(stats)