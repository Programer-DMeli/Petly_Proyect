import logging
import re
import unicodedata
from datetime import timedelta

from django.db.models import F, Sum
from django.utils import timezone
from django.utils.dateparse import parse_date
from rest_framework import permissions, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response

from .models import MetricaRespuestaChatbot, RespuestaAutomaticaChatbot


logger = logging.getLogger(__name__)


class RespuestaAutomaticaChatbotSerializer(serializers.ModelSerializer):
    disparadores = serializers.ListField(
        child=serializers.CharField(min_length=3, max_length=80, trim_whitespace=True),
        max_length=12,
        required=False,
        default=list,
    )

    class Meta:
        model = RespuestaAutomaticaChatbot
        fields = ["id", "pregunta_clave", "disparadores", "respuesta", "activa", "creado_en", "actualizado_en"]
        read_only_fields = ["id", "creado_en", "actualizado_en"]

    def validate_pregunta_clave(self, value):
        value = value.strip()
        if len(value) < 4:
            raise serializers.ValidationError("Escribe una pregunta clave de al menos 4 caracteres.")
        return value

    def validate_respuesta(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("La respuesta no puede estar vacía.")
        return value

    def validate_disparadores(self, value):
        unique = []
        seen = set()
        for trigger in value:
            cleaned = trigger.strip()
            normalized = _normalize(cleaned)
            if normalized and normalized not in seen:
                unique.append(cleaned)
                seen.add(normalized)
        return unique

    def validate(self, attrs):
        user = self.context["request"].user
        if user.rol == "ALBERGUE" and hasattr(user, "albergue"):
            question = attrs.get("pregunta_clave", getattr(self.instance, "pregunta_clave", ""))
            duplicates = RespuestaAutomaticaChatbot.objects.filter(
                albergue=user.albergue,
                pregunta_clave__iexact=question,
            )
            if self.instance:
                duplicates = duplicates.exclude(pk=self.instance.pk)
            if duplicates.exists():
                raise ValidationError({"pregunta_clave": "Ya existe una regla con esa pregunta clave."})
        return attrs


class RespuestaAutomaticaPermission(permissions.BasePermission):
    def has_permission(self, request, view):
        if not request.user.is_authenticated:
            return False
        if view.action == "coincidencia":
            return request.user.rol == "ADOPTANTE"
        return request.user.rol == "ALBERGUE" and hasattr(request.user, "albergue")

    def has_object_permission(self, request, view, obj):
        return obj.albergue.usuario_id == request.user.pk


def _normalize(value):
    value = unicodedata.normalize("NFD", value.casefold())
    value = "".join(char for char in value if unicodedata.category(char) != "Mn")
    return re.sub(r"[^\w\s]", " ", value, flags=re.UNICODE).strip()


class RespuestaAutomaticaChatbotViewSet(viewsets.ModelViewSet):
    serializer_class = RespuestaAutomaticaChatbotSerializer
    permission_classes = [RespuestaAutomaticaPermission]
    http_method_names = ["get", "post", "patch", "delete", "head", "options"]

    def get_queryset(self):
        if self.action == "coincidencia":
            return RespuestaAutomaticaChatbot.objects.none()
        if self.request.user.rol != "ALBERGUE" or not hasattr(self.request.user, "albergue"):
            return RespuestaAutomaticaChatbot.objects.none()
        return RespuestaAutomaticaChatbot.objects.filter(albergue=self.request.user.albergue)

    def perform_create(self, serializer):
        if self.request.user.rol != "ALBERGUE" or not hasattr(self.request.user, "albergue"):
            raise PermissionDenied("Solo el albergue puede configurar sus respuestas automáticas.")
        serializer.save(albergue=self.request.user.albergue)

    @action(detail=False, methods=["get"], url_path="metricas")
    def metricas(self, request):
        hoy = timezone.localdate()
        desde = _parse_metric_date(request.query_params.get("desde"), "desde", hoy - timedelta(days=29))
        hasta = _parse_metric_date(request.query_params.get("hasta"), "hasta", hoy)
        if desde > hasta:
            raise ValidationError({"desde": "La fecha inicial debe ser anterior o igual a la fecha final."})
        if hasta > hoy:
            raise ValidationError({"hasta": "La fecha final no puede estar en el futuro."})
        if (hasta - desde).days > 365:
            raise ValidationError({"desde": "El periodo máximo permitido es de 366 días."})

        metricas = list(
            MetricaRespuestaChatbot.objects.filter(
                albergue=request.user.albergue,
                fecha__range=(desde, hasta),
            )
            .values("pregunta_clave")
            .annotate(consultas=Sum("consultas"))
            .order_by("-consultas", "pregunta_clave")
        )
        return Response({
            "desde": desde,
            "hasta": hasta,
            "total_consultas": sum(item["consultas"] for item in metricas),
            "preguntas": metricas,
        })

    @action(detail=False, methods=["post"], url_path="coincidencia")
    def coincidencia(self, request):
        question = request.data.get("pregunta", "")
        question = question.strip() if isinstance(question, str) else ""
        if not question or len(question) > 2000:
            return Response({"detail": "Envía una pregunta de hasta 2000 caracteres."}, status=status.HTTP_400_BAD_REQUEST)
        normalized_question = f" {_normalize(question)} "
        matches = []
        rules = RespuestaAutomaticaChatbot.objects.filter(
            activa=True,
            albergue__activo=True,
        ).select_related("albergue")
        for rule in rules.iterator():
            phrases = [rule.pregunta_clave, *(rule.disparadores or [])]
            lengths = [
                len(phrase)
                for phrase in (_normalize(item) for item in phrases if isinstance(item, str))
                if len(phrase) >= 3 and f" {phrase} " in normalized_question
            ]
            if lengths:
                matches.append((max(lengths), rule))

        if not matches:
            return Response({"respuesta": None, "albergue": None})
        longest = max(length for length, _ in matches)
        best = [rule for length, rule in matches if length == longest]
        if len(best) != 1:
            return Response({"respuesta": None, "albergue": None})
        rule = best[0]
        try:
            _registrar_consulta(rule.albergue_id, rule.pregunta_clave)
        except Exception:
            # Analytics must not prevent the chatbot from returning its configured answer.
            logger.exception("No se pudo guardar el conteo agregado de una respuesta automática.")
        return Response({"respuesta": rule.respuesta, "albergue": rule.albergue.nombre})


def _parse_metric_date(value, field, default):
    if value is None:
        return default
    parsed = parse_date(value)
    if parsed is None:
        raise ValidationError({field: "Usa el formato de fecha YYYY-MM-DD."})
    return parsed


def _registrar_consulta(albergue_id, pregunta_clave):
    metric, created = MetricaRespuestaChatbot.objects.get_or_create(
        albergue_id=albergue_id,
        pregunta_clave=pregunta_clave,
        fecha=timezone.localdate(),
        defaults={"consultas": 1},
    )
    if not created:
        MetricaRespuestaChatbot.objects.filter(pk=metric.pk).update(consultas=F("consultas") + 1)
