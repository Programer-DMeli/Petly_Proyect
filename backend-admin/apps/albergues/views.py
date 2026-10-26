"""Vistas de albergues (US-46, US-18)."""
from rest_framework import viewsets, permissions, status, filters
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError
from django.shortcuts import get_object_or_404
from django.db import IntegrityError, transaction, models
from rest_framework.exceptions import APIException
from apps.acceso.authentication import spring_get, AuthorityUnavailable
import urllib.error

from .models import Albergue, Infraestructura
from .serializers import (
    AlbergueSerializer,
    AlbergueCreateUpdateSerializer,
    InfraestructuraSerializer,
    InfraestructuraCreateUpdateSerializer,
)


class IsShelterOwnerOrAdmin(permissions.BasePermission):
    """ADMIN gestiona albergues; ALBERGUE solo modifica su institución."""

    def has_permission(self, request, view):
        if not request.user.is_authenticated:
            return False
        if request.method in permissions.SAFE_METHODS:
            return request.user.rol in {"ALBERGUE", "ADMIN"}
        return request.user.rol in {"ALBERGUE", "ADMIN"}

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        return request.user.rol == "ADMIN" or obj.usuario == request.user


class AlbergueViewSet(viewsets.ModelViewSet):
    """CRUD de perfil del albergue."""

    queryset = Albergue.objects.select_related("usuario").prefetch_related("infraestructura").all()
    permission_classes = [permissions.IsAuthenticated, IsShelterOwnerOrAdmin]
    filter_backends = [filters.SearchFilter]
    search_fields = ["nombre", "direccion", "responsable_nombre"]

    def get_serializer_class(self):
        if self.action in ["create", "update", "partial_update"]:
            return AlbergueCreateUpdateSerializer
        return AlbergueSerializer

    def get_queryset(self):
        """ADMIN consulta todos; ALBERGUE consulta solo su institución."""
        user = self.request.user
        if user.rol == "ADMIN":
            return self.queryset
        return self.queryset.filter(usuario=user)

    def perform_create(self, serializer):
        """Asigna el usuario actual como dueño al crear."""
        if self.request.user.rol == 'ADMIN': serializer.save()
        else: serializer.save(usuario=self.request.user)

    @transaction.atomic
    def create(self, request, *args, **kwargs):
        """Override create para retornar serializer de lectura y manejar duplicados."""
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        try:
            self.perform_create(serializer)
        except IntegrityError as e:
            if 'usuario' in str(e).lower():
                raise ValidationError({'usuario': 'Ya tienes un albergue registrado.'})
            raise
        read_serializer = AlbergueSerializer(serializer.instance, context={'request': request})
        headers = self.get_success_headers(read_serializer.data)
        return Response(read_serializer.data, status=status.HTTP_201_CREATED, headers=headers)

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        instance._prefetched_objects_cache = {}
        return Response(AlbergueSerializer(instance, context={"request": request}).data)

    @transaction.atomic
    def destroy(self, request, *args, **kwargs):
        albergue = self.get_object()
        albergue = Albergue.objects.select_for_update().get(pk=albergue.pk)
        if albergue.mascotas.exists():
            error = APIException('Este albergue tiene mascotas. Desactívalo o gestiona sus mascotas antes de eliminarlo.')
            error.status_code = 409
            raise error
        albergue.delete()
        return Response(status=204)

    @action(detail=False, methods=['get'], url_path='cuentas-disponibles')
    def cuentas_disponibles(self, request):
        if request.user.rol != 'ADMIN': return Response({'error':'acceso denegado','campos':{}}, status=403)
        try:
            identities = spring_get('/api/admin/cuentas-albergue', request.headers.get('Authorization'))
        except urllib.error.HTTPError:
            raise AuthorityUnavailable()
        from django.contrib.auth import get_user_model
        technical = set(get_user_model().objects.filter(models.Q(is_staff=True)|models.Q(is_superuser=True)).values_list('spring_id',flat=True))
        assigned = set(Albergue.objects.values_list('usuario__spring_id',flat=True))
        return Response([dict(u, asignada=u['id'] in assigned) for u in identities if u['id'] not in technical])

    @action(detail=True, methods=["get", "post"], url_path="infraestructura")
    def infraestructura_list_create(self, request, pk=None):
        """Lista o crea infraestructura del albergue."""
        albergue = self.get_object()

        if request.method == "GET":
            infra = albergue.infraestructura.all()
            serializer = InfraestructuraSerializer(infra, many=True)
            return Response(serializer.data)

        # POST
        serializer = InfraestructuraCreateUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save(albergue=albergue)
        return Response(InfraestructuraSerializer(serializer.instance).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["get", "put", "patch", "delete"], url_path="infraestructura/(?P<infra_id>[^/.]+)")
    def infraestructura_detail(self, request, pk=None, infra_id=None):
        """Detalle, actualiza o elimina infraestructura específica."""
        albergue = self.get_object()
        infra = get_object_or_404(Infraestructura, pk=infra_id, albergue=albergue)

        if request.method == "GET":
            serializer = InfraestructuraSerializer(infra)
            return Response(serializer.data)

        if request.method in ["PUT", "PATCH"]:
            partial = request.method == "PATCH"
            serializer = InfraestructuraCreateUpdateSerializer(infra, data=request.data, partial=partial)
            serializer.is_valid(raise_exception=True)
            serializer.save()
            return Response(InfraestructuraSerializer(serializer.instance).data)

        # DELETE
        infra.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

    @action(detail=False, methods=["get"], url_path="mi-perfil")
    def mi_perfil(self, request):
        """Devuelve el perfil del albergue del usuario autenticado."""
        albergue = Albergue.objects.filter(usuario=request.user).first()
        if albergue is None:
            return Response(
                {"detail": "Aún no has registrado tu albergue. Regístralo para poder administrar tus mascotas."},
                status=status.HTTP_404_NOT_FOUND,
            )
        serializer = self.get_serializer(albergue)
        return Response(serializer.data)
