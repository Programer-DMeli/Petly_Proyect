import secrets

from django.conf import settings
from django.contrib.auth import get_user_model
from django.db import transaction
from rest_framework import permissions, serializers, status
from rest_framework.response import Response
from rest_framework.views import APIView


class SincronizarRolSerializer(serializers.Serializer):
    usuario_spring_id = serializers.IntegerField(min_value=1)
    correo = serializers.EmailField()
    rol = serializers.ChoiceField(choices=("ADMIN", "ALBERGUE", "ADOPTANTE"))


class SincronizarRolInternoView(APIView):
    """Sincroniza la identidad local a partir del rol autoritativo de Spring."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def put(self, request):
        secreto_configurado = settings.INTERNAL_API_SECRET
        secreto_recibido = request.headers.get("X-Internal-Secret", "")
        if not secreto_configurado:
            return Response(
                {"detail": "La integración interna de roles no está configurada."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        if not secreto_recibido or not secrets.compare_digest(secreto_recibido, secreto_configurado):
            return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)

        serializer = SincronizarRolSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        identidad = serializer.validated_data
        user_model = get_user_model()

        with transaction.atomic():
            usuario, creado = user_model.objects.select_for_update().get_or_create(
                usuario_spring_id=identidad["usuario_spring_id"],
                defaults={
                    "username": f"spring_{identidad['usuario_spring_id']}",
                    "email": identidad["correo"],
                    "rol": identidad["rol"],
                    "is_active": True,
                },
            )
            if usuario.is_staff or usuario.is_superuser:
                return Response(
                    {"detail": "La identidad está vinculada a una cuenta técnica."},
                    status=status.HTTP_409_CONFLICT,
                )

            usuario.email = identidad["correo"]
            usuario.rol = identidad["rol"]
            campos_actualizados = ["email", "rol"]
            if creado or usuario.has_usable_password():
                usuario.set_unusable_password()
                campos_actualizados.append("password")
            usuario.save(update_fields=campos_actualizados)

        return Response({
            "usuario_spring_id": usuario.usuario_spring_id,
            "rol": usuario.rol,
            "sincronizado": True,
        })
