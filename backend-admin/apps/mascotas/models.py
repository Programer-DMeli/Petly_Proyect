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
        POR_EVAL = "POR_EVAL", "Por evaluar"
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

    class Temperamento(models.TextChoices):
        TRANQUILO = "TRANQUILO", "Tranquilo"
        ACTIVO = "ACTIVO", "Activo"
        JUGUETON = "JUGUETON", "Juguetón"
        TIMIDO = "TIMIDO", "Tímido"
        SOCIABLE = "SOCIABLE", "Sociable"
        INDEPENDIENTE = "INDEPENDIENTE", "Independiente"
        POR_EVALUAR = "POR_EVALUAR", "Por evaluar"

    # Relación obligatoria con albergue
    albergue = models.ForeignKey(
        Albergue,
        on_delete=models.CASCADE,
        related_name="mascotas",
        verbose_name="Albergue",
    )

    energia = models.CharField(max_length=20, default="POR_EVALUAR", choices=[("BAJA", "Baja"), ("MEDIA", "Media"), ("ALTA", "Alta"), ("POR_EVALUAR", "Por evaluar")])

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

    # Temperamento (US-12): categorías predefinidas, "Por evaluar" cuando se desconozca
    temperamento = models.CharField(
        "Temperamento",
        max_length=20,
        choices=Temperamento.choices,
        default=Temperamento.POR_EVALUAR,
        blank=True,
    )

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

    @property
    def get_valid_transitions(self):
        """Retorna lista de estados válidos para transición desde el actual."""
        transiciones = {
            self.Estado.DISPONIBLE: [self.Estado.EN_PROCESO, self.Estado.ADOPTADO],
            self.Estado.EN_PROCESO: [self.Estado.DISPONIBLE, self.Estado.ADOPTADO],
            self.Estado.ADOPTADO: [self.Estado.DISPONIBLE, self.Estado.EN_PROCESO],
        }
        return transiciones.get(self.estado, [])


class ReglaCompatibilidad(models.Model):
    """Pesos vigentes del motor compartido de compatibilidad (singleton id=1)."""

    id = models.PositiveSmallIntegerField(primary_key=True, default=1, editable=False)
    peso_vivienda = models.PositiveSmallIntegerField(default=30)
    peso_rutina = models.PositiveSmallIntegerField(default=35)
    peso_convivencia = models.PositiveSmallIntegerField(default=35)
    version_reglas = models.PositiveIntegerField(default=1)
    actualizado_en = models.DateTimeField(auto_now=True)
    actualizado_por_spring_id = models.PositiveBigIntegerField(null=True, blank=True)

    class Meta:
        constraints = [
            models.CheckConstraint(check=models.Q(id=1), name="compat_rule_singleton_id"),
            models.CheckConstraint(
                check=models.Q(peso_vivienda__gte=0, peso_vivienda__lte=100)
                & models.Q(peso_rutina__gte=0, peso_rutina__lte=100)
                & models.Q(peso_convivencia__gte=0, peso_convivencia__lte=100),
                name="compat_rule_weights_range",
            ),
        ]

    def clean(self):
        super().clean()
        if self.peso_vivienda + self.peso_rutina + self.peso_convivencia != 100:
            from django.core.exceptions import ValidationError
            raise ValidationError("Los pesos de compatibilidad deben sumar 100.")

    def save(self, *args, **kwargs):
        if self.pk != 1:
            from django.core.exceptions import ValidationError
            raise ValidationError("Solo existe una configuración global de compatibilidad.")
        self.full_clean()
        return super().save(*args, **kwargs)
