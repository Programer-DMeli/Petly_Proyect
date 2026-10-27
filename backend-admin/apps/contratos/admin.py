from django.contrib import admin

from .models import AprobacionContratoCorreo, ContratoAdopcion, EventoWebhookFirma


@admin.register(ContratoAdopcion)
class ContratoAdopcionAdmin(admin.ModelAdmin):
    list_display = ("public_id", "solicitud", "estado", "proveedor", "generado_en", "enviado_en", "aprobado_en", "firmado_en")
    list_filter = ("estado", "proveedor", "generado_en")
    search_fields = ("=public_id", "solicitud__postulante__documento_numero", "proveedor_documento_id")
    readonly_fields = tuple(field.name for field in ContratoAdopcion._meta.fields)
    ordering = ("-creado_en",)

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(EventoWebhookFirma)
class EventoWebhookFirmaAdmin(admin.ModelAdmin):
    list_display = ("proveedor", "evento_id", "contrato", "estado_recibido", "recibido_en", "procesado_en")
    list_filter = ("proveedor", "estado_recibido", "recibido_en")
    search_fields = ("evento_id", "contrato__public_id")
    readonly_fields = tuple(field.name for field in EventoWebhookFirma._meta.fields)

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(AprobacionContratoCorreo)
class AprobacionContratoCorreoAdmin(admin.ModelAdmin):
    exclude = ("token_sha256",)
    list_display = ("contrato", "correo", "estado", "expira_en", "intentos_fallidos", "aprobado_por_albergue", "consumido_en")
    list_filter = ("estado", "expira_en")
    search_fields = ("correo", "contrato__public_id")
    readonly_fields = tuple(field.name for field in AprobacionContratoCorreo._meta.fields)

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
