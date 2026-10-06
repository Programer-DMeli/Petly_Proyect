"""Modelos de mascotas (US-11, US-12, US-13)."""
from django.db import models
from django.conf import settings
from apps.albergues.models import Albergue


class Mascota(models.Model):
    """Catálogo de mascotas del albergue."""

    class Especie(models.TextChoices):
        PERRO = "PERRO", "Perro"
        GATO = "GATO", "Gato"
        OTRO = "OTRO", "Otro"

    class Sexo(models.TextChoices):
        MACHO = "MACHO", "Macho"
        HEMBRA = "HEMBRA", "Hembra"

    class Estado(models.TextChoices):
        DISPONIBLE = "DISPONIBLE", "Disponible"
        EN_PROCESO = "EN_PROCESO", "En Proceso"
        ADOPTADO = "ADOPTADO", "Adoptado"

    class Tamanio(models.TextChoices):
        PEQUENO = "PEQUENO", "Pequeño"
        MEDIANO = "MEDIANO", "Mediano"
        GRANDE = "GRANDE", "Grande"

    # Relación obligatoria con albergue
    albergue = models.ForeignKey(
        Albergue,
        on_delete=models.CASCADE,
        related_name="mascotas",
        verbose_name="Albergue",
    )

    # Identificación
    nombre = models.CharField("Nombre", max_length=100)
    codigo = models.CharField("Código interno", max_length=20, unique=True)
    especie = models.CharField("Especie", max_length=10, choices=Especie.choices)
    raza = models.CharField("Raza", max_length=100, blank=True)
    sexo = models.CharField("Sexo", max_length=10, choices=Sexo.choices)
    tamanio = models.CharField("Tamaño", max_length=10, choices=Tamanio.choices, blank=True)

    # Edad aproximada
    edad_anos = models.PositiveIntegerField("Edad (años)", null=True, blank=True)
    edad_meses = models.PositiveIntegerField("Edad (meses)", null=True, blank=True)

    # Estado de adopción
    estado = models.CharField(
        "Estado",
        max_length=15,
        choices=Estado.choices,
        default=Estado.DISPONIBLE,
        db_index=True,
    )

    # Descripción y salud
    descripcion = models.TextField("Descripción / Historia", blank=True)
    esterilizado = models.BooleanField("Esterilizado", default=False)
    vacunas_al_dia = models.BooleanField("Vacunas al día", default=False)
    desparasitado = models.BooleanField("Desparasitado", default=False)
    necesidades_especiales = models.TextField("Necesidades especiales", blank=True)

    # Imágenes (URLs o paths)
    foto_principal = models.ImageField("Foto principal", upload_to="mascotas/", blank=True, null=True)
    fotos_adicionales = models.JSONField("Fotos adicionales", default=list, blank=True)

    # Fechas relevantes
    fecha_ingreso = models.DateField("Fecha de ingreso", auto_now_add=True)
    fecha_adopcion = models.DateField("Fecha de adopción", null=True, blank=True)

    # Auditoría
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)
    creado_por = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="mascotas_creadas",
        verbose_name="Creado por",
    )

    class Meta:
        verbose_name = "Mascota"
        verbose_name_plural = "Mascotas"
        ordering = ["-creado_en"]
        indexes = [
            models.Index(fields=["albergue", "estado"]),
            models.Index(fields=["estado", "especie"]),
        ]

    def __str__(self):
        return f"{self.nombre} ({self.get_especie_display()}) - {self.get_estado_display()}"

    @property
    def edad_texto(self):
        """Edad legible: '2 años 3 meses'."""
        partes = []
        if self.edad_anos:
            partes.append(f"{self.edad_anos} año{'s' if self.edad_anos > 1 else ''}")
        if self.edad_meses:
            partes.append(f"{self.edad_meses} mes{'es' if self.edad_meses > 1 else ''}")
        return " ".join(partes) if partes else "Desconocida"