from django.db import IntegrityError, transaction
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from PIL import Image, UnidentifiedImageError
from rest_framework import mixins, permissions, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from apps.acceso.models import SolicitudAdopcion, User
from .models import FotoReporteBienestar, ReporteBienestarPostAdopcion, SeguimientoPostAdopcion


MAX_REPORT_PHOTOS = 5
MAX_PHOTO_BYTES = 5 * 1024 * 1024
MAX_REPORT_PHOTOS_BYTES = 20 * 1024 * 1024
MAX_IMAGE_PIXELS = 20_000_000


class FotoReporteBienestarSerializer(serializers.ModelSerializer):
    descarga_url = serializers.SerializerMethodField()

    class Meta:
        model = FotoReporteBienestar
        fields = ["id", "mime_type", "tamaño_bytes", "subido_en", "descarga_url"]
        read_only_fields = fields

    def get_descarga_url(self, obj):
        return f"/api/contratos/reportes-seguimiento/{obj.reporte_id}/fotos/{obj.pk}/"


class ReporteBienestarReadSerializer(serializers.ModelSerializer):
    seguimiento_id = serializers.IntegerField(read_only=True)
    fotos = FotoReporteBienestarSerializer(many=True, read_only=True)
    estado_bienestar_display = serializers.CharField(source="get_estado_bienestar_display", read_only=True)

    class Meta:
        model = ReporteBienestarPostAdopcion
        fields = ["id", "seguimiento_id", "estado_bienestar", "estado_bienestar_display",
                  "observaciones", "enviado_en", "fotos"]
        read_only_fields = fields


class CrearReporteBienestarSerializer(serializers.Serializer):
    seguimiento_id = serializers.IntegerField(min_value=1)
    estado_bienestar = serializers.ChoiceField(choices=ReporteBienestarPostAdopcion.EstadoBienestar.choices)
    observaciones = serializers.CharField(max_length=2000, required=False, allow_blank=True)
    fotos = serializers.ListField(
        child=serializers.FileField(), min_length=1, max_length=MAX_REPORT_PHOTOS,
    )

    def validate_seguimiento_id(self, value):
        user = self.context["request"].user
        seguimiento = SeguimientoPostAdopcion.objects.select_related(
            "solicitud", "solicitud__adoptante", "solicitud__albergue"
        ).filter(
            pk=value,
            solicitud__adoptante=user,
            solicitud__estado=SolicitudAdopcion.Estado.ENTREGADA,
        ).exclude(estado=SeguimientoPostAdopcion.Estado.CANCELADO).first()
        if seguimiento is None:
            raise serializers.ValidationError("El seguimiento no pertenece a una adopción activa tuya.")
        if hasattr(seguimiento, "reporte_bienestar"):
            raise serializers.ValidationError("Ya enviaste un reporte para este seguimiento.")
        if hasattr(seguimiento.solicitud, "expediente_post_adopcion"):
            raise serializers.ValidationError("El expediente de esta adopción ya está cerrado.")
        return seguimiento

    def validate_fotos(self, files):
        total = 0
        for upload in files:
            if upload.size <= 0 or upload.size > MAX_PHOTO_BYTES:
                raise serializers.ValidationError("Cada foto debe pesar más de 0 y hasta 5 MB.")
            total += upload.size
            if total > MAX_REPORT_PHOTOS_BYTES:
                raise serializers.ValidationError("El conjunto de fotos no puede superar 20 MB.")
            try:
                upload.seek(0)
                with Image.open(upload) as image:
                    if image.format not in {"JPEG", "PNG"}:
                        raise ValueError("formato")
                    if image.width * image.height > MAX_IMAGE_PIXELS:
                        raise ValueError("dimensiones")
                    image.verify()
                    mime_type = {"JPEG": "image/jpeg", "PNG": "image/png"}[image.format]
            except (UnidentifiedImageError, Image.DecompressionBombError, OSError, ValueError, KeyError) as exc:
                raise serializers.ValidationError("Sube fotos JPEG o PNG válidas, de hasta 20 megapíxeles.") from exc
            upload.seek(0)
            upload.petly_mime_type = mime_type
        return files


