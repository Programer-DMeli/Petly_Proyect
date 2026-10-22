"""Vistas de mascotas (US-11, US-12, US-13)."""
from rest_framework import viewsets, permissions, status, filters
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from django.shortcuts import get_object_or_404
from django.db.models import Q
from django_filters.rest_framework import DjangoFilterBackend

from apps.albergues.models import Albergue
from .models import Mascota
from .serializers import (
    MascotaSerializer,
    MascotaListSerializer,
    MascotaCompatibilidadSerializer,
    MascotaCreateUpdateSerializer,
    MascotaEstadoSerializer,
    MascotaFilter,
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
        return request.user.rol == 'ALBERGUE' and hasattr(request.user, 'albergue')


class MascotaViewSet(viewsets.ModelViewSet):
    """CRUD de mascotas + endpoint cambio de estado + filtros (US-12)."""

    queryset = Mascota.objects.select_related("albergue", "albergue__usuario", "creado_por").all()
    permission_classes = [permissions.IsAuthenticated, IsAlbergueOwnerOrReadOnly]
    parser_classes = [MultiPartParser, FormParser, JSONParser]
    filter_backends = [MascotaFilter, filters.SearchFilter, filters.OrderingFilter]
    search_fields = ["nombre", "codigo", "raza", "descripcion"]
    ordering_fields = ["creado_en", "fecha_ingreso", "nombre", "estado", "especie", "tamanio", "temperamento", "edad_meses"]
    ordering = ["-creado_en"]

    def get_serializer_class(self):
        if self.action == "compatibilidad":
            return MascotaCompatibilidadSerializer
        if self.action == "list":
            return MascotaListSerializer
        if self.action in ["create", "update", "partial_update"]:
            return MascotaCreateUpdateSerializer
        if self.action == "cambiar_estado":
            return MascotaEstadoSerializer
        return MascotaSerializer

    @action(detail=False, methods=["get"], url_path="compatibilidad")
    def compatibilidad(self, request):
        """Expone atributos de mascotas disponibles para el servicio Spring; no calcula puntajes."""
        queryset = Mascota.objects.filter(
            estado=Mascota.Estado.DISPONIBLE, albergue__activo=True
        ).order_by("id")
        page = self.paginate_queryset(queryset)
        if page is not None:
            return self.get_paginated_response(self.get_serializer(page, many=True).data)
        return Response(self.get_serializer(queryset, many=True).data)

    @action(detail=True, methods=["post"], url_path="generar-descripcion")
    def generar_descripcion(self, request, pk=None):
        """Return an editable AI draft; this endpoint never changes the saved description."""
        mascota = self.get_object()
        from .descripcion_ia import DescripcionIAError, ProveedorDescripcionNoConfigurado, generar_borrador
        try:
            draft = generar_borrador(mascota)
        except ProveedorDescripcionNoConfigurado as error:
            return Response({"detail": str(error)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
        except DescripcionIAError as error:
            return Response({"detail": str(error)}, status=status.HTTP_502_BAD_GATEWAY)
        return Response({"borrador": draft, "guardado": False})

    def get_queryset(self):
        """Filtrar por albergue del usuario (excepto staff)."""
        user = self.request.user
        qs = self.queryset

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
            codigo=__import__("uuid").uuid4().hex[:20].upper(),
        )

    def create(self, request, *args, **kwargs):
        """Override create para retornar serializer de lectura."""
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        self.perform_create(serializer)
        read_serializer = MascotaSerializer(serializer.instance, context={'request': request})
        headers = self.get_success_headers(read_serializer.data)
        return Response(read_serializer.data, status=status.HTTP_201_CREATED, headers=headers)

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        instance._prefetched_objects_cache = {}
        return Response(MascotaSerializer(instance, context={"request": request}).data)

    @action(detail=True, methods=["post"], url_path="cambiar-estado")
    def cambiar_estado(self, request, pk=None):
        """
        Cambia el estado de la mascota (US-13).
        
        Transiciones válidas:
        - DISPONIBLE -> EN_PROCESO
        - EN_PROCESO -> DISPONIBLE (si se cancela)
        - EN_PROCESO -> ADOPTADO
        - DISPONIBLE -> ADOPTADO (adopción directa)
        
        Se permite corregir manualmente ADOPTADO; al salir se limpia fecha_adopcion.
        """
        mascota = self.get_object()
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        nuevo_estado = serializer.validated_data["estado"]

        if nuevo_estado == Mascota.Estado.DISPONIBLE and mascota.solicitudes.filter(
            estado__in=["APROBADA", "ENTREGADA"]
        ).exists():
            return Response(
                {"estado": "No se puede liberar una mascota con una solicitud aprobada activa."},
                status=status.HTTP_409_CONFLICT,
            )

        # Sprint 1: corrección manual entre cualquiera de los tres estados.
        # No crea solicitudes ni expedientes de adopción.
        mascota.estado = nuevo_estado
        if nuevo_estado == Mascota.Estado.ADOPTADO:
            if not mascota.fecha_adopcion:
                from django.utils import timezone
                mascota.fecha_adopcion = timezone.now().date()
        else:
            mascota.fecha_adopcion = None
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
