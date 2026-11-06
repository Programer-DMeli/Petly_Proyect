from django.apps import AppConfig


class AccesoConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.acceso"
    label = "acceso"
    verbose_name = "Acceso"

    def ready(self):
        from . import avisos_mascotas  # noqa: F401
