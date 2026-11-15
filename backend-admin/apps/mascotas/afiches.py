"""Cola de afiches de adopción, con una reserva por intento."""
import hashlib
import re
from datetime import timedelta
from uuid import uuid4

from django.conf import settings
from django.core.exceptions import ValidationError as DjangoValidationError
from django.core.validators import validate_email
from django.core.files.storage import FileSystemStorage
from django.db import connection, transaction
from django.db.models import Q
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.urls import reverse
from django.utils.crypto import constant_time_compare
from PIL import Image
from rest_framework import serializers
from rest_framework.exceptions import NotFound, ValidationError
from rest_framework.permissions import AllowAny, BasePermission, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Mascota, PedidoAfiche

Estado = PedidoAfiche.Estado
MAX_INTENTOS = 3
MAX_FONDO_BYTES = 12 * 1024 * 1024
ERRORES = ("SESION", "CUPO", "PLAZO", "PNG_INVALIDO", "FOTO", "GENERACION", "ENTREGA", "RESERVA_VENCIDA")


def version_foto(foto):
    if not foto:
        raise ValidationError({"foto_principal": "Añade una foto principal."})
    try:
        with foto.storage.open(foto.name, "rb") as archivo:
            with Image.open(archivo) as imagen:
                imagen.verify()
            archivo.seek(0)
            return hashlib.file_digest(archivo, "sha256").hexdigest()
    except FileNotFoundError as exc:
        raise ValidationError({"foto_principal": "La foto principal no está disponible."}) from exc
    except (OSError, SyntaxError, Image.DecompressionBombError) as exc:
        raise ValidationError({"foto_principal": "La foto principal no es una imagen válida."}) from exc


def datos_publicos(albergue):
    telefono = albergue.telefono.strip()
    if not re.fullmatch(r"\+?[0-9 ().-]+", telefono) or not 7 <= sum(c.isdigit() for c in telefono) <= 15:
        telefono = ""
    correo = albergue.email.strip()
    try:
        validate_email(correo)
    except DjangoValidationError:
        correo = ""
    return {"nombre": albergue.nombre.strip(), "telefono": telefono, "email": correo}


def foto_url(pedido, request):
    almacenamiento = Mascota._meta.get_field("foto_principal").storage
    return request.build_absolute_uri(almacenamiento.url(pedido.foto_ruta))


def almacenamiento_afiches():
    return FileSystemStorage(location=settings.PRIVATE_FILES_ROOT)


class PedidoAficheSerializer(serializers.ModelSerializer):
    foto_url = serializers.SerializerMethodField()
    fondo_url = serializers.SerializerMethodField()

    class Meta:
        model = PedidoAfiche
        fields = (
            "id", "mascota", "albergue", "foto_version", "foto_url", "datos_afiche",
            "estado", "intentos", "reserva_vence_en", "ruta_resultado",
            "fondo_url", "error_codigo", "creado_en",
        )

    def get_foto_url(self, pedido):
        return foto_url(pedido, self.context["request"])

    def get_fondo_url(self, pedido):
        return reverse("afiche-fondo", args=[pedido.pk]) if pedido.ruta_resultado else None


def datos_pedido(pedido, request):
    return PedidoAficheSerializer(pedido, context={"request": request}).data


class EsAlbergue(BasePermission):
    def has_permission(self, request, view):
        return request.user.is_active and request.user.rol == "ALBERGUE"


class CrearPedidoAfiche(APIView):
    permission_classes = [IsAuthenticated, EsAlbergue]

    @transaction.atomic
    def post(self, request, mascota_id):
        # La fila de la mascota existe incluso antes del primer pedido.
        mascota = get_object_or_404(
            Mascota.objects.select_for_update().select_related("albergue"),
            pk=mascota_id, albergue__usuario=request.user, albergue__activo=True,
        )
        albergue = mascota.albergue
        datos = datos_publicos(albergue)
        if not datos["nombre"]:
            raise ValidationError({"albergue": "Completa el nombre público del albergue."})
        if not (datos["telefono"] or datos["email"]):
            raise ValidationError({"contacto": "Completa un teléfono o correo público válido del albergue."})
        version = version_foto(mascota.foto_principal)
        pedido = PedidoAfiche.objects.select_for_update().filter(
            mascota=mascota, albergue=albergue, foto_version=version,
            estado__in=[Estado.PENDIENTE, Estado.RESERVADO],
        ).order_by("pk").first()
        if pedido:
            return Response(datos_pedido(pedido, request))
        pedido = PedidoAfiche.objects.create(
            mascota=mascota, albergue=albergue, foto_ruta=mascota.foto_principal.name,
            foto_version=version, datos_afiche=datos,
        )
        return Response(datos_pedido(pedido, request), status=201)


class ConsultarPedidoAfiche(APIView):
    permission_classes = [IsAuthenticated, EsAlbergue]

    def get(self, request, pedido_id):
        pedido = get_object_or_404(
            PedidoAfiche, pk=pedido_id, albergue__usuario=request.user,
        )
        return Response(datos_pedido(pedido, request))


