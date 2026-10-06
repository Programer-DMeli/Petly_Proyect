"""Vistas de acceso y postulantes (US-18, US-21)."""
from rest_framework import viewsets, permissions, status, filters, generics, throttling
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from rest_framework_simplejwt.tokens import RefreshToken
from django.shortcuts import get_object_or_404
from django.db.models import Q, Count
from django.utils import timezone
from django.contrib.auth import get_user_model
from django.core.mail import send_mail
from django.conf import settings
from django.core.cache import cache
import time

from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from .models import Postulante, SolicitudAdopcion, User, AdoptanteProfile
from .serializers import (
    PostulanteSerializer,
    PostulanteListSerializer,
    PostulanteCreateUpdateSerializer,
    SolicitudAdopcionSerializer,
    SolicitudAdopcionListSerializer,
    SolicitudEstadoSerializer,
    SolicitudCreateSerializer,
    CustomTokenObtainPairSerializer,
    UserRegistrationSerializer,
    UserSerializer,
    AdoptanteProfileSerializer,
    AdoptanteProfileCreateUpdateSerializer,
    PasswordResetRequestSerializer,
    PasswordResetConfirmSerializer,
)

User = get_user_model()


class IsAlbergueStaffOrReadOnly(permissions.BasePermission):
    """
    Permiso: staff del albergue puede ver/gestionar postulantes y solicitudes.
    Lectura permitida para todos autenticados.
    """

    def has_permission(self, request, view):
        return request.user.is_authenticated

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        # Solo staff del albergue puede modificar
        if hasattr(request.user, 'albergue'):
            if isinstance(obj, Postulante):
                return obj.solicitudes.filter(albergue=request.user.albergue).exists()
            if isinstance(obj, SolicitudAdopcion):
                return obj.albergue == request.user.albergue
        return request.user.is_staff


class PostulanteViewSet(viewsets.ModelViewSet):
    """CRUD de postulantes + historial de solicitudes."""

    queryset = Postulante.objects.select_related("creado_por").prefetch_related("solicitudes__mascota", "solicitudes__albergue").all()
    permission_classes = [permissions.IsAuthenticated, IsAlbergueStaffOrReadOnly]
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ["nombres", "apellidos", "documento_numero", "email", "telefono"]
    ordering_fields = ["creado_en", "nombres", "apellidos"]
    ordering = ["-creado_en"]

    def get_serializer_class(self):
        if self.action == "list":
            return PostulanteListSerializer
        if self.action in ["create", "update", "partial_update"]:
            return PostulanteCreateUpdateSerializer
        return PostulanteSerializer

    def get_queryset(self):
        """Filtrar postulantes que tienen solicitudes en el albergue del usuario."""
        user = self.request.user
        qs = self.queryset

        if user.is_staff:
            return qs

        try:
            albergue = user.albergue
            # Postulantes que han solicitado mascotas de este albergue
            return qs.filter(solicitudes__albergue=albergue).distinct()
        except Albergue.DoesNotExist:
            return qs.none()

    def perform_create(self, serializer):
        serializer.save(creado_por=self.request.user)

    @action(detail=True, methods=["get"], url_path="historial")
    def historial(self, request, pk=None):
        """Historial completo de solicitudes del postulante."""
        postulante = self.get_object()
        solicitudes = postulante.solicitudes.select_related("mascota", "albergue", "revisado_por").all()
        
        # Agrupar por estado
        resumen = {
            "total": solicitudes.count(),
            "pendientes": solicitudes.filter(estado=SolicitudAdopcion.Estado.PENDIENTE).count(),
            "en_revision": solicitudes.filter(estado=SolicitudAdopcion.Estado.EN_REVISION).count(),
            "aprobadas": solicitudes.filter(estado=SolicitudAdopcion.Estado.APROBADA).count(),
            "rechazadas": solicitudes.filter(estado=SolicitudAdopcion.Estado.RECHAZADA).count(),
            "canceladas": solicitudes.filter(estado=SolicitudAdopcion.Estado.CANCELADA).count(),
            "entregadas": solicitudes.filter(estado=SolicitudAdopcion.Estado.ENTREGADA).count(),
        }
        
        serializer = SolicitudAdopcionListSerializer(solicitudes, many=True)
        return Response({
            "postulante": PostulanteSerializer(postulante).data,
            "resumen": resumen,
            "solicitudes": serializer.data,
        })

    @action(detail=True, methods=["get"], url_path="solicitudes")
    def solicitudes_list(self, request, pk=None):
        """Lista paginada de solicitudes del postulante."""
        postulante = self.get_object()
        solicitudes = postulante.solicitudes.select_related("mascota", "albergue").all()
        
        page = self.paginate_queryset(solicitudes)
        if page is not None:
            serializer = SolicitudAdopcionListSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)
        
        serializer = SolicitudAdopcionListSerializer(solicitudes, many=True)
        return Response(serializer.data)


