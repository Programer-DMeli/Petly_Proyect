"""Vistas de albergues (US-46, US-18)."""
from rest_framework import viewsets, permissions, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError
from django.shortcuts import get_object_or_404
from django.db import IntegrityError

from .models import Albergue, Infraestructura
from .serializers import (
    AlbergueSerializer,
    AlbergueCreateUpdateSerializer,
    InfraestructuraSerializer,
    InfraestructuraCreateUpdateSerializer,
)


class IsOwnerOrReadOnly(permissions.BasePermission):
    """Permiso: solo el usuario dueño del albergue puede modificar."""

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        return obj.usuario == request.user


class AlbergueViewSet(viewsets.ModelViewSet):
    """CRUD de perfil del albergue."""

    queryset = Albergue.objects.select_related("usuario").prefetch_related("infraestructura").all()
    permission_classes = [permissions.IsAuthenticated, IsOwnerOrReadOnly]

    def get_serializer_class(self):
        if self.action in ["create", "update", "partial_update"]:
            return AlbergueCreateUpdateSerializer
        return AlbergueSerializer

    def get_queryset(self):
        """Cada usuario ve solo su albergue (excepto staff)."""
        user = self.request.user
        if user.is_staff:
            return self.queryset
        return self.queryset.filter(usuario=user)

    def perform_create(self, serializer):
        """Asigna el usuario actual como dueño al crear."""
        serializer.save(usuario=self.request.user)

    def create(self, request, *args, **kwargs):
        """Override create para retornar serializer de lectura y manejar duplicados."""
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        try:
            self.perform_create(serializer)
        except IntegrityError as e:
            if 'usuario_id' in str(e):
                raise ValidationError({'usuario': 'Ya tienes un albergue registrado.'})
            raise
        read_serializer = AlbergueSerializer(serializer.instance, context={'request': request})
        headers = self.get_success_headers(read_serializer.data)
        return Response(read_serializer.data, status=status.HTTP_201_CREATED, headers=headers)

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
        albergue = get_object_or_404(Albergue, usuario=request.user)
        serializer = self.get_serializer(albergue)
        return Response(serializer.data)