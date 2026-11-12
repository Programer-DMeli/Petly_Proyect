"""Vinculación de FCM a una cuenta autenticada y revocación por instalación."""
import hashlib

from django.db import transaction
from rest_framework import permissions, serializers
from rest_framework.exceptions import PermissionDenied
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import DispositivoPush, User


def huella(valor):
    return hashlib.sha256(valor.encode()).hexdigest()


class Credencial(serializers.Serializer):
    clave = serializers.RegexField(r"^[0-9a-f]{64}\Z", max_length=64)


class Registro(Credencial):
    token = serializers.RegexField(r"^[A-Za-z0-9_:.\-]+\Z", max_length=4096)


def actualizar_token(dispositivo, token):
    token_hash = huella(token)
    # Un token FCM sólo puede estar activo para una cuenta a la vez.
    DispositivoPush.objects.filter(token_hash=token_hash).exclude(pk=dispositivo.pk).update(
        token="", token_hash=None, revocado=True,
    )
    dispositivo.token = token
    dispositivo.token_hash = token_hash
    dispositivo.save(update_fields=["token", "token_hash"])


class VincularPushView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    @transaction.atomic
    def put(self, request):
        if request.user.rol != User.Rol.ADOPTANTE or not request.user.usuario_spring_id:
            raise PermissionDenied()
        datos = Registro(data=request.data)
        datos.is_valid(raise_exception=True)
        dispositivo, _ = DispositivoPush.objects.select_for_update().get_or_create(
            pk=huella(datos.validated_data["clave"]), defaults={"adoptante": request.user},
        )
        if dispositivo.revocado or dispositivo.adoptante_id != request.user.pk:
            raise PermissionDenied()
        actualizar_token(dispositivo, datos.validated_data["token"])
        return Response(status=204)


class GestionarPushView(APIView):
    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    @transaction.atomic
    def post(self, request, operacion):
        datos = (Registro if operacion == "rotar" else Credencial)(data=request.data)
        datos.is_valid(raise_exception=True)
        clave = huella(datos.validated_data["clave"])
        if operacion == "revocar":
            dispositivo, _ = DispositivoPush.objects.select_for_update().get_or_create(
                pk=clave, defaults={"revocado": True},
            )
            DispositivoPush.objects.filter(pk=dispositivo.pk).update(
                adoptante=None, token="", token_hash=None, revocado=True,
            )
        else:
            dispositivo = DispositivoPush.objects.select_for_update().filter(pk=clave, revocado=False).first()
            if dispositivo is None:
                return Response(status=404)
            actualizar_token(dispositivo, datos.validated_data["token"])
        return Response(status=204)
