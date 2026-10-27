"""Modelos de acceso y postulantes (US-18, US-21)."""
from django.db import models
from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.core.validators import RegexValidator
from django.utils import timezone
from django.utils.translation import gettext_lazy as _
from .kyc_storage import PrivateKycStorage, nombre_archivo_kyc


class User(AbstractUser):
    """Usuario personalizado con roles para Petly (US-21)."""

    spring_id = models.PositiveBigIntegerField(null=True, blank=True, unique=True)

    class Rol(models.TextChoices):
        ADOPTANTE = "ADOPTANTE", "Adoptante"
        ALBERGUE = "ALBERGUE", "Albergue"
        ADMIN = "ADMIN", "Administrador"

    rol = models.CharField(
        "Rol",
        max_length=20,
        choices=Rol.choices,
        default=Rol.ADOPTANTE,
        db_index=True,
    )
    telefono = models.CharField("Teléfono", max_length=20, blank=True)
    fecha_nacimiento = models.DateField("Fecha de nacimiento", null=True, blank=True)
    acepto_terminos = models.BooleanField("Aceptó términos", default=False)
    fecha_aceptacion_terminos = models.DateTimeField("Fecha aceptación términos", null=True, blank=True)

    # Bloqueo por intentos fallidos (US-21): 2 fallos consecutivos -> 5 min bloqueo
    intentos_fallidos = models.PositiveIntegerField("Intentos fallidos", default=0)
    bloqueado_hasta = models.DateTimeField("Bloqueado hasta", null=True, blank=True)

    class Meta:
        verbose_name = "Usuario"
        verbose_name_plural = "Usuarios"
        ordering = ["-date_joined"]

    def __str__(self):
        return f"{self.username} ({self.get_rol_display()})"

    @property
    def is_adoptante(self):
        return self.rol == self.Rol.ADOPTANTE

    @property
    def is_albergue(self):
        return self.rol == self.Rol.ALBERGUE

    @property
    def is_admin_user(self):
        return self.rol == self.Rol.ADMIN or self.is_superuser


class AdoptanteProfile(models.Model):
    """Perfil extendido del adoptante para el catálogo móvil (US-21, US-16, US-17)."""

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="adoptante_profile",
        verbose_name="Usuario",
    )

    # Datos personales adicionales
    documento_tipo = models.CharField("Tipo documento", max_length=20, default="DNI")
    documento_numero = models.CharField("Número documento", max_length=20, unique=True, blank=True, null=True)
    nombres = models.CharField("Nombres", max_length=100, blank=True)
    apellidos = models.CharField("Apellidos", max_length=100, blank=True)

    # Domicilio
    direccion = models.CharField("Dirección", max_length=255, blank=True)
    distrito = models.CharField("Distrito", max_length=100, blank=True)
    provincia = models.CharField("Provincia", max_length=100, blank=True)
    departamento = models.CharField("Departamento", max_length=100, blank=True)

    # Perfil de adopción (US-16, US-17)
    tipo_vivienda = models.CharField("Tipo de vivienda", max_length=50, blank=True,
        help_text="Casa, departamento, quintas, etc.")
    tiene_patio = models.BooleanField("Tiene patio/área exterior", default=False)
    tiene_ninos = models.BooleanField("Viven niños en el hogar", default=False)
    tiene_otras_mascotas = models.BooleanField("Tiene otras mascotas", default=False)
    horas_fuera = models.PositiveSmallIntegerField("Horas fuera de casa al día", null=True, blank=True)
    detalles_otras_mascotas = models.TextField("Detalle otras mascotas", blank=True)
    experiencia_previa = models.TextField("Experiencia previa con mascotas", blank=True)

    # Motivación y preferencias
    motivo_adopcion = models.TextField("Motivo de adopción", blank=True)
    preferencia_especie = models.CharField("Preferencia especie", max_length=10,
        choices=[("PERRO", "Perro"), ("GATO", "Gato"), ("OTRO", "Otro")], blank=True)
    preferencia_tamanio = models.CharField("Preferencia tamaño", max_length=10,
        choices=[("PEQUENO", "Pequeño"), ("MEDIANO", "Mediano"), ("GRANDE", "Grande")], blank=True)
    preferencia_edad = models.CharField("Preferencia edad", max_length=50, blank=True,
        help_text="Ej: cachorro, joven, adulto, senior")

    # Cuestionario completado (US-16)
    cuestionario_completado = models.BooleanField("Cuestionario completado", default=False)
    fecha_cuestionario = models.DateTimeField("Fecha cuestionario", null=True, blank=True)
    compatibilidad_revision = models.PositiveIntegerField(
        "Revisión de compatibilidad", default=1,
        help_text="Aumenta cuando cambia un dato usado por el motor de compatibilidad.",
    )

    # Auditoría
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Perfil de Adoptante"
        verbose_name_plural = "Perfiles de Adoptantes"
        ordering = ["-creado_en"]

    CAMPOS_COMPATIBILIDAD = (
        "tipo_vivienda", "tiene_patio", "tiene_ninos", "tiene_otras_mascotas",
        "horas_fuera", "preferencia_especie", "preferencia_tamanio", "preferencia_edad",
    )

    def save(self, *args, **kwargs):
        """Versiona cualquier cambio de datos que pueda alterar un puntaje."""
        if self.pk and not self._state.adding:
            previos = type(self).objects.filter(pk=self.pk).values(
                *self.CAMPOS_COMPATIBILIDAD, "compatibilidad_revision"
            ).first()
            if previos and any(getattr(self, campo) != previos[campo] for campo in self.CAMPOS_COMPATIBILIDAD):
                self.compatibilidad_revision = previos["compatibilidad_revision"] + 1
                update_fields = kwargs.get("update_fields")
                if update_fields is not None:
                    kwargs["update_fields"] = set(update_fields) | {"compatibilidad_revision"}
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.nombres} {self.apellidos} ({self.user.username})"

    @property
    def nombre_completo(self):
        return f"{self.nombres} {self.apellidos}"


