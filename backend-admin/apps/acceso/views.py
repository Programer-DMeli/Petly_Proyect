"""Vistas de acceso y postulantes (US-18, US-21)."""
from rest_framework import viewsets, permissions, status, filters, generics, throttling
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from rest_framework_simplejwt.tokens import RefreshToken
from django.shortcuts import get_object_or_404
from django.http import FileResponse
from django.db.models import Q, Count
from django.db import transaction
from rest_framework.exceptions import PermissionDenied, ValidationError
from django.utils import timezone
from django.contrib.auth import get_user_model
from django.core.mail import send_mail
from django.conf import settings
from django.core.cache import cache
import time

from apps.albergues.models import Albergue
from apps.mascotas.ficha_veterinaria import generar_ficha_veterinaria
from apps.mascotas.models import Mascota
from .models import Postulante, SolicitudAdopcion, SolicitudEvento, AvisoExterno, User, AdoptanteProfile, Autorizacion
from .solicitudes import cambiar_estado_solicitud
from .serializers import (
    PostulanteSerializer,
    PostulanteListSerializer,
    PostulanteCreateUpdateSerializer,
    SolicitudAdopcionSerializer,
    SolicitudAdopcionListSerializer,
    SolicitudAdopcionResumenSerializer,
    SolicitudEstadoSerializer,
    SolicitudEventoSerializer,
    SolicitudCreateSerializer,
    RevisionVeterinariaSolicitudSerializer,
    ProgramarRecojoSerializer,
    ConfirmarEntregaSerializer,
    CustomTokenObtainPairSerializer,
    UserRegistrationSerializer,
    UserSerializer,
    AdoptanteProfileSerializer,
    AdoptanteProfileCreateUpdateSerializer,
    PasswordResetRequestSerializer,
    PasswordResetConfirmSerializer,
    AutorizacionSerializer,
    AutorizacionCreateSerializer,
    AutorizacionRevokeSerializer,
)

class RetiredEndpointView(APIView):
    """Endpoint retirado (fuera de Sprint 1) — retorna 410 Gone."""
    def dispatch(self, request, *args, **kwargs):
        return Response({"detail": "Endpoint retirado del alcance actual."}, status=status.HTTP_410_GONE)

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


class IsAdoptante(permissions.BasePermission):
    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.rol == User.Rol.ADOPTANTE)


