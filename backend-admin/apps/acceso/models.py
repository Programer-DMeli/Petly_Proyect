"""Modelos de acceso y postulantes (US-18, US-21)."""
from django.db import models
from django.conf import settings
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class Postulante(models.Model):
    """Persona interesada en adoptar (datos básicos + historial)."""

    class EstadoPostulante(models.TextChoices):
        ACTIVO = "ACTIVO", "Activo"
        INACTIVO = "INACTIVO", "Inactivo"
        BLOQUEADO = "BLOQUEADO", "Bloqueado"

    # Identificación
    documento_tipo = models.CharField("Tipo documento", max_length=20, default="DNI")
    documento_numero = models.CharField("Número documento", max_length=20, unique=True)
    nombres = models.CharField("Nombres", max_length=100)
    apellidos = models.CharField("Apellidos", max_length=100)
    email = models.EmailField("Email", blank=True)
    telefono = models.CharField("Teléfono", max_length=20, blank=True)
    fecha_nacimiento = models.DateField("Fecha de nacimiento", null=True, blank=True)

    # Domicilio
    direccion = models.CharField("Dirección", max_length=255, blank=True)
    distrito = models.CharField("Distrito", max_length=100, blank=True)
    provincia = models.CharField("Provincia", max_length=100, blank=True)
    departamento = models.CharField("Departamento", max_length=100, blank=True)

    # Perfil de adopción
    tipo_vivienda = models.CharField("Tipo de vivienda", max_length=50, blank=True,
        help_text="Casa, departamento, quintas, etc.")
    tiene_patio = models.BooleanField("Tiene patio/área exterior", default=False)
    tiene_otras_mascotas = models.BooleanField("Tiene otras mascotas", default=False)
    detalles_otras_mascotas = models.TextField("Detalle otras mascotas", blank=True)
    experiencia_previa = models.TextField("Experiencia previa con mascotas", blank=True)

    # Motivación
    motivo_adopcion = models.TextField("Motivo de adopción", blank=True)
    preferencia_especie = models.CharField("Preferencia especie", max_length=10,
        choices=Mascota.Especie.choices, blank=True)
    preferencia_tamanio = models.CharField("Preferencia tamaño", max_length=10,
        choices=Mascota.Tamanio.choices, blank=True)
    preferencia_edad = models.CharField("Preferencia edad", max_length=50, blank=True,
        help_text="Ej: cachorro, joven, adulto, senior")

    # Estado y control
    estado = models.CharField("Estado", max_length=15,
        choices=EstadoPostulante.choices, default=EstadoPostulante.ACTIVO)
    observaciones_internas = models.TextField("Observaciones internas (staff)", blank=True)

    # Auditoría
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)
    creado_por = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="postulantes_creados",
    )

    class Meta:
        verbose_name = "Postulante"
        verbose_name_plural = "Postulantes"
        ordering = ["-creado_en"]
        indexes = [
            models.Index(fields=["documento_numero"]),
            models.Index(fields=["estado"]),
            models.Index(fields=["email"]),
        ]

    def __str__(self):
        return f"{self.nombres} {self.apellidos} ({self.documento_numero})"

    @property
    def nombre_completo(self):
        return f"{self.nombres} {self.apellidos}"

    @property
    def total_solicitudes(self):
        return self.solicitudes.count()

    @property
    def solicitudes_aprobadas(self):
        return self.solicitudes.filter(estado=SolicitudAdopcion.Estado.APROBADA).count()

    @property
    def solicitudes_pendientes(self):
        return self.solicitudes.filter(estado=SolicitudAdopcion.Estado.PENDIENTE).count()


class SolicitudAdopcion(models.Model):
    """Solicitud de adopción de una mascota por un postulante."""

    class Estado(models.TextChoices):
        PENDIENTE = "PENDIENTE", "Pendiente"
        EN_REVISION = "EN_REVISION", "En Revisión"
        APROBADA = "APROBADA", "Aprobada"
        RECHAZADA = "RECHAZADA", "Rechazada"
        CANCELADA = "CANCELADA", "Cancelada por postulante"
        ENTREGADA = "ENTREGADA", "Entregada (adopción finalizada)"

    # Relaciones
    postulante = models.ForeignKey(
        Postulante,
        on_delete=models.CASCADE,
        related_name="solicitudes",
        verbose_name="Postulante",
    )
    mascota = models.ForeignKey(
        Mascota,
        on_delete=models.CASCADE,
        related_name="solicitudes",
        verbose_name="Mascota",
    )
    albergue = models.ForeignKey(
        Albergue,
        on_delete=models.CASCADE,
        related_name="solicitudes",
        verbose_name="Albergue",
    )

    # Estado del proceso
    estado = models.CharField("Estado", max_length=15,
        choices=Estado.choices, default=Estado.PENDIENTE, db_index=True)

    # Información de la solicitud
    mensaje_postulante = models.TextField("Mensaje del postulante", blank=True,
        help_text="Por qué quiere adoptar a esta mascota")
    respuestas_cuestionario = models.JSONField("Respuestas cuestionario", default=dict, blank=True)

    # Seguimiento
    revisado_por = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="solicitudes_revisadas",
    )
    fecha_revision = models.DateTimeField("Fecha de revisión", null=True, blank=True)
    observaciones_revision = models.TextField("Observaciones de revisión", blank=True)

    # Fechas clave
    fecha_solicitud = models.DateTimeField(auto_now_add=True)
    fecha_entrega = models.DateField("Fecha de entrega", null=True, blank=True)

    # Auditoría
    actualizado_en = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Solicitud de Adopción"
        verbose_name_plural = "Solicitudes de Adopción"
        ordering = ["-fecha_solicitud"]
        indexes = [
            models.Index(fields=["albergue", "estado"]),
            models.Index(fields=["postulante", "estado"]),
            models.Index(fields=["mascota", "estado"]),
        ]
        # Un postulante no puede tener 2 solicitudes pendientes para la misma mascota
        constraints = [
            models.UniqueConstraint(
                fields=["postulante", "mascota"],
                condition=models.Q(estado__in=["PENDIENTE", "EN_REVISION"]),
                name="unique_pending_solicitud_per_mascota"
            ),
        ]

    def __str__(self):
        return f"Solicitud #{self.id} - {self.postulante.nombre_completo} -> {self.mascota.nombre} ({self.get_estado_display()})"

    @property
    def get_valid_transitions(self):
        """Retorna lista de estados válidos para transición desde el actual."""
        transiciones = {
            self.Estado.PENDIENTE: [self.Estado.EN_REVISION, self.Estado.RECHAZADA, self.Estado.CANCELADA],
            self.Estado.EN_REVISION: [self.Estado.APROBADA, self.Estado.RECHAZADA, self.Estado.PENDIENTE],
            self.Estado.APROBADA: [self.Estado.ENTREGADA, self.Estado.RECHAZADA],
            self.Estado.RECHAZADA: [],
            self.Estado.CANCELADA: [],
            self.Estado.ENTREGADA: [],
        }
        return transiciones.get(self.estado, [])