class VerificacionKyc(models.Model):
    """Estado y trazabilidad de una consulta de identidad a RENIEC (US-22)."""

    class Estado(models.TextChoices):
        PENDIENTE = "PENDIENTE", "Pendiente de consulta"
        VERIFICADO = "VERIFICADO", "Verificado por RENIEC"
        PENDIENTE_REVISION = "PENDIENTE_REVISION", "Pendiente de revisión"
        APROBADO_MANUAL = "APROBADO_MANUAL", "Aprobado por revisión manual"
        RECHAZADO_MANUAL = "RECHAZADO_MANUAL", "Rechazado por revisión manual"

    adoptante = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="verificacion_kyc",
        limit_choices_to={"rol": "ADOPTANTE"},
    )
    dni = models.CharField(
        "DNI",
        max_length=8,
        validators=[RegexValidator(r"^\d{8}$", "El DNI debe contener exactamente ocho dígitos.")],
    )
    nombres = models.CharField(max_length=100)
    apellidos = models.CharField(max_length=100)
    estado = models.CharField(
        max_length=24,
        choices=Estado.choices,
        default=Estado.PENDIENTE,
        db_index=True,
    )
    coincide = models.BooleanField(null=True, blank=True)
    proveedor = models.CharField(max_length=80, blank=True)
    referencia_reniec = models.CharField(max_length=160, blank=True)
    fecha_consulta = models.DateTimeField(null=True, blank=True)
    ultimo_intento_en = models.DateTimeField(null=True, blank=True)
    error_servicio = models.CharField(max_length=500, blank=True)
    motivo_revision = models.CharField(max_length=500, blank=True)
    fecha_revision = models.DateTimeField(null=True, blank=True)
    revisado_por = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="verificaciones_kyc_revisadas",
        limit_choices_to={"rol": "ADMIN"},
    )
    documento_anverso = models.ImageField(
        upload_to=nombre_archivo_kyc,
        storage=PrivateKycStorage(),
        null=True,
        blank=True,
    )
    documento_reverso = models.ImageField(
        upload_to=nombre_archivo_kyc,
        storage=PrivateKycStorage(),
        null=True,
        blank=True,
    )
    creado_en = models.DateTimeField(auto_now_add=True)
    actualizado_en = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Verificación KYC"
        verbose_name_plural = "Verificaciones KYC"
        ordering = ["-actualizado_en"]

    def __str__(self):
        return f"KYC {self.adoptante_id} ({self.get_estado_display()})"