class FondoPedidoAfiche(APIView):
    permission_classes = [IsAuthenticated, EsAlbergue]

    def get(self, request, pedido_id):
        pedido = get_object_or_404(
            PedidoAfiche, pk=pedido_id, albergue__usuario=request.user, estado=Estado.COMPLETADO,
        )
        try:
            fondo = almacenamiento_afiches().open(pedido.ruta_resultado, "rb")
        except FileNotFoundError as exc:
            raise NotFound() from exc
        return FileResponse(fondo, content_type="image/png", headers={"Cache-Control": "private, no-store"})


def liberar(pedido, estado, ruta_resultado="", error_codigo=""):
    pedido.error_codigo = error_codigo
    pedido.estado = estado
    pedido.ruta_resultado = ruta_resultado
    pedido.reserva = None
    pedido.reserva_vence_en = None
    pedido.save(update_fields=["estado", "ruta_resultado", "reserva", "reserva_vence_en", "error_codigo"])


def tomar_pedido():
    while True:
        with transaction.atomic():
            disponibles = PedidoAfiche.objects.filter(
                Q(estado=Estado.PENDIENTE) | Q(estado=Estado.RESERVADO, reserva_vence_en__lte=timezone.now()),
            ).order_by("pk")
            pedido = disponibles.select_for_update(
                skip_locked=connection.features.has_select_for_update_skip_locked,
            ).first()
            if pedido is None:
                return None
            if pedido.intentos >= MAX_INTENTOS:
                liberar(pedido, Estado.FALLIDO, error_codigo="RESERVA_VENCIDA")
                continue

            pedido.estado = Estado.RESERVADO
            pedido.intentos += 1
            pedido.reserva = uuid4()
            pedido.reserva_vence_en = timezone.now() + timedelta(seconds=settings.AFICHE_RESERVA_SEGUNDOS)
            pedido.save(update_fields=["estado", "intentos", "reserva", "reserva_vence_en"])
            return pedido


def ruta_del_intento(pedido):
    return f"afiches/{pedido.pk}/{pedido.reserva}.png"


class OperacionTrabajador(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = []

    def post(self, request, **kwargs):
        secreto = settings.AFICHE_WORKER_TOKEN
        if not secreto:
            return Response({"detail": "El trabajador de afiches no está configurado."}, status=503)
        if not constant_time_compare(request.headers.get("X-Afiche-Worker-Token", ""), secreto):
            return Response({"detail": "Credenciales de trabajador inválidas."}, status=401)
        return self.operar(request, **kwargs)


class TomarPedidoAfiche(OperacionTrabajador):
    def operar(self, request):
        pedido = tomar_pedido()
        if pedido is None:
            return Response(status=204)
        return Response({
            **datos_pedido(pedido, request),
            "reserva": str(pedido.reserva),
            "ruta_destino": ruta_del_intento(pedido),
        })


class EntregaAficheSerializer(serializers.Serializer):
    reserva = serializers.UUIDField()
    estado = serializers.ChoiceField(choices=[Estado.COMPLETADO, Estado.FALLIDO])
    fondo = serializers.FileField(required=False)
    error_codigo = serializers.ChoiceField(choices=ERRORES, required=False)
    reintentar = serializers.BooleanField(default=True)

    def validate(self, datos):
        if datos["estado"] == Estado.COMPLETADO:
            fondo = datos.get("fondo")
            if not fondo or fondo.size > MAX_FONDO_BYTES:
                raise ValidationError({"fondo": "Sube un PNG de hasta 12 MB."})
            try:
                with Image.open(fondo) as imagen:
                    if imagen.format != "PNG" or imagen.width * 5 != imagen.height * 4 or imagen.height > 4096:
                        raise ValueError("Formato o dimensiones no válidos")
                    imagen.verify()
                fondo.seek(0)
                with Image.open(fondo) as imagen:
                    imagen.load()
            except (OSError, SyntaxError, ValueError, Image.DecompressionBombError) as exc:
                raise ValidationError({"fondo": "El fondo debe ser un PNG válido de proporción 4:5."}) from exc
            fondo.seek(0)
        return datos


class EntregarPedidoAfiche(OperacionTrabajador):
    @transaction.atomic
    def operar(self, request, pedido_id):
        entrada = EntregaAficheSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        datos = entrada.validated_data
        # Comprobar la reserva bajo el bloqueo antes de guardar el archivo.
        pedido = get_object_or_404(PedidoAfiche.objects.select_for_update(), pk=pedido_id)
        if (
            pedido.estado != Estado.RESERVADO
            or pedido.reserva != datos["reserva"]
            or pedido.reserva_vence_en <= timezone.now()
        ):
            return Response({"detail": "La reserva ya no está vigente."}, status=409)

        if datos["estado"] == Estado.COMPLETADO:
            storage = almacenamiento_afiches()
            destino = ruta_del_intento(pedido)
            storage.delete(destino)  # Limpia un archivo huérfano de la misma reserva.
            ruta = storage.save(destino, datos["fondo"])
            try:
                liberar(pedido, Estado.COMPLETADO, ruta)
            except Exception:
                storage.delete(ruta)
                raise
        else:
            reintentar = datos["reintentar"] and pedido.intentos < MAX_INTENTOS
            liberar(pedido, Estado.PENDIENTE if reintentar else Estado.FALLIDO,
                    error_codigo=datos.get("error_codigo", "GENERACION"))
        return Response(datos_pedido(pedido, request))
