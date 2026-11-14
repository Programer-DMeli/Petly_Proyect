from django.db import IntegrityError, transaction
from PIL import Image, UnidentifiedImageError
from rest_framework import mixins, permissions, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from apps.acceso.models import SolicitudAdopcion, User
from .models import CasoExitoAdopcion, FotoCasoExito


MAX_FOTOS_CASO = 5
MAX_FOTO_BYTES = 5 * 1024 * 1024
MAX_CASO_FOTOS_BYTES = 20 * 1024 * 1024
MAX_PIXELES_FOTO = 20_000_000


class FotoCasoExitoReadSerializer(serializers.ModelSerializer):
    class Meta:
        model = FotoCasoExito
        fields = ["id", "mime_type", "tamaño_bytes", "subido_en"]
        read_only_fields = fields


class CasoExitoReadSerializer(serializers.ModelSerializer):
    solicitud_id = serializers.IntegerField(read_only=True)
    mascota_nombre = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    albergue_nombre = serializers.CharField(source="solicitud.albergue.nombre", read_only=True)
    estado_display = serializers.CharField(source="get_estado_display", read_only=True)
    fotos = FotoCasoExitoReadSerializer(many=True, read_only=True)

    class Meta:
        model = CasoExitoAdopcion
        fields = [
            "id", "solicitud_id", "mascota_nombre", "albergue_nombre", "historia",
            "estado", "estado_display", "creado_en", "fotos",
        ]
        read_only_fields = fields


class AdopcionElegibleSerializer(serializers.ModelSerializer):
    mascota_nombre = serializers.CharField(source="mascota.nombre", read_only=True)
    albergue_nombre = serializers.CharField(source="albergue.nombre", read_only=True)

    class Meta:
        model = SolicitudAdopcion
        fields = ["id", "mascota_nombre", "albergue_nombre", "fecha_entrega"]
        read_only_fields = fields


class CrearCasoExitoSerializer(serializers.Serializer):
    solicitud_id = serializers.IntegerField(min_value=1)
    historia = serializers.CharField(max_length=4000, trim_whitespace=True)
    fotos = serializers.ListField(
        child=serializers.FileField(), min_length=1, max_length=MAX_FOTOS_CASO,
    )

    def validate_solicitud_id(self, value):
        user = self.context["request"].user
        solicitud = SolicitudAdopcion.objects.filter(
            pk=value,
            adoptante=user,
            estado=SolicitudAdopcion.Estado.ENTREGADA,
        ).first()
        if solicitud is None:
            raise serializers.ValidationError("Selecciona una adopción entregada que te pertenezca.")
        if CasoExitoAdopcion.objects.filter(solicitud=solicitud).exists():
            raise serializers.ValidationError("Ya enviaste una historia para esta adopción.")
        return solicitud

    def validate_fotos(self, archivos):
        total = 0
        for archivo in archivos:
            if archivo.size <= 0 or archivo.size > MAX_FOTO_BYTES:
                raise serializers.ValidationError("Cada foto debe pesar más de 0 y hasta 5 MB.")
            total += archivo.size
            if total > MAX_CASO_FOTOS_BYTES:
                raise serializers.ValidationError("Las fotos juntas no pueden superar 20 MB.")
            try:
                archivo.seek(0)
                with Image.open(archivo) as imagen:
                    if imagen.format not in {"JPEG", "PNG"}:
                        raise ValueError("formato")
                    if imagen.width * imagen.height > MAX_PIXELES_FOTO:
                        raise ValueError("dimensiones")
                    imagen.verify()
                    archivo.petly_mime_type = {
                        "JPEG": "image/jpeg",
                        "PNG": "image/png",
                    }[imagen.format]
            except (UnidentifiedImageError, Image.DecompressionBombError, OSError, ValueError, KeyError) as exc:
                raise serializers.ValidationError(
                    "Sube fotos JPEG o PNG válidas, de hasta 20 megapíxeles."
                ) from exc
            finally:
                archivo.seek(0)
        return archivos


