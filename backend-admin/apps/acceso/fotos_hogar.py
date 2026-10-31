"""Fotos privadas: nunca se sirven mediante MEDIA_URL."""
import hashlib
import io
import uuid
from PIL import Image, UnidentifiedImageError
from django.db import transaction
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from rest_framework.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response
from .models import SolicitudAdopcion, FotoHogar
from .eligibilidad import comprobar_elegibilidad


def solicitud_autorizada(user, pk, escritura=False):
    solicitud = get_object_or_404(SolicitudAdopcion.objects.select_related("adoptante", "albergue"), pk=pk)
    titular = solicitud.adoptante_id == user.pk and user.rol == "ADOPTANTE"
    if escritura and not titular:
        raise PermissionDenied("Solo el titular puede adjuntar fotografías.")
    if not titular:
        if user.rol != "ALBERGUE" or solicitud.albergue.usuario_id != user.pk or not solicitud.adoptante:
            raise PermissionDenied("No tienes acceso a estas fotografías.")
        try:
            comprobar_elegibilidad(solicitud.adoptante, solicitud.albergue_id)
        except ValidationError:
            raise PermissionDenied("La autorización para consultar estas fotos ya no está vigente.")
    return solicitud


class FotosHogarView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, pk):
        solicitud = solicitud_autorizada(request.user, pk)
        return Response([{"id": f.pk, "url": f"/api/acceso/solicitudes/{pk}/fotos-hogar/{f.pk}/", "tipo": f.tipo}
                         for f in solicitud.fotos_hogar.order_by("id")])

    def post(self, request, pk):
        solicitud_autorizada(request.user, pk, escritura=True)
        archivo = request.FILES.get("archivo")
        if not archivo or archivo.size > 5 * 1024 * 1024:
            raise ValidationError({"archivo": "Selecciona un JPEG/PNG de hasta 5 MB."})
        contenido = archivo.read()
        try:
            with Image.open(io.BytesIO(contenido)) as imagen:
                tipo = imagen.format
                if tipo not in ("JPEG", "PNG"):
                    raise ValueError()
                imagen.verify()
        except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
            raise ValidationError({"archivo": "La imagen JPEG/PNG no es válida."})
        digest = hashlib.sha256(contenido).hexdigest()
        with transaction.atomic():
            solicitud = SolicitudAdopcion.objects.select_for_update().get(pk=pk)
            existente = FotoHogar.objects.filter(solicitud=solicitud, sha256=digest).first()
            if existente:
                return Response({"id": existente.pk}, status=200)
            if solicitud.estado not in ("PENDIENTE", "OBSERVADA"):
                raise ValidationError({"solicitud": "La solicitud ya no admite fotografías."})
            if solicitud.fotos_hogar.count() >= 3:
                raise ValidationError({"archivo": "Máximo tres fotografías por solicitud."})
            from django.core.files.base import ContentFile
            foto = FotoHogar(solicitud=solicitud, sha256=digest, tipo=tipo)
            try:
                foto.archivo.save(uuid.uuid4().hex + (".png" if tipo == "PNG" else ".jpg"), ContentFile(contenido), save=False)
                foto.save()
            except BaseException:
                if foto.archivo.name:
                    foto.archivo.delete(save=False)
                raise
        return Response({"id": foto.pk}, status=201)


class FotoHogarView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, pk, foto_id):
        solicitud_autorizada(request.user, pk)
        foto = get_object_or_404(FotoHogar, solicitud_id=pk, pk=foto_id)
        response = FileResponse(foto.archivo.open("rb"), content_type="image/png" if foto.tipo == "PNG" else "image/jpeg")
        response["Cache-Control"] = "private, no-store"
        response["X-Content-Type-Options"] = "nosniff"
        return response