class SolicitudAdopcionPermission(IsAlbergueStaffOrReadOnly):
    """El adoptante puede crear; el albergue/staff conserva permisos de gestión."""

    def has_permission(self, request, view):
        if not request.user.is_authenticated:
            return False
        action = getattr(view, "action", None)
        if action == "create":
            return request.user.rol == User.Rol.ADOPTANTE
        if action == "cancelar":
            return request.user.rol == User.Rol.ADOPTANTE
        if action == "cambiar_estado":
            return request.user.is_staff or (
                request.user.rol == User.Rol.ALBERGUE and hasattr(request.user, "albergue")
            )
        if action in {"revisar_veterinaria", "programar_recojo", "confirmar_entrega"}:
            return request.user.is_staff or (
                request.user.rol == User.Rol.ALBERGUE and hasattr(request.user, "albergue")
            )
        if action == "cancelar":
            return request.user.rol == User.Rol.ADOPTANTE
        return request.method in permissions.SAFE_METHODS

    def has_object_permission(self, request, view, obj):
        if getattr(view, "action", None) == "cancelar":
            return request.user.rol == User.Rol.ADOPTANTE and obj.adoptante_id == request.user.pk
        return super().has_object_permission(request, view, obj)


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

        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(adoptante=user)

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
            "en_revision": solicitudes.filter(estado=SolicitudAdopcion.Estado.OBSERVADA).count(),
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
        "postulante", "mascota", "albergue", "revisado_por", "adoptante"
    ).all()
    permission_classes = [permissions.IsAuthenticated, SolicitudAdopcionPermission]
    http_method_names = ["get", "post", "head", "options"]
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ["postulante__nombres", "postulante__apellidos", "postulante__documento_numero", "mascota__nombre"]
    ordering_fields = ["fecha_solicitud", "estado", "fecha_revision"]
    ordering = ["-fecha_solicitud"]

    def usa_resumen(self):
        return self.action in {"list", "retrieve"} and self.request.query_params.get("vista") == "resumen"

    def get_serializer_class(self):
        if self.usa_resumen():
            return SolicitudAdopcionResumenSerializer
        if self.action == "list":
            return SolicitudAdopcionListSerializer
        if self.action == "cambiar_estado":
            return SolicitudEstadoSerializer
        if self.action == "revisar_veterinaria":
            return RevisionVeterinariaSolicitudSerializer
        if self.action == "programar_recojo":
            return ProgramarRecojoSerializer
        if self.action == "confirmar_entrega":
            return ConfirmarEntregaSerializer
        if self.action == "create":
            return SolicitudCreateSerializer
        return SolicitudAdopcionSerializer

    def get_queryset(self):
        user = self.request.user
        qs = self.queryset
        if self.usa_resumen():
            qs = qs.defer("respuestas_cuestionario", "mensaje_postulante")

        if user.is_staff:
            return qs

        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(adoptante=user)

        try:
            albergue = user.albergue
            return qs.filter(albergue=albergue)
        except Albergue.DoesNotExist:
            return qs.none()

    def retrieve(self, request, *args, **kwargs):
        response = super().retrieve(request, *args, **kwargs)
        response["Cache-Control"] = "private, no-store"
        return response

    def _solicitud_entregada_del_titular(self, pk):
        solicitud = generics.get_object_or_404(self.get_queryset(), pk=pk, adoptante_id=self.request.user.pk)
        if solicitud.estado != SolicitudAdopcion.Estado.ENTREGADA:
            raise PermissionDenied("La ficha veterinaria estará disponible cuando se confirme la entrega.")
        return solicitud

    @action(detail=True, methods=["get"], url_path="ficha-veterinaria", permission_classes=[IsAdoptante])
    def ficha_veterinaria(self, request, pk=None):
        solicitud = self._solicitud_entregada_del_titular(pk)
        response = FileResponse(
            generar_ficha_veterinaria(solicitud.mascota), as_attachment=True,
            filename=f"ficha_veterinaria_solicitud_{solicitud.pk}.pdf", content_type="application/pdf",
        )
        response["Cache-Control"] = "private, no-store"
        return response

    @action(detail=True, methods=["get"], url_path="alerta-clinica", permission_classes=[IsAdoptante])
    def alerta_clinica(self, request, pk=None):
        solicitud = self._solicitud_entregada_del_titular(pk)
        return Response(
            {"alerta_clinica": solicitud.mascota.alerta_clinica},
            headers={"Cache-Control": "private, no-store"},
        )

    def perform_create(self, serializer):
        # Recheck under a row lock so concurrent submissions cannot both pass validation.
        mascota_id = serializer.validated_data["mascota"].pk
        postulante = serializer.validated_data.get("postulante")
        with transaction.atomic():
            mascota = Mascota.objects.select_for_update().filter(pk=mascota_id).first()
            if mascota is None:
                raise ValidationError({"mascota": "La mascota indicada no existe."})
            if mascota.estado != Mascota.Estado.DISPONIBLE:
                raise ValidationError({"mascota": "La mascota no está disponible para adopción."})
            if not mascota.albergue.activo:
                raise ValidationError({"mascota": "El albergue de esta mascota no está activo."})
            titular = Q(adoptante=self.request.user) | (Q(postulante=postulante) if postulante else Q(adoptante=self.request.user))
            activa = SolicitudAdopcion.objects.filter(
                titular,
                mascota=mascota,
                estado__in=[SolicitudAdopcion.Estado.PENDIENTE, SolicitudAdopcion.Estado.OBSERVADA],
            ).exists()
            if activa:
                raise ValidationError({"non_field_errors": ["Ya existe una solicitud activa para esta mascota."]})
            from .eligibilidad import comprobar_elegibilidad
            comprobar_elegibilidad(self.request.user, mascota.albergue_id, bloquear=True)
            solicitud = serializer.save(
                mascota=mascota,
                albergue=mascota.albergue,
                adoptante=self.request.user,
            )
            SolicitudEvento.objects.create(
                solicitud=solicitud,
                tipo=SolicitudEvento.Tipo.SOLICITUD_CREADA,
                estado_nuevo=solicitud.estado,
                descripcion="Solicitud enviada por el adoptante.",
                ocurrido_en=solicitud.fecha_solicitud,
                actor=self.request.user,
            )

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        self.perform_create(serializer)
        response_data = SolicitudAdopcionSerializer(serializer.instance, context=self.get_serializer_context()).data
        return Response(response_data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], url_path="cambiar-estado")
    def cambiar_estado(self, request, pk=None):
        """
        Cambia el estado de la solicitud.
        
        Transiciones válidas:
        - PENDIENTE -> OBSERVADA, APROBADA, RECHAZADA
        - OBSERVADA -> APROBADA, RECHAZADA
        - APROBADA -> (terminal)
        - RECHAZADA -> (terminal)
        - CANCELADA -> (terminal)
        - ENTREGADA -> (terminal)
        """
        solicitud = self.get_object()
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        nuevo_estado = serializer.validated_data["estado"]
        observaciones = serializer.validated_data.get("observaciones_revision", "")
        if nuevo_estado == SolicitudAdopcion.Estado.CANCELADA:
            raise ValidationError({
                "estado": "La cancelación debe solicitarla el adoptante titular.",
            })
        if nuevo_estado == SolicitudAdopcion.Estado.ENTREGADA:
            raise ValidationError({"estado": "Registra la entrega desde el contrato firmado."})
        solicitud = cambiar_estado_solicitud(
            solicitud.pk, nuevo_estado, request.user, observaciones
        )
        return Response(SolicitudAdopcionSerializer(solicitud, context={"request": request}).data)

    @action(detail=True, methods=["post"], url_path="revisar-veterinaria")
    @transaction.atomic
    def revisar_veterinaria(self, request, pk=None):
        solicitud = self.get_object()
        solicitud = SolicitudAdopcion.objects.select_for_update().select_related("mascota").get(pk=solicitud.pk)
        if solicitud.estado != SolicitudAdopcion.Estado.APROBADA:
            raise ValidationError({"estado": "La revisión veterinaria se realiza después de aprobar la solicitud."})
        if solicitud.fecha_recojo_programado or solicitud.estado == SolicitudAdopcion.Estado.ENTREGADA:
            raise ValidationError({"estado_revision_veterinaria": "La revisión ya no puede cambiar después de programar el recojo."})

        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        datos = serializer.validated_data
        estado = datos["estado_revision_veterinaria"]
        anterior = solicitud.estado_revision_veterinaria
        mascota = solicitud.mascota
        solicitud.estado_revision_veterinaria = estado
        solicitud.observaciones_revision_veterinaria = datos.get("observaciones_revision_veterinaria", "").strip()
        solicitud.ficha_veterinaria_revisada = {
            "esterilizado": mascota.esterilizado,
            "vacunas_al_dia": mascota.vacunas_al_dia,
            "desparasitado": mascota.desparasitado,
            "estado_salud_general": mascota.estado_salud_general,
            "necesidades_especiales": mascota.necesidades_especiales,
        }
        solicitud.fecha_revision_veterinaria = timezone.now()
        solicitud.revision_veterinaria_por = request.user
        solicitud.save(update_fields=[
            "estado_revision_veterinaria", "observaciones_revision_veterinaria",
            "ficha_veterinaria_revisada", "fecha_revision_veterinaria",
            "revision_veterinaria_por", "actualizado_en",
        ])
        tipo_evento = {
            SolicitudAdopcion.RevisionVeterinaria.APTA: SolicitudEvento.Tipo.VETERINARIA_APTA,
            SolicitudAdopcion.RevisionVeterinaria.OBSERVADA: SolicitudEvento.Tipo.VETERINARIA_OBSERVADA,
            SolicitudAdopcion.RevisionVeterinaria.RECHAZADA: SolicitudEvento.Tipo.VETERINARIA_RECHAZADA,
        }.get(estado)
        descripcion = solicitud.observaciones_revision_veterinaria
        evento = SolicitudEvento.objects.create(
            solicitud=solicitud,
            tipo=tipo_evento,
            estado_anterior=anterior,
            estado_nuevo=estado,
            descripcion=descripcion,
            actor=request.user,
            visible_adoptante=True,
        )
        if solicitud.adoptante_id:
            AvisoExterno.objects.create(evento=evento)
        return Response(SolicitudAdopcionSerializer(solicitud, context={"request": request}).data)

    @action(detail=True, methods=["post"], url_path="programar-recojo")
    @transaction.atomic
    def programar_recojo(self, request, pk=None):
        solicitud = self.get_object()
        solicitud = SolicitudAdopcion.objects.select_for_update().select_related(
            "mascota", "albergue", "adoptante"
        ).get(pk=solicitud.pk)
        if solicitud.estado != SolicitudAdopcion.Estado.APROBADA:
            raise ValidationError({"estado": "Solo se puede programar el recojo de una solicitud aprobada."})
        if solicitud.estado_revision_veterinaria != SolicitudAdopcion.RevisionVeterinaria.APTA:
            raise ValidationError({"estado_revision_veterinaria": "La mascota debe quedar apta antes de programar el recojo."})
        if not solicitud.adoptante_id:
            raise ValidationError({"adoptante": "La solicitud no está vinculada a una cuenta adoptante."})

        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        datos = serializer.validated_data
        fecha = datos["fecha_recojo_programado"]
        mensaje = datos["mensaje_recojo"].strip()
        solicitud.fecha_recojo_programado = fecha
        solicitud.mensaje_recojo = mensaje
        solicitud.recojo_programado_por = request.user
        solicitud.save(update_fields=[
            "fecha_recojo_programado", "mensaje_recojo", "recojo_programado_por", "actualizado_en",
        ])

        fecha_local = timezone.localtime(fecha).strftime("%d/%m/%Y a las %H:%M")
        texto_chat = f"Recojo acordado para {fecha_local}. {mensaje}"
        from apps.mensajeria.models import Conversacion, Mensaje, ParticipanteConversacion
        conversacion, _ = Conversacion.objects.get_or_create(
            solicitud=solicitud,
            defaults={"adoptante": solicitud.adoptante, "albergue": solicitud.albergue},
        )
        ParticipanteConversacion.objects.get_or_create(conversacion=conversacion, usuario=solicitud.adoptante)
        ParticipanteConversacion.objects.get_or_create(conversacion=conversacion, usuario=solicitud.albergue.usuario)
        Mensaje.objects.create(conversacion=conversacion, remitente=request.user, texto=texto_chat)
        conversacion.save(update_fields=["actualizada_en"])

        evento = SolicitudEvento.objects.create(
            solicitud=solicitud,
            tipo=SolicitudEvento.Tipo.RECOJO_PROGRAMADO,
            estado_nuevo=solicitud.estado,
            descripcion=texto_chat,
            actor=request.user,
            visible_adoptante=True,
        )
        AvisoExterno.objects.create(evento=evento)
        return Response(SolicitudAdopcionSerializer(solicitud, context={"request": request}).data)

    @action(detail=True, methods=["post"], url_path="confirmar-entrega")
    @transaction.atomic
    def confirmar_entrega(self, request, pk=None):
        solicitud = self.get_object()
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        if solicitud.estado != SolicitudAdopcion.Estado.APROBADA:
            raise ValidationError({"estado": "Solo se puede confirmar la entrega de una solicitud aprobada."})
        if solicitud.estado_revision_veterinaria != SolicitudAdopcion.RevisionVeterinaria.APTA:
            raise ValidationError({"estado_revision_veterinaria": "La revisión veterinaria debe estar aprobada."})
        if not solicitud.fecha_recojo_programado:
            raise ValidationError({"fecha_recojo_programado": "Programa el recojo antes de confirmar la entrega."})
        try:
            solicitud = cambiar_estado_solicitud(
                solicitud.pk,
                SolicitudAdopcion.Estado.ENTREGADA,
                request.user,
                entrega_data=serializer.validated_data,
            )
        except Exception as exc:
            from django.core.exceptions import ValidationError as DjangoValidationError
            if isinstance(exc, DjangoValidationError):
                raise ValidationError({"detail": exc.messages}) from exc
            raise
        return Response(SolicitudAdopcionSerializer(solicitud, context={"request": request}).data)

    @action(detail=True, methods=["post"], url_path="cancelar")
    def cancelar(self, request, pk=None):
        """Cancela una solicitud propia antes de que el albergue la apruebe."""
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante titular puede cancelar esta solicitud.")
        solicitud = self.get_object()
        solicitud = cambiar_estado_solicitud(
            solicitud.pk,
            SolicitudAdopcion.Estado.CANCELADA,
            request.user,
        )
        return Response(
            SolicitudAdopcionSerializer(solicitud, context={"request": request}).data
        )

    @action(detail=True, methods=["get"], url_path="historial-seguimiento")
    def historial_seguimiento(self, request, pk=None):
        """Entrega eventos y fechas clave solo al adoptante o albergue involucrado."""
        solicitud = self.get_object()
        eventos = solicitud.eventos.all()
        if request.user.rol == User.Rol.ADOPTANTE:
            eventos = eventos.filter(visible_adoptante=True)
        serialized = SolicitudEventoSerializer(eventos, many=True).data
        fecha_por_tipo = {}
        tipo_fecha = {
            SolicitudEvento.Tipo.REGISTRO_HISTORICO: "solicitudCreada",
            SolicitudEvento.Tipo.SOLICITUD_CREADA: "solicitudCreada",
            SolicitudEvento.Tipo.OBSERVADA: "observada",
            SolicitudEvento.Tipo.APROBADA: "aprobada",
            SolicitudEvento.Tipo.RECHAZADA: "rechazada",
            SolicitudEvento.Tipo.CANCELADA: "cancelada",
            SolicitudEvento.Tipo.CONTRATO_GENERADO: "contratoGenerado",
            SolicitudEvento.Tipo.CONTRATO_ENVIADO: "contratoEnviadoAFirma",
            SolicitudEvento.Tipo.CONTRATO_FIRMADO: "contratoFirmado",
            SolicitudEvento.Tipo.CONTRATO_EXPIRADO: "contratoExpirado",
            SolicitudEvento.Tipo.ENTREGADA: "entregada",
        }
        for evento in eventos:
            campo = tipo_fecha.get(evento.tipo)
            if campo and campo not in fecha_por_tipo:
                fecha_por_tipo[campo] = evento.ocurrido_en
        return Response({
            "solicitudId": solicitud.pk,
            "estadoActual": solicitud.estado,
            "eventos": serialized,
            "fechasClave": fecha_por_tipo,
        })

    @action(detail=False, methods=["get"], url_path="estadisticas")
    def estadisticas(self, request):
        """Estadísticas de solicitudes para dashboard."""
        qs = self.get_queryset()
        stats = {
            "total": qs.count(),
            "pendientes": qs.filter(estado=SolicitudAdopcion.Estado.PENDIENTE).count(),
            "en_revision": qs.filter(estado=SolicitudAdopcion.Estado.OBSERVADA).count(),
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
    permission_classes = [permissions.IsAuthenticated, IsAdoptante]

    def get_object(self):
        profile, created = AdoptanteProfile.objects.get_or_create(user=self.request.user)
        return profile

    def get_serializer_class(self):
        if self.request.method in ["PUT", "PATCH"]:
            return AdoptanteProfileCreateUpdateSerializer
        return AdoptanteProfileSerializer

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        # The update serializer locks and saves a fresh ORM instance. Refresh the
        # response instance so compatibility_revision reflects the committed value.
        instance.refresh_from_db()
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


class AutorizacionViewSet(viewsets.ModelViewSet):
    """
    Gestión de autorizaciones de perfil por el adoptante (US-18).
    El adoptante puede autorizar a albergues específicos a consultar su perfil,
    ver sus autorizaciones activas y revocarlas.
    """
    permission_classes = [permissions.IsAuthenticated]
    # No AuthThrottle aquí; usa throttle por defecto de usuario (100/min)

    def get_serializer_class(self):
        if self.action == "create":
            return AutorizacionCreateSerializer
        if self.action == "revocar":
            return AutorizacionRevokeSerializer
        return AutorizacionSerializer

    def get_queryset(self):
        # El adoptante ve solo sus autorizaciones
        return Autorizacion.objects.filter(adoptante=self.request.user).select_related("albergue")

    def get_serializer_context(self):
        context = super().get_serializer_context()
        context["request"] = self.request
        return context

    @action(detail=True, methods=["post"], url_path="revocar")
    def revocar(self, request, pk=None):
        """Revoca una autorización vigente."""
        autorizacion = self.get_object()
        if autorizacion.estado != Autorizacion.Estado.VIGENTE:
            return Response(
                {"detail": f"No se puede revocar una autorización {autorizacion.get_estado_display().lower()}."},
                status=status.HTTP_400_BAD_REQUEST
            )
        autorizacion.revocar()
        return Response(AutorizacionSerializer(autorizacion, context={"request": request}).data)

    @action(detail=False, methods=["get"], url_path="activas")
    def activas(self, request):
        """Lista solo autorizaciones vigentes."""
        qs = self.get_queryset().filter(estado=Autorizacion.Estado.VIGENTE)
        serializer = self.get_serializer(qs, many=True)
        return Response(serializer.data)


class AutorizacionCheckView(APIView):
    """
    Verifica si un adoptante ha autorizado a un albergue específico (US-18).
    Endpoint interno llamado por Spring Boot para validar autorización.
    """
    permission_classes = [permissions.IsAuthenticated]
    # Sin AuthThrottle: endpoint interno, no auth público

    def get(self, request):
        adoptante_id = request.query_params.get("adoptante_id")
        albergue_id = request.query_params.get("albergue_id")
        
        if not adoptante_id or not albergue_id:
            return Response({"detail": "adoptante_id y albergue_id son requeridos."}, status=status.HTTP_400_BAD_REQUEST)
        
        try:
            adoptante_id = int(adoptante_id)
            albergue_id = int(albergue_id)
        except ValueError:
            return Response({"detail": "IDs inválidos."}, status=status.HTTP_400_BAD_REQUEST)
        
        # Verificar que el usuario autenticado es un albergue
        if not hasattr(request.user, 'albergue') or request.user.albergue.id != albergue_id:
            return Response({"detail": "No autorizado: solo el albergue propietario puede consultar."}, status=status.HTTP_403_FORBIDDEN)
        
        # Verificar autorización usando el modelo Autorizacion
        from .models import Autorizacion, AdoptanteProfile
        
        try:
            autorizacion = Autorizacion.objects.get(
                adoptante_id=adoptante_id,
                albergue_id=albergue_id
            )
        except Autorizacion.DoesNotExist:
            return Response({
                "autorizado": False,
                "detail": "El adoptante no ha autorizado a este albergue.",
                "campos_permitidos": []
            })
        
        if not autorizacion.esta_vigente():
            return Response({
                "autorizado": False,
                "detail": f"Autorización {autorizacion.get_estado_display().lower()}.",
                "campos_permitidos": []
            })
        
        # Verificar que el adoptante existe y tiene perfil
        try:
            profile = AdoptanteProfile.objects.get(user_id=adoptante_id)
        except AdoptanteProfile.DoesNotExist:
            return Response({"autorizado": False, "detail": "Perfil del adoptante no encontrado."}, status=status.HTTP_404_NOT_FOUND)
        
        campos_permitidos = autorizacion.campos_permitidos or [
            "tipo_vivienda", "tiene_patio", "tiene_ninos", "tiene_otras_mascotas",
            "detalles_otras_mascotas", "experiencia_previa", "motivo_adopcion",
            "preferencia_especie", "preferencia_tamanio", "preferencia_edad",
            "horas_fuera", "presupuesto"
        ]
        
        return Response({
            "autorizado": True,
            "campos_permitidos": campos_permitidos
        })
