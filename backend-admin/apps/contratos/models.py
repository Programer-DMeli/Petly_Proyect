import uuid

from django.conf import settings
from django.db import models

from .storage import PrivateContractStorage


class ContratoAdopcion(models.Model):
    """Versión inmutable del documento generado y su estado en el proveedor."""

    class Estado(models.TextChoices):
        BORRADOR = "BORRADOR", "Borrador"
        PENDIENTE_FIRMA = "PENDIENTE_FIRMA", "Pendiente de envío"
        EN_FIRMA = "EN_FIRMA", "En firma"
        FIRMADO = "FIRMADO", "Firmado"
        RECHAZADO = "RECHAZADO", "Rechazado"
        EXPIRADO = "EXPIRADO", "Expirado"
        ERROR = "ERROR", "Error de integración"

    public_id = models.UUIDField(default=uuid.uuid4, unique=True, editable=False)
    solicitud = models.OneToOneField(
        "acceso.SolicitudAdopcion",
        on_delete=models.PROTECT,
        related_name="contrato_adopcion",
    )
    estado = models.CharField(max_length=20, choices=Estado.choices, default=Estado.BORRADOR, db_index=True)
    proveedor = models.CharField(max_length=80, blank=True)
    proveedor_documento_id = models.CharField(max_length=160, null=True, blank=True, unique=True)
    url_firma = models.URLField(max_length=1000, blank=True)
    contenido_html = models.TextField()
    contenido_sha256 = models.CharField(max_length=64, db_index=True)
    documento_firmado = models.FileField(
        upload_to="contratos/firmados/", storage=PrivateContractStorage(), null=True, blank=True
    )
    snapshot = models.JSONField(default=dict)
    creado_por = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name="contratos_generados",
    )
    generado_en = models.DateTimeField(auto_now_add=True)
    enviado_en = models.DateTimeField(null=True, blank=True)
    firmado_en = models.DateTimeField(null=True, blank=True)
    expira_en = models.DateTimeField(null=True, blank=True)
    error_integracion = models.CharField(max_length=500, blank=True)
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-creado_en"]
        indexes = [models.Index(fields=["estado", "creado_en"])]

    def __str__(self):
        return f"Contrato {self.public_id} · solicitud {self.solicitud_id} · {self.get_estado_display()}"


class EventoWebhookFirma(models.Model):
    """Recibo idempotente de un evento autenticado enviado por el proveedor."""

    contrato = models.ForeignKey(ContratoAdopcion, on_delete=models.PROTECT, related_name="webhooks")
    proveedor = models.CharField(max_length=80)
    evento_id = models.CharField(max_length=160)
    estado_recibido = models.CharField(max_length=32)
    payload_sha256 = models.CharField(max_length=64)
    recibido_en = models.DateTimeField(auto_now_add=True)
    procesado_en = models.DateTimeField(null=True, blank=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["proveedor", "evento_id"], name="uniq_signature_provider_event")]
        ordering = ["recibido_en"]
