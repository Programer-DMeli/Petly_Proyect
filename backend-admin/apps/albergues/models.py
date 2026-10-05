"""Modelos de albergues (US-46)."""
from django.db import models
from django.conf import settings


class Albergue(models.Model):
    """Perfil e información institucional del albergue."""

    nombre = models.CharField("Nombre", max_length=150)
    descripcion = models.TextField("Descripción", blank=True)
    direccion = models.CharField("Dirección", max_length=255)
    telefono = models.CharField("Teléfono", max_length=20, blank=True)
    email = models.EmailField("Email de contacto", blank=True)
    sitio_web = models.URLField("Sitio web", blank=True)

    # Capacidad y operación
    capacidad_maxima = models.PositiveIntegerField("Capacidad máxima", default=0)
    horario_atencion = models.CharField("Horario de atención", max_length=100, blank=True)

    # Responsable
    responsable_nombre = models.CharField("Nombre del responsable", max_length=150, blank=True)
    responsable_telefono = models.CharField("Teléfono del responsable", max_length=20, blank=True)
    responsable_email = models.EmailField("Email del responsable", blank=True)

    # Relación con usuario dueño (para permisos)
    usuario = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="albergue",
        verbose_name="Usuario propietario",
    )

    # Auditoría
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)
    activo = models.BooleanField(default=True)

    class Meta:
        verbose_name = "Albergue"
        verbose_name_plural = "Albergues"
        ordering = ["nombre"]

    def __str__(self):
        return self.nombre


class Infraestructura(models.Model):
    """Infraestructura y áreas físicas del albergue."""

    class TipoArea(models.TextChoices):
        RECEPCION = "RECEPCION", "Recepción"
        OFICINA = "OFICINA", "Oficina administrativa"
        ZONA_CUARENTENA = "ZONA_CUARENTENA", "Zona de cuarentena"
        ZONA_CACHORROS = "ZONA_CACHORROS", "Zona de cachorros"
        ZONA_ADULTOS = "ZONA_ADULTOS", "Zona de adultos"
        ZONA_GATOS = "ZONA_GATOS", "Zona de gatos"
        ENFERMERIA = "ENFERMERIA", "Enfermería / Veterinaria"
        QUIROFANO = "QUIROFANO", "Quirófano"
        ALMACEN = "ALMACEN", "Almacén de insumos"
        PATIO = "PATIO", "Patio / Área de recreación"
        BAÑOS = "BAÑOS", "Baños / Lavaderos"
        OTRO = "OTRO", "Otro"

    albergue = models.ForeignKey(
        Albergue,
        on_delete=models.CASCADE,
        related_name="infraestructura",
        verbose_name="Albergue",
    )
    tipo = models.CharField("Tipo de área", max_length=20, choices=TipoArea.choices)
    nombre = models.CharField("Nombre / Identificador", max_length=100)
    descripcion = models.TextField("Descripción", blank=True)
    metros_cuadrados = models.DecimalField(
        "Metros cuadrados", max_digits=8, decimal_places=2, null=True, blank=True
    )
    capacidad = models.PositiveIntegerField("Capacidad (animales)", null=True, blank=True)
    equipamiento = models.TextField("Equipamiento / Observaciones", blank=True)
    activo = models.BooleanField(default=True)
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Infraestructura"
        verbose_name_plural = "Infraestructura"
        ordering = ["albergue", "tipo", "nombre"]
        unique_together = ["albergue", "tipo", "nombre"]

    def __str__(self):
        return f"{self.albergue.nombre} - {self.get_tipo_display()}: {self.nombre}"