class Autorizacion(models.Model):
    """Autorización del adoptante para que un albergue consulte su perfil (US-18)."""

    class Estado(models.TextChoices):
        VIGENTE = "VIGENTE", "Vigente"
        REVOCADA = "REVOCADA", "Revocada"
        EXPIRADA = "EXPIRADA", "Expirada"

    adoptante = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="autorizaciones",
        verbose_name="Adoptante",
        limit_choices_to={"rol": "ADOPTANTE"},
    )
    albergue = models.ForeignKey(
        "albergues.Albergue",
        on_delete=models.CASCADE,
        related_name="autorizaciones",
        verbose_name="Albergue",
    )
    estado = models.CharField(
        "Estado",
        max_length=15,
        choices=Estado.choices,
        default=Estado.VIGENTE,
        db_index=True,
    )
    campos_permitidos = models.JSONField(
        "Campos permitidos",
        default=list,
        help_text="Lista de campos del perfil que el albergue puede consultar",
    )
    fecha_autorizacion = models.DateTimeField(auto_now_add=True)
    fecha_revocacion = models.DateTimeField(null=True, blank=True)
    fecha_expiracion = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = "Autorización"
        verbose_name_plural = "Autorizaciones"
        ordering = ["-fecha_autorizacion"]
        unique_together = ["adoptante", "albergue"]
        indexes = [
            models.Index(fields=["adoptante", "estado"]),
            models.Index(fields=["albergue", "estado"]),
        ]

    def __str__(self):
        return f"{self.adoptante.username} -> {self.albergue.nombre} ({self.get_estado_display()})"

    def esta_vigente(self):
        from django.utils import timezone
        ahora = timezone.now()
        if self.estado != self.Estado.VIGENTE:
            return False
        if self.fecha_expiracion and self.fecha_expiracion < ahora:
            return False
        return True

    def revocar(self):
        from django.utils import timezone
        self.estado = self.Estado.REVOCADA
        self.fecha_revocacion = timezone.now()
        self.save(update_fields=["estado", "fecha_revocacion"])


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
        choices=[("PERRO", "Perro"), ("GATO", "Gato"), ("OTRO", "Otro")], blank=True)
    preferencia_tamanio = models.CharField("Preferencia tamaño", max_length=10,
        choices=[("PEQUENO", "Pequeño"), ("MEDIANO", "Mediano"), ("GRANDE", "Grande")], blank=True)
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
        OBSERVADA = "OBSERVADA", "Observada"
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
        null=True,
        blank=True,
    )
    adoptante = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        related_name="solicitudes_adopcion",
        verbose_name="Cuenta adoptante",
        null=True,
        blank=True,
    )
    mascota = models.ForeignKey(
        "mascotas.Mascota",
        on_delete=models.CASCADE,
        related_name="solicitudes",
        verbose_name="Mascota",
    )
    albergue = models.ForeignKey(
        "albergues.Albergue",
        on_delete=models.CASCADE,
        related_name="solicitudes",
        verbose_name="Albergue",
    )
    adoptante = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="solicitudes_adopcion",
        verbose_name="Cuenta adoptante",
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
                condition=models.Q(estado__in=["PENDIENTE", "OBSERVADA"]),
                name="unique_pending_solicitud_per_mascota"
            ),
            models.UniqueConstraint(
                fields=["adoptante", "mascota"],
                condition=models.Q(estado__in=["PENDIENTE", "OBSERVADA", "EN_REVISION"]),
                name="unique_active_adoptante_mascota",
            ),
        ]

    def __str__(self):
        titular = self.postulante.nombre_completo if self.postulante_id else (self.adoptante.email if self.adoptante_id else "Adoptante")
        return f"Solicitud #{self.id} - {titular} -> {self.mascota.nombre} ({self.get_estado_display()})"

    @property
    def get_valid_transitions(self):
        """Retorna lista de estados válidos para transición desde el actual."""
        transiciones = {
            self.Estado.PENDIENTE: [self.Estado.OBSERVADA, self.Estado.APROBADA, self.Estado.RECHAZADA],
            self.Estado.OBSERVADA: [self.Estado.APROBADA, self.Estado.RECHAZADA],
            self.Estado.APROBADA: [],
            self.Estado.RECHAZADA: [],
            self.Estado.CANCELADA: [],
            self.Estado.ENTREGADA: [],
        }
        return transiciones.get(self.estado, [])


class SolicitudEvento(models.Model):
    """Evento inmutable del seguimiento cronológico de una solicitud (US-08)."""

    class Tipo(models.TextChoices):
        REGISTRO_HISTORICO = "REGISTRO_HISTORICO", "Registro histórico"
        SOLICITUD_CREADA = "SOLICITUD_CREADA", "Solicitud creada"
        OBSERVADA = "OBSERVADA", "Solicitud observada"
        APROBADA = "APROBADA", "Solicitud aprobada"
        RECHAZADA = "RECHAZADA", "Solicitud rechazada"
        CANCELADA = "CANCELADA", "Solicitud cancelada"
        CONTRATO_GENERADO = "CONTRATO_GENERADO", "Contrato generado"
        CONTRATO_ENVIADO = "CONTRATO_ENVIADO", "Contrato enviado a firma"
        CONTRATO_FIRMADO = "CONTRATO_FIRMADO", "Contrato firmado"
        CONTRATO_APROBADO_CORREO = "CONTRATO_APROBADO_CORREO", "Contrato aprobado por correo"
        CONTRATO_RECHAZADO = "CONTRATO_RECHAZADO", "Contrato rechazado"
        CONTRATO_EXPIRADO = "CONTRATO_EXPIRADO", "Contrato expirado"
        ENTREGADA = "ENTREGADA", "Mascota entregada"

    solicitud = models.ForeignKey(
        SolicitudAdopcion,
        on_delete=models.CASCADE,
        related_name="eventos",
        verbose_name="Solicitud",
    )
    tipo = models.CharField(max_length=24, choices=Tipo.choices, db_index=True)
    estado_anterior = models.CharField(max_length=15, blank=True)
    estado_nuevo = models.CharField(max_length=15, blank=True)
    descripcion = models.TextField(blank=True)
    ocurrido_en = models.DateTimeField(default=timezone.now, db_index=True)
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="eventos_solicitud_registrados",
    )
    visible_adoptante = models.BooleanField(default=True)

    class Meta:
        verbose_name = "Evento de solicitud"
        verbose_name_plural = "Eventos de solicitudes"
        ordering = ["ocurrido_en", "id"]
        indexes = [models.Index(fields=["solicitud", "ocurrido_en"])]

    def __str__(self):
        return f"{self.solicitud_id}: {self.get_tipo_display()} ({self.ocurrido_en:%Y-%m-%d %H:%M})"
