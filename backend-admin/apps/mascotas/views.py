"""Vistas de mascotas (US-11, US-12, US-13)."""
import hashlib
import json
import secrets

from django.conf import settings
from django.utils.http import http_date
from rest_framework import viewsets, permissions, status, filters
from rest_framework.exceptions import ValidationError
from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from django.shortcuts import get_object_or_404
from django.db.models import Q
from django_filters.rest_framework import DjangoFilterBackend

from apps.albergues.models import Albergue
from .models import Mascota
from .estados import cambiar_estado_mascota
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
        """Devuelve mascotas disponibles ordenadas por compatibilidad del adoptante."""
        from .compatibilidad import puntuar_compatibilidad
        if request.user.rol != "ADOPTANTE":
            return Response({"detail": "Este orden requiere una cuenta de adoptante."}, status=status.HTTP_403_FORBIDDEN)
        perfil = getattr(request.user, "adoptante_profile", None)
        if perfil is None:
            return Response({"detail": "Completa tu perfil de adopción para calcular compatibilidad."}, status=status.HTTP_400_BAD_REQUEST)
        mascotas = list(Mascota.objects.filter(
            estado=Mascota.Estado.DISPONIBLE, albergue__activo=True
        ).select_related("albergue").order_by("id"))
        resultado = []
        for mascota in mascotas:
            puntaje = puntuar_compatibilidad(perfil, mascota)
            datos = MascotaCompatibilidadSerializer(mascota, context={"request": request}).data
            datos["compatibilidad"] = puntaje
            resultado.append(datos)
        resultado.sort(key=lambda item: (item["compatibilidad"]["puntaje"] is None,
                                         -(item["compatibilidad"]["puntaje"] or 0), item["id"]))
        return Response(resultado)

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

        try:
            mascota = cambiar_estado_mascota(mascota.pk, nuevo_estado)
        except DjangoValidationError as exc:
            raise ValidationError({"estado": exc.messages})

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


class DatosCompatibilidadView(APIView):
    """Feed interno de mascotas vigentes usado por el motor de compatibilidad (US-03)."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []

    def get(self, request):
        clave_configurada = getattr(settings, "PET_COMPATIBILITY_SERVICE_TOKEN", "")
        clave_recibida = request.headers.get("X-Service-Token", "")
        if not clave_configurada:
            return Response({"detail": "La integración de compatibilidad no está configurada."}, status=503)
        if not secrets.compare_digest(clave_recibida, clave_configurada):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=401)

        mascotas = Mascota.objects.filter(
            estado=Mascota.Estado.DISPONIBLE,
            albergue__activo=True,
        ).order_by("id")
        items = MascotaCompatibilidadSerializer(mascotas, many=True).data
        contenido = json.dumps(items, sort_keys=True, separators=(",", ":"), default=str)
        revision = hashlib.sha256(contenido.encode("utf-8")).hexdigest()
        etag = f'"{revision}"'
        headers = {"ETag": etag, "Cache-Control": "private, no-cache"}
        ultima_actualizacion = mascotas.order_by("-actualizado_en").values_list("actualizado_en", flat=True).first()
        if ultima_actualizacion:
            headers["Last-Modified"] = http_date(ultima_actualizacion.timestamp())
        etags_cliente = {valor.strip() for valor in request.headers.get("If-None-Match", "").split(",")}
        if etag in etags_cliente or "*" in etags_cliente:
            return Response(status=304, headers=headers)
        return Response({"catalogVersion": revision, "items": items}, headers=headers)
