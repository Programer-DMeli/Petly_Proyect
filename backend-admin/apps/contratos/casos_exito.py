from django.db import IntegrityError, transaction
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from PIL import Image, UnidentifiedImageError
from rest_framework import mixins, permissions, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.response import Response

from apps.acceso.models import SolicitudAdopcion, User
from config.parametros_sistema import obtener_limite_imagen_bytes
from .models import CasoExitoAdopcion, FotoCasoExito


MAX_FOTOS_CASO = 5
MAX_CASO_FOTOS_BYTES = 20 * 1024 * 1024
MAX_PIXELES_FOTO = 20_000_000


class FotoCasoExitoReadSerializer(serializers.ModelSerializer):
    descarga_url = serializers.SerializerMethodField()

    class Meta:
        model = FotoCasoExito
        fields = ["id", "mime_type", "tamaño_bytes", "subido_en", "descarga_url"]
        read_only_fields = fields

    def get_descarga_url(self, obj):
        return f"/api/contratos/casos-exito/{obj.caso_id}/fotos/{obj.pk}/"


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
            "estado", "estado_display", "motivo_revision", "destacado", "creado_en",
            "publicado_en", "fotos",
        ]
        read_only_fields = fields


class CasoExitoPublicoSerializer(serializers.ModelSerializer):
    albergue_id = serializers.IntegerField(source="solicitud.albergue_id", read_only=True)
    mascota_nombre = serializers.CharField(source="solicitud.mascota.nombre", read_only=True)
    albergue_nombre = serializers.CharField(source="solicitud.albergue.nombre", read_only=True)
    fotos = FotoCasoExitoReadSerializer(many=True, read_only=True)

    class Meta:
        model = CasoExitoAdopcion
        fields = [
            "id", "albergue_id", "mascota_nombre", "albergue_nombre", "historia",
            "publicado_en", "destacado", "fotos",
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
        limite_imagen = obtener_limite_imagen_bytes()
        total = 0
        for archivo in archivos:
            if archivo.size <= 0 or archivo.size > limite_imagen:
                raise serializers.ValidationError(
                    f"Cada foto debe pesar más de 0 y hasta {limite_imagen // (1024 * 1024)} MB, según la configuración global."
                )
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


class RevisionCasoExitoSerializer(serializers.Serializer):
    decision = serializers.ChoiceField(choices=["APROBAR", "RECHAZAR"])
    motivo = serializers.CharField(max_length=500, required=False, allow_blank=True, trim_whitespace=True)

    def validate(self, attrs):
        if attrs["decision"] == "RECHAZAR" and not attrs.get("motivo", "").strip():
            raise serializers.ValidationError({"motivo": "Indica por qué se rechaza la historia."})
        return attrs


class ActualizarDestacadoCasoExitoSerializer(serializers.Serializer):
    destacado = serializers.BooleanField()


class CasosDestacadosQuerySerializer(serializers.Serializer):
    albergue_id = serializers.IntegerField(min_value=1)


class CasosPublicadosQuerySerializer(serializers.Serializer):
    albergue_id = serializers.IntegerField(min_value=1, required=False)


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
        if self.action == "revisar":
            return RevisionCasoExitoSerializer
        if self.action == "destacar":
            return ActualizarDestacadoCasoExitoSerializer
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

    @action(detail=False, methods=["get"], url_path="pendientes")
    def pendientes(self, request):
        if request.user.rol == User.Rol.ADMIN:
            queryset = CasoExitoAdopcion.objects.filter(estado=CasoExitoAdopcion.Estado.PENDIENTE)
        elif request.user.rol == User.Rol.ALBERGUE:
            queryset = CasoExitoAdopcion.objects.filter(
                estado=CasoExitoAdopcion.Estado.PENDIENTE,
                solicitud__albergue__usuario=request.user,
            )
        else:
            raise PermissionDenied("Solo el albergue responsable o ADMIN puede revisar historias.")
        queryset = queryset.select_related("solicitud__mascota", "solicitud__albergue", "autor").prefetch_related("fotos")
        page = self.paginate_queryset(queryset)
        serializer = self.get_serializer(page if page is not None else queryset, many=True)
        if page is not None:
            return self.get_paginated_response(serializer.data)
        return Response(serializer.data)

    @action(
        detail=True, methods=["post"], url_path="revisar",
        parser_classes=[JSONParser, MultiPartParser, FormParser],
    )
    def revisar(self, request, pk=None):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        decision = serializer.validated_data["decision"]
        motivo = serializer.validated_data.get("motivo", "").strip()

        with transaction.atomic():
            caso = get_object_or_404(
                CasoExitoAdopcion.objects.select_for_update().select_related("solicitud__albergue"),
                pk=pk,
            )
            user = request.user
            es_admin = user.rol == User.Rol.ADMIN
            es_albergue_responsable = (
                user.rol == User.Rol.ALBERGUE
                and caso.solicitud.albergue.usuario_id == user.pk
            )
            if not (es_admin or es_albergue_responsable):
                raise NotFound("No se encontró una historia pendiente que puedas revisar.")
            if caso.estado != CasoExitoAdopcion.Estado.PENDIENTE:
                return Response({"detail": "La historia ya fue revisada."}, status=status.HTTP_409_CONFLICT)

            caso.revisado_por = user
            caso.revisado_en = timezone.now()
            caso.motivo_revision = motivo if decision == "RECHAZAR" else ""
            if decision == "APROBAR":
                caso.estado = CasoExitoAdopcion.Estado.APROBADO
                caso.publicado_en = caso.revisado_en
            else:
                caso.estado = CasoExitoAdopcion.Estado.RECHAZADO
                caso.publicado_en = None
            caso.save(update_fields=[
                "estado", "revisado_por", "revisado_en", "motivo_revision", "publicado_en", "actualizado_en",
            ])

        return Response(CasoExitoReadSerializer(caso, context=self.get_serializer_context()).data)

    @action(detail=False, methods=["get"], url_path="publicados", permission_classes=[permissions.AllowAny])
    def publicados(self, request):
        filtros = CasosPublicadosQuerySerializer(data=request.query_params)
        filtros.is_valid(raise_exception=True)
        queryset = CasoExitoAdopcion.objects.filter(
            estado=CasoExitoAdopcion.Estado.APROBADO,
            publicado_en__isnull=False,
        ).select_related("solicitud__mascota", "solicitud__albergue").prefetch_related("fotos")
        albergue_id = filtros.validated_data.get("albergue_id")
        if albergue_id is not None:
            queryset = queryset.filter(solicitud__albergue_id=albergue_id)
        page = self.paginate_queryset(queryset)
        serializer = CasoExitoPublicoSerializer(
            page if page is not None else queryset,
            many=True,
            context=self.get_serializer_context(),
        )
        if page is not None:
            return self.get_paginated_response(serializer.data)
        return Response(serializer.data)

    @action(detail=False, methods=["get"], url_path="destacados", permission_classes=[permissions.AllowAny])
    def destacados(self, request):
        filtros = CasosDestacadosQuerySerializer(data=request.query_params)
        filtros.is_valid(raise_exception=True)
        albergue_id = filtros.validated_data["albergue_id"]
        queryset = CasoExitoAdopcion.objects.filter(
            solicitud__albergue_id=albergue_id,
            estado=CasoExitoAdopcion.Estado.APROBADO,
            publicado_en__isnull=False,
            destacado=True,
            solicitud__albergue__activo=True,
        ).select_related("solicitud__mascota", "solicitud__albergue").prefetch_related("fotos")
        page = self.paginate_queryset(queryset)
        serializer = CasoExitoPublicoSerializer(
            page if page is not None else queryset,
            many=True,
            context=self.get_serializer_context(),
        )
        if page is not None:
            return self.get_paginated_response(serializer.data)
        return Response(serializer.data)

    @action(
        detail=True, methods=["post"], url_path="destacar",
        parser_classes=[JSONParser, MultiPartParser, FormParser],
    )
    def destacar(self, request, pk=None):
        if request.user.rol != User.Rol.ALBERGUE:
            raise PermissionDenied("Solo el albergue propietario puede destacar testimonios.")

        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        destacado = serializer.validated_data["destacado"]

        with transaction.atomic():
            caso = get_object_or_404(
                CasoExitoAdopcion.objects.select_for_update().filter(
                    solicitud__albergue__usuario=request.user,
                ),
                pk=pk,
            )
            if (
                caso.estado != CasoExitoAdopcion.Estado.APROBADO
                or caso.publicado_en is None
            ):
                return Response(
                    {"detail": "Solo puedes destacar historias aprobadas y publicadas."},
                    status=status.HTTP_409_CONFLICT,
                )

            caso.destacado = destacado
            caso.save(update_fields=["destacado", "actualizado_en"])

        return Response({"id": caso.pk, "destacado": caso.destacado})

    @action(
        detail=True,
        methods=["get"],
        url_path=r"fotos/(?P<foto_id>[0-9]+)",
        permission_classes=[permissions.AllowAny],
    )
    def foto(self, request, pk=None, foto_id=None):
        caso = get_object_or_404(
            CasoExitoAdopcion.objects.select_related("solicitud__albergue"),
            pk=pk,
        )
        publicada = (
            caso.estado == CasoExitoAdopcion.Estado.APROBADO
            and caso.publicado_en is not None
        )
        if not publicada:
            user = request.user
            puede_ver = user.is_authenticated and (
                user.rol == User.Rol.ADMIN
                or caso.autor_id == user.pk
                or (
                    user.rol == User.Rol.ALBERGUE
                    and caso.solicitud.albergue.usuario_id == user.pk
                )
            )
            if not puede_ver:
                raise NotFound("No se encontró la fotografía.")

        foto = get_object_or_404(FotoCasoExito, pk=foto_id, caso=caso)
        try:
            archivo = foto.archivo.open("rb")
        except (OSError, ValueError):
            raise NotFound("No se pudo abrir la fotografía.")
        response = FileResponse(archivo, content_type=foto.mime_type)
        response["X-Content-Type-Options"] = "nosniff"
        response["Cache-Control"] = "public, max-age=300" if publicada else "private, no-store"
        return response

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