class SolicitudAdopcionViewSet(viewsets.ModelViewSet):
    """CRUD de solicitudes de adopción + cambio de estado."""

    queryset = SolicitudAdopcion.objects.select_related(
        "postulante", "mascota", "albergue", "revisado_por"
    ).all()
    permission_classes = [permissions.IsAuthenticated, IsAlbergueStaffOrReadOnly]
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ["postulante__nombres", "postulante__apellidos", "postulante__documento_numero", "mascota__nombre"]
    ordering_fields = ["fecha_solicitud", "estado", "fecha_revision"]
    ordering = ["-fecha_solicitud"]

    def get_serializer_class(self):
        if self.action == "list":
            return SolicitudAdopcionListSerializer
        if self.action == "cambiar_estado":
            return SolicitudEstadoSerializer
        if self.action == "create":
            return SolicitudCreateSerializer
        return SolicitudAdopcionSerializer

    def get_queryset(self):
        user = self.request.user
        qs = self.queryset

        if user.is_staff:
            return qs

        try:
            albergue = user.albergue
            return qs.filter(albergue=albergue)
        except Albergue.DoesNotExist:
            return qs.none()

    @action(detail=True, methods=["post"], url_path="cambiar-estado")
    def cambiar_estado(self, request, pk=None):
        """
        Cambia el estado de la solicitud.
        
        Transiciones válidas:
        - PENDIENTE -> EN_REVISION, RECHAZADA, CANCELADA
        - EN_REVISION -> APROBADA, RECHAZADA, PENDIENTE
        - APROBADA -> ENTREGADA, RECHAZADA (si hay problema)
        - RECHAZADA -> (terminal)
        - CANCELADA -> (terminal)
        - ENTREGADA -> (terminal)
        """
        solicitud = self.get_object()
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        nuevo_estado = serializer.validated_data["estado"]
        actual = solicitud.estado
        observaciones = serializer.validated_data.get("observaciones_revision", "")

        # Validar transiciones permitidas
        transiciones_validas = {
            SolicitudAdopcion.Estado.PENDIENTE: [
                SolicitudAdopcion.Estado.EN_REVISION,
                SolicitudAdopcion.Estado.RECHAZADA,
                SolicitudAdopcion.Estado.CANCELADA,
            ],
            SolicitudAdopcion.Estado.EN_REVISION: [
                SolicitudAdopcion.Estado.APROBADA,
                SolicitudAdopcion.Estado.RECHAZADA,
                SolicitudAdopcion.Estado.PENDIENTE,
            ],
            SolicitudAdopcion.Estado.APROBADA: [
                SolicitudAdopcion.Estado.ENTREGADA,
                SolicitudAdopcion.Estado.RECHAZADA,
            ],
            SolicitudAdopcion.Estado.RECHAZADA: [],
            SolicitudAdopcion.Estado.CANCELADA: [],
            SolicitudAdopcion.Estado.ENTREGADA: [],
        }

        if nuevo_estado not in transiciones_validas.get(actual, []):
            return Response(
                {
                    "error": f"Transición no permitida: {actual} -> {nuevo_estado}",
                    "transiciones_validas": transiciones_validas.get(actual, []),
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Actualizar
        solicitud.estado = nuevo_estado
        solicitud.revisado_por = request.user
        solicitud.fecha_revision = timezone.now()
        if observaciones:
            solicitud.observaciones_revision = observaciones
        
        # Si se entrega, actualizar mascota y fecha entrega
        if nuevo_estado == SolicitudAdopcion.Estado.ENTREGADA:
            solicitud.fecha_entrega = timezone.now().date()
            # Cambiar estado de mascota a ADOPTADO
            mascota = solicitud.mascota
            if mascota.estado != Mascota.Estado.ADOPTADO:
                mascota.estado = Mascota.Estado.ADOPTADO
                mascota.fecha_adopcion = timezone.now().date()
                mascota.save(update_fields=["estado", "fecha_adopcion", "actualizado_en"])
        
        solicitud.save(update_fields=[
            "estado", "revisado_por", "fecha_revision",
            "observaciones_revision", "fecha_entrega", "actualizado_en"
        ])

        return Response(SolicitudAdopcionSerializer(solicitud, context={"request": request}).data)

    @action(detail=False, methods=["get"], url_path="estadisticas")
    def estadisticas(self, request):
        """Estadísticas de solicitudes para dashboard."""
        qs = self.get_queryset()
        stats = {
            "total": qs.count(),
            "pendientes": qs.filter(estado=SolicitudAdopcion.Estado.PENDIENTE).count(),
            "en_revision": qs.filter(estado=SolicitudAdopcion.Estado.EN_REVISION).count(),
            "aprobadas": qs.filter(estado=SolicitudAdopcion.Estado.APROBADA).count(),
            "rechazadas": qs.filter(estado=SolicitudAdopcion.Estado.RECHAZADA).count(),
            "canceladas": qs.filter(estado=SolicitudAdopcion.Estado.CANCELADA).count(),
            "entregadas": qs.filter(estado=SolicitudAdopcion.Estado.ENTREGADA).count(),
            "este_mes": qs.filter(fecha_solicitud__month=timezone.now().month).count(),
        }
        return Response(stats)

    @action(detail=False, methods=["get"], url_path="mis-solicitudes")
    def mis_solicitudes(self, request):
        """Solicitudes del albergue del usuario (alias para list)."""
        return self.list(request)


class AuthThrottle(throttling.BaseThrottle):
    """Throttle para auth: 2 intentos por 5 minutos (US-21)."""
    
    def allow_request(self, request, view):
        ident = self.get_ident(request)
        key = f"auth_throttle_{ident}"
        
        # Obtener historial de intentos
        history = cache.get(key, [])
        now = time.time()
        
        # Filtrar intentos de los últimos 5 minutos (300 segundos)
        history = [t for t in history if now - t < 300]
        
        if len(history) >= 2:
            return False
        
        # Agregar intento actual
        history.append(now)
        cache.set(key, history, 300)  # 5 minutos TTL
        return True
    
    def wait(self):
        return 300  # 5 minutos


class CustomTokenObtainPairView(TokenObtainPairView):
    """Login con JWT + info de usuario y rol (US-21)."""
    serializer_class = CustomTokenObtainPairSerializer
    throttle_classes = [AuthThrottle]


class CustomTokenRefreshView(TokenRefreshView):
    """Refresh token con rotación (US-21)."""
    throttle_classes = [AuthThrottle]


class LogoutView(APIView):
    """Logout: blacklist refresh token (US-21)."""
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        try:
            refresh_token = request.data.get("refresh")
            if refresh_token:
                token = RefreshToken(refresh_token)
                token.blacklist()
            return Response({"detail": "Sesión cerrada correctamente."})
        except Exception:
            return Response({"detail": "Token inválido."}, status=status.HTTP_400_BAD_REQUEST)


class UserRegistrationView(generics.CreateAPIView):
    """Registro de usuario (adoptante o albergue) (US-21)."""
    serializer_class = UserRegistrationSerializer
    permission_classes = [permissions.AllowAny]
    throttle_classes = [AuthThrottle]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        # Si es adoptante, crear perfil vacío
        if user.rol == User.Rol.ADOPTANTE:
            AdoptanteProfile.objects.create(user=user)

        # Generar tokens
        refresh = RefreshToken.for_user(user)
        return Response({
            "user": UserSerializer(user).data,
            "access": str(refresh.access_token),
            "refresh": str(refresh),
        }, status=status.HTTP_201_CREATED)


class UserProfileView(generics.RetrieveUpdateAPIView):
    """Perfil del usuario autenticado (US-21)."""
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user


class AdoptanteProfileView(generics.RetrieveUpdateAPIView):
    """Perfil extendido del adoptante para móvil (US-21, US-16, US-17)."""
    serializer_class = AdoptanteProfileSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_serializer_class(self):
        if self.request.method in ["PUT", "PATCH"]:
            return AdoptanteProfileCreateUpdateSerializer
        return AdoptanteProfileSerializer

    def get_object(self):
        profile, created = AdoptanteProfile.objects.get_or_create(user=self.request.user)
        return profile

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        return Response(AdoptanteProfileSerializer(instance).data)


class PasswordResetRequestView(APIView):
    """Solicitud de recuperación de contraseña (US-24)."""
    permission_classes = [permissions.AllowAny]
    throttle_classes = [AuthThrottle]

    def post(self, request):
        serializer = PasswordResetRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.validated_data["email"]
        user = User.objects.get(email=email)

        # Generar token de reset (usando JWT con claim personalizado)
        refresh = RefreshToken.for_user(user)
        refresh["type"] = "password_reset"
        refresh.set_exp(lifetime=timezone.timedelta(minutes=15))  # US-24: 15 min

        reset_token = str(refresh.access_token)

        # Enviar email (configurar EMAIL_BACKEND en producción)
        reset_url = f"{settings.FRONTEND_URL}/recuperacion?token={reset_token}"
        send_mail(
            subject="Recuperación de contraseña - Petly",
            message=f"Hola {user.username},\n\n"
                    f"Para restablecer tu contraseña, haz clic en el siguiente enlace:\n"
                    f"{reset_url}\n\n"
                    f"El enlace expira en 15 minutos.\n\n"
                    f"Si no solicitaste esto, ignora este correo.\n\n"
                    f"Equipo Petly",
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[email],
            fail_silently=True,  # No fallar si email no configurado
        )

        return Response({"detail": "Si el email existe, se enviaron instrucciones."})


class PasswordResetConfirmView(APIView):
    """Confirmación de recuperación de contraseña (US-24)."""
    permission_classes = [permissions.AllowAny]
    throttle_classes = [AuthThrottle]

    def post(self, request):
        serializer = PasswordResetConfirmSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        token_str = serializer.validated_data["token"]
        new_password = serializer.validated_data["new_password"]

        try:
            token = RefreshToken(token_str)
            if token.get("type") != "password_reset":
                return Response({"detail": "Token inválido."}, status=status.HTTP_400_BAD_REQUEST)

            user_id = token["user_id"]
            user = User.objects.get(id=user_id)
            user.set_password(new_password)
            user.save()

            # Invalidar token (blacklist)
            token.blacklist()

            return Response({"detail": "Contraseña actualizada correctamente."})
        except Exception:
            return Response({"detail": "Token inválido o expirado."}, status=status.HTTP_400_BAD_REQUEST)