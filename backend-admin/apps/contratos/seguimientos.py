from django.utils import timezone
from django.http import FileResponse
from django.db import transaction
from django.db.models import Prefetch
from rest_framework import permissions, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento, User
from apps.acceso.auditoria import registrar_evento_auditoria
from .models import (
    AlertaIncumplimientoSeguimiento,
    ExpedientePostAdopcion,
    ReporteBienestarPostAdopcion,
    SeguimientoPostAdopcion,
    SolicitudAsesoriaVeterinaria,
)
from .alertas_incumplimiento import generar_alertas_incumplimiento
from .reportes import ReporteBienestarReadSerializer
from .certificados import generar_certificado_expediente


class SolicitudAsesoriaVeterinariaSerializer(serializers.ModelSerializer):
    class Meta:
        model = SolicitudAsesoriaVeterinaria
        fields = ["id", "seguimiento", "problema", "estado", "creado_en"]
        read_only_fields = ["id", "estado", "creado_en"]

    def get_fields(self):
        fields = super().get_fields()
        request = self.context.get("request")
        queryset = SeguimientoPostAdopcion.objects.none()
        if request and request.user.is_authenticated:
            queryset = SeguimientoPostAdopcion.objects.filter(
                solicitud__adoptante=request.user,
                solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
                solicitud__expediente_post_adopcion__cerrado_en__isnull=True,
            ).exclude(estado=SeguimientoPostAdopcion.Estado.CANCELADO)
        fields["seguimiento"].queryset = queryset
        return fields