class ReporteBienestarPostAdopcionViewSet(
    mixins.ListModelMixin,
    mixins.CreateModelMixin,
    viewsets.GenericViewSet,
):
    permission_classes = [permissions.IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser]
    http_method_names = ["get", "post", "head", "options"]

    def get_queryset(self):
        user = self.request.user
        queryset = ReporteBienestarPostAdopcion.objects.select_related(
            "seguimiento__solicitud__mascota", "seguimiento__solicitud__adoptante",
            "seguimiento__solicitud__albergue",
        ).prefetch_related("fotos")
        if user.rol == User.Rol.ADOPTANTE:
            return queryset.filter(seguimiento__solicitud__adoptante=user)
        if user.rol == User.Rol.ALBERGUE:
            return queryset.filter(seguimiento__solicitud__albergue__usuario=user)
        return queryset.none()

    def get_serializer_class(self):
        if self.action == "create":
            return CrearReporteBienestarSerializer
        return ReporteBienestarReadSerializer

    def list(self, request, *args, **kwargs):
        if request.user.rol not in {User.Rol.ADOPTANTE, User.Rol.ALBERGUE}:
            raise PermissionDenied("Este perfil no puede consultar reportes postadopción.")
        return super().list(request, *args, **kwargs)

    def create(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante titular puede enviar el reporte.")
        files = request.FILES.getlist("fotos")
        serializer = self.get_serializer(data={
            "seguimiento_id": request.data.get("seguimiento_id"),
            "estado_bienestar": request.data.get("estado_bienestar"),
            "observaciones": request.data.get("observaciones", ""),
            "fotos": files,
        })
        serializer.is_valid(raise_exception=True)
        seguimiento = serializer.validated_data["seguimiento_id"]
        try:
            with transaction.atomic():
                reporte = ReporteBienestarPostAdopcion.objects.create(
                    seguimiento=seguimiento,
                    estado_bienestar=serializer.validated_data["estado_bienestar"],
                    observaciones=serializer.validated_data.get("observaciones", "").strip(),
                )
                saved_names = []
                try:
                    for upload in serializer.validated_data["fotos"]:
                        foto = FotoReporteBienestar(
                            reporte=reporte,
                            mime_type=upload.petly_mime_type,
                            tamaño_bytes=upload.size,
                        )
                        foto.archivo.save(upload.name, upload, save=False)
                        saved_names.append(foto.archivo.name)
                        foto.save()
                except Exception:
                    for name in saved_names:
                        foto.archivo.storage.delete(name)
                    raise
        except IntegrityError:
            return Response({"detail": "Ya existe un reporte para este seguimiento."}, status=409)
        return Response(
            ReporteBienestarReadSerializer(reporte, context=self.get_serializer_context()).data,
            status=status.HTTP_201_CREATED,
        )

    @action(detail=True, methods=["get"], url_path=r"fotos/(?P<foto_id>[0-9]+)")
    def foto(self, request, pk=None, foto_id=None):
        if request.user.rol not in {User.Rol.ADOPTANTE, User.Rol.ALBERGUE}:
            raise PermissionDenied("Este perfil no puede consultar fotos de seguimiento.")
        reporte = self.get_object()
        foto = get_object_or_404(FotoReporteBienestar, pk=foto_id, reporte=reporte)
        try:
            archivo = foto.archivo.open("rb")
        except (OSError, ValueError):
            return Response({"detail": "No se pudo abrir la foto del reporte."}, status=404)
        response = FileResponse(archivo, content_type=foto.mime_type)
        response["Cache-Control"] = "private, no-store"
        response["X-Content-Type-Options"] = "nosniff"
        return response
