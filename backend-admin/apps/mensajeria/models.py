import os
import uuid

from django.conf import settings
from django.core.files.storage import FileSystemStorage
from django.db import models


class PrivateMessageStorage(FileSystemStorage):
    """Message files are served only by an authenticated, participant-checked view."""
    def __init__(self):
        super().__init__(location=settings.PRIVATE_FILES_ROOT / "mensajeria", base_url=None,
                         file_permissions_mode=0o600, directory_permissions_mode=0o700)

    def url(self, name):
        raise ValueError("Los adjuntos privados no tienen una URL pública.")


def private_message_upload_path(instance, filename):
    extension = os.path.splitext(filename)[1].lower()
    return f"{instance.mensaje.conversacion_id}/{uuid.uuid4().hex}{extension}"


class BienvenidaAlbergue(models.Model):
    albergue = models.OneToOneField("albergues.Albergue", on_delete=models.CASCADE, related_name="bienvenida_chat")
    texto = models.CharField(max_length=1000)
    actualizado_en = models.DateTimeField(auto_now=True)


class Conversacion(models.Model):
    solicitud = models.OneToOneField("acceso.SolicitudAdopcion", on_delete=models.PROTECT, related_name="conversacion")
    adoptante = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="conversaciones_adoptante")
    albergue = models.ForeignKey("albergues.Albergue", on_delete=models.PROTECT, related_name="conversaciones")
    creada_en = models.DateTimeField(auto_now_add=True)
    actualizada_en = models.DateTimeField(auto_now=True, db_index=True)

    class Meta:
        ordering = ["-actualizada_en", "-id"]


class ParticipanteConversacion(models.Model):
    conversacion = models.ForeignKey(Conversacion, on_delete=models.CASCADE, related_name="participantes")
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="participaciones_chat")
    leido_hasta = models.DateTimeField(null=True, blank=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["conversacion", "usuario"], name="chat_participante_unico")]


class Mensaje(models.Model):
    conversacion = models.ForeignKey(Conversacion, on_delete=models.CASCADE, related_name="mensajes")
    remitente = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="mensajes_chat")
    texto = models.TextField(max_length=4000, blank=True)
    bienvenida_automatica = models.BooleanField(default=False)
    creado_en = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        ordering = ["creado_en", "id"]


class AdjuntoMensaje(models.Model):
    mensaje = models.ForeignKey(Mensaje, on_delete=models.CASCADE, related_name="adjuntos")
    archivo = models.FileField(upload_to=private_message_upload_path, storage=PrivateMessageStorage())
    nombre_original = models.CharField(max_length=255)
    content_type = models.CharField(max_length=100)
    tamaño_bytes = models.PositiveIntegerField()


class BloqueoConversacion(models.Model):
    conversacion = models.ForeignKey(Conversacion, on_delete=models.CASCADE, related_name="bloqueos")
    bloqueador = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="bloqueos_chat")
    motivo = models.CharField(max_length=500, blank=True)
    creado_en = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["conversacion", "bloqueador"], name="chat_bloqueo_unico")]


class ReporteConversacion(models.Model):
    class Motivo(models.TextChoices):
        ACOSO = "ACOSO", "Acoso o amenazas"
        FRAUDE = "FRAUDE", "Fraude o suplantación"
        CONTENIDO = "CONTENIDO", "Contenido inapropiado"
        OTRO = "OTRO", "Otro motivo"

    conversacion = models.ForeignKey(Conversacion, on_delete=models.PROTECT, related_name="reportes")
    reportante = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="reportes_chat")
    motivo = models.CharField(max_length=12, choices=Motivo.choices)
    detalle = models.CharField(max_length=1000, blank=True)
    creado_en = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-creado_en"]