class CasoExitoAdopcionViewSet(mixins.ListModelMixin, mixins.CreateModelMixin, viewsets.GenericViewSet):
    permission_classes = [permissions.IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser]
    http_method_names = ["get", "post", "head", "options"]

    def get_queryset(self):
        if self.request.user.rol != User.Rol.ADOPTANTE:
            return CasoExitoAdopcion.objects.none()
        return CasoExitoAdopcion.objects.filter(autor=self.request.user).select_related(
            "solicitud__mascota", "solicitud__albergue",
        ).prefetch_related("fotos")

    def get_serializer_class(self):
        if self.action == "create":
            return CrearCasoExitoSerializer
        return CasoExitoReadSerializer

    def list(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante puede consultar sus historias.")
        return super().list(request, *args, **kwargs)

    @action(detail=False, methods=["get"], url_path="adopciones-disponibles")
    def adopciones_disponibles(self, request):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante puede consultar sus adopciones.")
        solicitudes = SolicitudAdopcion.objects.filter(
            adoptante=request.user,
            estado=SolicitudAdopcion.Estado.ENTREGADA,
            fecha_entrega__isnull=False,
        ).filter(caso_exito__isnull=True).select_related("mascota", "albergue").order_by("-fecha_entrega", "-id")
        return Response(AdopcionElegibleSerializer(solicitudes, many=True).data)

    def create(self, request, *args, **kwargs):
        if request.user.rol != User.Rol.ADOPTANTE:
            raise PermissionDenied("Solo el adoptante titular puede enviar una historia.")

        serializer = self.get_serializer(data={
            "solicitud_id": request.data.get("solicitud_id"),
            "historia": request.data.get("historia", ""),
            "fotos": request.FILES.getlist("fotos"),
        })
        serializer.is_valid(raise_exception=True)
        solicitud_validada = serializer.validated_data["solicitud_id"]
        archivos_guardados = []
        try:
            with transaction.atomic():
                solicitud = SolicitudAdopcion.objects.select_for_update().filter(
                    pk=solicitud_validada.pk,
                    adoptante=request.user,
                    estado=SolicitudAdopcion.Estado.ENTREGADA,
                    fecha_entrega__isnull=False,
                ).first()
                if solicitud is None:
                    raise serializers.ValidationError("La adopción ya no está disponible para publicar una historia.")
                if CasoExitoAdopcion.objects.filter(solicitud=solicitud).exists():
                    raise serializers.ValidationError("Ya enviaste una historia para esta adopción.")

                caso = CasoExitoAdopcion.objects.create(
                    solicitud=solicitud,
                    autor=request.user,
                    historia=serializer.validated_data["historia"],
                )
                for archivo in serializer.validated_data["fotos"]:
                    foto = FotoCasoExito(
                        caso=caso,
                        mime_type=archivo.petly_mime_type,
                        tamaño_bytes=archivo.size,
                    )
                    foto.archivo.save(archivo.name, archivo, save=False)
                    archivos_guardados.append(foto.archivo.name)
                    foto.save()
        except IntegrityError:
            self._borrar_archivos(archivos_guardados)
            return Response({"detail": "Ya existe una historia para esta adopción."}, status=status.HTTP_409_CONFLICT)
        except Exception:
            self._borrar_archivos(archivos_guardados)
            raise

        caso = self.get_queryset().get(pk=caso.pk)
        return Response(
            CasoExitoReadSerializer(caso, context=self.get_serializer_context()).data,
            status=status.HTTP_201_CREATED,
        )

    @staticmethod
    def _borrar_archivos(nombres):
        storage = FotoCasoExito._meta.get_field("archivo").storage
        for nombre in nombres:
            try:
                storage.delete(nombre)
            except OSError:
                # La historia y sus fotos se guardan juntas; un fallo de limpieza no oculta el error original.
                continue