class SolicitudAsesoriaVeterinariaViewSet(viewsets.ModelViewSet):
    serializer_class = SolicitudAsesoriaVeterinariaSerializer
    permission_classes = [permissions.IsAuthenticated]
    http_method_names = ["get", "post", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = SolicitudAsesoriaVeterinaria.objects.select_related("seguimiento__solicitud")
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(seguimiento__solicitud__adoptante=user)
        if user.rol == User.Rol.ALBERGUE:
            return qs.filter(seguimiento__solicitud__albergue__usuario=user)
        return qs.none()

    def create(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante puede solicitar asesoría veterinaria.")
        return super().create(request, *args, **kwargs)

    @action(detail=True, methods=["post"], url_path="cambiar-estado")
    def cambiar_estado(self, request, pk=None):
        if request.user.rol != User.Rol.ALBERGUE:
            raise PermissionDenied("Solo el albergue responsable puede actualizar la asesoría.")
        serializer = EstadoAsesoriaSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            asesoria = self.get_queryset().select_for_update().get(pk=self.get_object().pk)
            nuevo = serializer.validated_data["estado"]
            transitions = {
                SolicitudAsesoriaVeterinaria.Estado.PENDIENTE: {SolicitudAsesoriaVeterinaria.Estado.EN_ATENCION},
                SolicitudAsesoriaVeterinaria.Estado.EN_ATENCION: {SolicitudAsesoriaVeterinaria.Estado.ATENDIDA},
            }
            if nuevo != asesoria.estado and nuevo not in transitions.get(asesoria.estado, set()):
                return Response({"detail": "La asesoría debe pasar de pendiente a en atención y luego a atendida."}, status=409)
            asesoria.estado = nuevo
            asesoria.save(update_fields=["estado"])
        return Response(self.get_serializer(asesoria).data)


class EstadoAsesoriaSerializer(serializers.Serializer):
    estado = serializers.ChoiceField(choices=SolicitudAsesoriaVeterinaria.Estado.choices)


class GestionarSeguimientoPermission(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.method in permissions.SAFE_METHODS or request.user.rol == User.Rol.ALBERGUE


class EvaluacionSerializer(serializers.Serializer):
    calificacion_adaptacion = serializers.IntegerField(min_value=1, max_value=5)
    observaciones_cierre = serializers.CharField(max_length=2000, required=False, allow_blank=True)


class CerrarExpedienteSerializer(serializers.Serializer):
    confirmar = serializers.BooleanField()

    def validate_confirmar(self, value):
        if not value:
            raise serializers.ValidationError("Confirma el cierre del expediente.")
        return value


class ExpedientePostAdopcionSerializer(serializers.ModelSerializer):
    class Meta:
        model = ExpedientePostAdopcion
        fields = ["id", "calificacion_adaptacion", "observaciones_cierre", "cerrado_en"]


class SeguimientoPostAdopcionSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField()
    mascota_nombre = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    adoptante_email = serializers.EmailField(source="solicitud.adoptante.email", read_only=True, allow_null=True)
    albergue_nombre = serializers.CharField(source="solicitud.albergue.nombre", read_only=True)
    asesorias_veterinarias = SolicitudAsesoriaVeterinariaSerializer(many=True, read_only=True)
    expediente_cierre = serializers.SerializerMethodField()
    reporte_bienestar = serializers.SerializerMethodField()
    alerta_incumplimiento = serializers.SerializerMethodField()
    estado_entrega = serializers.SerializerMethodField()

    class Meta:
        model = SeguimientoPostAdopcion
        fields = ["id", "solicitud_id", "mascota_nombre", "adoptante_email", "programado_para",
                  "modalidad", "estado", "notas", "creado_en", "actualizado_en",
                  "albergue_nombre", "asesorias_veterinarias", "expediente_cierre", "reporte_bienestar"]
        fields += ["alerta_incumplimiento", "requiere_reporte", "estado_entrega", "cancelado_en"]
        read_only_fields = ["id", "estado", "creado_en", "actualizado_en", "cancelado_en"]

    def get_expediente_cierre(self, obj):
        try:
            expediente = obj.solicitud.expediente_post_adopcion
        except ExpedientePostAdopcion.DoesNotExist:
            return None
        if expediente.cerrado_en is None:
            return None
        return ExpedientePostAdopcionSerializer(expediente).data

    def get_reporte_bienestar(self, obj):
        try:
            reporte = obj.reporte_bienestar
        except ReporteBienestarPostAdopcion.DoesNotExist:
            return None
        return ReporteBienestarReadSerializer(reporte, context=self.context).data

    def get_estado_entrega(self, obj):
        if not obj.requiere_reporte:
            return None
        reporte = getattr(obj, "reporte_bienestar", None)
        if reporte:
            return "FUERA_DE_PLAZO" if reporte.enviado_en > obj.programado_para else "ENVIADA"
        if obj.estado == SeguimientoPostAdopcion.Estado.CANCELADO:
            return "CANCELADA"
        expediente = getattr(obj.solicitud, "expediente_post_adopcion", None)
        cierre = expediente.cerrado_en if expediente else None
        if cierre:
            return "VENCIDA" if obj.programado_para <= cierre else "SIN_ENTREGA"
        return "VENCIDA" if obj.programado_para <= timezone.now() else "PENDIENTE"

    def get_alerta_incumplimiento(self, obj):
        alerta = getattr(obj, "alerta_incumplimiento", None)
        if not alerta or (self.context["request"].user.rol == User.Rol.ADOPTANTE and not alerta.emitida_en):
            return None
        return {
            "estado": alerta.estado,
            "generada_en": alerta.generada_en,
            "emitida_en": alerta.emitida_en,
            "resuelta_en": alerta.resuelta_en,
        }

    def validate_solicitud_id(self, value):
        request = self.context["request"]
        qs = SolicitudAdopcion.objects.select_related("albergue", "adoptante", "mascota")
        if request.user.rol == User.Rol.ALBERGUE and hasattr(request.user, "albergue"):
            qs = qs.filter(albergue=request.user.albergue)
        solicitud = qs.filter(pk=value, estado=SolicitudAdopcion.Estado.ENTREGADA).first()
        if solicitud is None:
            raise serializers.ValidationError("Debe corresponder a una adopción entregada de tu albergue.")
        return value

    def validate_programado_para(self, value):
        if value <= timezone.now():
            raise serializers.ValidationError("La fecha y hora deben ser futuras.")
        return value

    def validate(self, attrs):
        solicitud_id = attrs.get("solicitud_id", getattr(self.instance, "solicitud_id", None))
        if self.instance and getattr(self.instance, "alerta_incumplimiento", None):
            alerta = self.instance.alerta_incumplimiento
            if alerta.emitida_en:
                raise ValidationError("El plazo queda registrado en el historial de la alerta emitida.")
        if self.instance and self.instance.estado != SeguimientoPostAdopcion.Estado.PROGRAMADO:
            raise ValidationError("Solo se pueden editar seguimientos programados.")
        if self.instance and solicitud_id != self.instance.solicitud_id:
            raise ValidationError({"solicitud_id": "La actividad pertenece a su expediente original."})
        if solicitud_id:
            user = self.context["request"].user
            solicitud = SolicitudAdopcion.objects.filter(pk=solicitud_id).first()
            if not solicitud or solicitud.estado != SolicitudAdopcion.Estado.ENTREGADA:
                raise ValidationError({"solicitud_id": "El seguimiento requiere una adopción entregada."})
            if user.rol == User.Rol.ALBERGUE and solicitud.albergue.usuario_id != user.pk:
                raise PermissionDenied("La adopción no pertenece a tu albergue.")
            if self.instance and hasattr(self.instance, "reporte_bienestar"):
                raise ValidationError({"solicitud_id": "No se puede editar un seguimiento con un reporte ya enviado."})
            if expediente_cerrado(solicitud):
                raise ValidationError({"solicitud_id": "El expediente ya está cerrado; no se puede editar su seguimiento."})
        if attrs.get("modalidad", getattr(self.instance, "modalidad", None)) == SeguimientoPostAdopcion.Modalidad.REPORTE:
            attrs["requiere_reporte"] = True
        return attrs


def expediente_cerrado(solicitud):
    expediente = getattr(solicitud, "expediente_post_adopcion", None)
    return expediente is not None and expediente.cerrado_en is not None


def seguimientos_con_detalle():
    return SeguimientoPostAdopcion.objects.select_related(
        "solicitud__mascota", "solicitud__adoptante", "solicitud__albergue",
        "solicitud__expediente_post_adopcion", "reporte_bienestar", "alerta_incumplimiento",
    ).prefetch_related("asesorias_veterinarias", "reporte_bienestar__fotos")


class ExpedienteAlbergueSerializer(serializers.ModelSerializer):
    mascota_nombre = serializers.CharField(source="mascota.nombre")
    adoptante_email = serializers.EmailField(source="adoptante.email", allow_null=True)
    adoptante_nombre = serializers.SerializerMethodField()
    evaluacion = ExpedientePostAdopcionSerializer(source="expediente_post_adopcion", read_only=True)
    cronograma = SeguimientoPostAdopcionSerializer(source="seguimientos_post_adopcion", many=True)
    fecha_entrega = serializers.DateField(read_only=True)
    entrega_confirmada_en = serializers.DateTimeField(read_only=True)
    entrega_satisfaccion = serializers.IntegerField(read_only=True)
    entrega_observaciones = serializers.CharField(read_only=True)

    class Meta:
        model = SolicitudAdopcion
        fields = ["id", "mascota_nombre", "adoptante_email", "adoptante_nombre", "fecha_entrega",
                  "entrega_confirmada_en", "entrega_satisfaccion", "entrega_observaciones", "evaluacion", "cronograma"]

    def get_adoptante_nombre(self, obj):
        if not obj.adoptante:
            return "Cuenta adoptante no disponible"
        return obj.adoptante.get_full_name() or obj.adoptante.email or obj.adoptante.username


class ExpedienteAlbergueViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = ExpedienteAlbergueSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        qs = SolicitudAdopcion.objects.filter(estado=SolicitudAdopcion.Estado.ENTREGADA)
        if user.rol != User.Rol.ALBERGUE:
            return qs.none()
        return qs.filter(albergue__usuario=user).select_related(
            "mascota", "adoptante", "expediente_post_adopcion",
            "albergue", "expediente_post_adopcion__cerrado_por",
        ).prefetch_related(
            Prefetch("seguimientos_post_adopcion", queryset=seguimientos_con_detalle()),
        ).order_by("-id")

    @action(detail=True, methods=["get"], url_path="certificado")
    def certificado(self, request, pk=None):
        solicitud = self.get_object()
        expediente = getattr(solicitud, "expediente_post_adopcion", None)
        if expediente is None or expediente.cerrado_en is None:
            return Response(
                {"detail": "El certificado estará disponible cuando el expediente esté cerrado."},
                status=status.HTTP_409_CONFLICT,
            )

        documento = generar_certificado_expediente(solicitud)
        return FileResponse(
            documento,
            as_attachment=True,
            filename=f"certificado-seguimiento-{solicitud.pk}.pdf",
            content_type="application/pdf",
        )


class SeguimientoPostAdopcionViewSet(viewsets.ModelViewSet):
    serializer_class = SeguimientoPostAdopcionSerializer
    permission_classes = [permissions.IsAuthenticated, GestionarSeguimientoPermission]
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        qs = seguimientos_con_detalle().filter(solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA)
        solicitud_id = self.request.query_params.get("solicitud_id")
        if solicitud_id is not None:
            filtro = serializers.IntegerField(min_value=1).run_validation(solicitud_id)
            qs = qs.filter(solicitud_id=filtro)
        if user.rol == User.Rol.ALBERGUE and hasattr(user, "albergue"):
            return qs.filter(solicitud__albergue=user.albergue)
        if user.rol == User.Rol.ADOPTANTE:
            return qs.filter(solicitud__adoptante=user, oculto_adoptante=False)
        return qs.none()

    @action(detail=True, methods=["post"], url_path="ocultar", permission_classes=[permissions.IsAuthenticated])
    def ocultar(self, request, pk=None):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante titular puede quitar una entrega de su cronograma.")
        entregas = SeguimientoPostAdopcion.objects.filter(
            pk=pk, requiere_reporte=True, solicitud__adoptante=request.user,
            solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
        )
        if not entregas.update(oculto_adoptante=True):
            return Response({"detail": "No se encontró la entrega."}, status=404)
        return Response(status=status.HTTP_204_NO_CONTENT)

    def list(self, request, *args, **kwargs):
        if request.user.rol == User.Rol.ALBERGUE:
            # La tarea programada es la fuente principal; reconciliar al abrir el panel
            # también cubre instalaciones locales donde aún no configuraron el scheduler.
            generar_alertas_incumplimiento()
        return super().list(request, *args, **kwargs)

    @transaction.atomic
    def create(self, request, *args, **kwargs):
        # Todas las escrituras del expediente comparten este bloqueo.
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        solicitud = SolicitudAdopcion.objects.select_for_update().get(
            pk=serializer.validated_data["solicitud_id"],
        )
        if expediente_cerrado(solicitud) or solicitud.estado != SolicitudAdopcion.Estado.ENTREGADA:
            return Response({"detail": "La adopción ya no admite nuevas actividades."}, status=409)
        if not solicitud.adoptante_id:
            raise ValidationError({"solicitud_id": "Esta adopción no tiene una cuenta adoptante vinculada."})
        seguimiento = serializer.save(creado_por=request.user)
        SolicitudEvento.objects.create(
            solicitud=solicitud,
            seguimiento=seguimiento,
            tipo=SolicitudEvento.Tipo.SEGUIMIENTO_PROGRAMADO,
            destinatario_id=solicitud.adoptante_id,
            actor=request.user,
        )
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    @transaction.atomic
    def update(self, request, *args, **kwargs):
        SolicitudAdopcion.objects.select_for_update().get(pk=self.get_object().solicitud_id)
        return super().update(request, *args, **kwargs)

    @action(detail=True, methods=["post"], url_path="evaluar-expediente")
    @transaction.atomic
    def evaluar_expediente(self, request, pk=None):
        solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=self.get_object().solicitud_id)
        if expediente_cerrado(solicitud):
            return Response({"detail": "El expediente ya está cerrado."}, status=409)
        if not ReporteBienestarPostAdopcion.objects.filter(seguimiento__solicitud=solicitud).exists():
            return Response({"detail": "La evaluación se habilita al recibir el primer reporte."}, status=409)
        serializer = EvaluacionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        expediente, _ = ExpedientePostAdopcion.objects.update_or_create(
            solicitud=solicitud,
            defaults={"cerrado_en": None, **serializer.validated_data},
        )
        return Response(ExpedientePostAdopcionSerializer(expediente).data)

    @action(detail=True, methods=["post"], url_path="cerrar-expediente")
    @transaction.atomic
    def cerrar_expediente(self, request, pk=None):
        solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=self.get_object().solicitud_id)
        serializer = CerrarExpedienteSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        expediente = getattr(solicitud, "expediente_post_adopcion", None)
        if expediente is None:
            return Response({"detail": "Guarda la evaluación antes de cerrar el expediente."}, status=409)
        if expediente.cerrado_en is None:
            expediente.cerrado_en = timezone.now()
            expediente.cerrado_por = request.user
            expediente.save(update_fields=["cerrado_en", "cerrado_por"])
            AlertaIncumplimientoSeguimiento.objects.filter(
                seguimiento__solicitud=solicitud, estado=AlertaIncumplimientoSeguimiento.Estado.PENDIENTE,
            ).update(estado=AlertaIncumplimientoSeguimiento.Estado.RESUELTA, resuelta_en=expediente.cerrado_en)
            registrar_evento_auditoria(
                request.user,
                accion="EXPEDIENTE_POSTADOPCION_CERRADO",
                recurso_tipo="SOLICITUD_ADOPCION",
                recurso_id=solicitud.pk,
                ocurrido_en=expediente.cerrado_en,
            )
        return Response({
            "detail": "Expediente de adopción cerrado.",
            "expediente_cierre": ExpedientePostAdopcionSerializer(expediente).data,
        }, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], url_path="emitir-incumplimiento")
    @transaction.atomic
    def emitir_incumplimiento(self, request, pk=None):
        solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=self.get_object().solicitud_id)
        seguimiento = self.get_object()
        alerta = getattr(seguimiento, "alerta_incumplimiento", None)
        if alerta and alerta.emitida_en:
            return Response(self.get_serializer(seguimiento).data)
        ahora = timezone.now()
        if (expediente_cerrado(solicitud) or not solicitud.adoptante_id
                or not seguimiento.requiere_reporte or seguimiento.programado_para > ahora
                or seguimiento.estado == SeguimientoPostAdopcion.Estado.CANCELADO
                or hasattr(seguimiento, "reporte_bienestar")):
            return Response({"detail": "La alerta requiere una entrega vencida sin reporte en un expediente abierto."}, status=409)
        alerta, _ = AlertaIncumplimientoSeguimiento.objects.get_or_create(seguimiento=seguimiento)
        alerta.emitida_en = ahora
        alerta.estado = AlertaIncumplimientoSeguimiento.Estado.PENDIENTE
        alerta.resuelta_en = None
        alerta.save(update_fields=["emitida_en", "estado", "resuelta_en"])
        SolicitudEvento.objects.get_or_create(
            seguimiento=seguimiento,
            tipo=SolicitudEvento.Tipo.INCUMPLIMIENTO_REPORTE,
            defaults={"solicitud": solicitud, "actor": request.user, "ocurrido_en": ahora},
        )
        return Response(self.get_serializer(self.get_object()).data)

    @transaction.atomic
    def destroy(self, request, *args, **kwargs):
        solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=self.get_object().solicitud_id)
        seguimiento = self.get_object()
        if hasattr(seguimiento, "reporte_bienestar"):
            return Response({"detail": "No se puede cancelar un seguimiento con un reporte ya enviado."}, status=409)
        if expediente_cerrado(solicitud):
            return Response({"detail": "El expediente está cerrado; no se puede cancelar el seguimiento."}, status=409)
        if seguimiento.estado != SeguimientoPostAdopcion.Estado.PROGRAMADO:
            return Response({"detail": "Solo se pueden cancelar seguimientos programados."}, status=409)
        seguimiento.estado = SeguimientoPostAdopcion.Estado.CANCELADO
        seguimiento.cancelado_en = timezone.now()
        seguimiento.save(update_fields=["estado", "cancelado_en", "actualizado_en"])
        AlertaIncumplimientoSeguimiento.objects.filter(
            seguimiento=seguimiento, estado=AlertaIncumplimientoSeguimiento.Estado.PENDIENTE,
        ).update(estado=AlertaIncumplimientoSeguimiento.Estado.RESUELTA, resuelta_en=seguimiento.cancelado_en)
        return Response(status=status.HTTP_204_NO_CONTENT)
