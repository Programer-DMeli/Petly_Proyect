from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from .models import User, AdoptanteProfile, Postulante, SolicitudAdopcion


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    list_display = ('username', 'email', 'rol', 'first_name', 'last_name', 'is_staff', 'is_active', 'date_joined')
    list_filter = ('rol', 'is_staff', 'is_active', 'is_superuser')
    search_fields = ('username', 'email', 'first_name', 'last_name')
    ordering = ('-date_joined',)
    
    fieldsets = BaseUserAdmin.fieldsets + (
        ('Información Petly', {'fields': ('rol', 'telefono', 'fecha_nacimiento', 'acepto_terminos', 'fecha_aceptacion_terminos', 'intentos_fallidos', 'bloqueado_hasta')}),
    )
    add_fieldsets = BaseUserAdmin.add_fieldsets + (
        ('Información Petly', {'fields': ('rol', 'telefono', 'fecha_nacimiento')}),
    )


@admin.register(AdoptanteProfile)
class AdoptanteProfileAdmin(admin.ModelAdmin):
    list_display = ('nombre_completo', 'user_link', 'tipo_vivienda', 'tiene_ninos', 'tiene_otras_mascotas', 'cuestionario_completado', 'fecha_cuestionario')
    list_filter = ('tipo_vivienda', 'tiene_ninos', 'tiene_otras_mascotas', 'cuestionario_completado')
    search_fields = ('user__username', 'user__email', 'documento_numero', 'nombres', 'apellidos')
    readonly_fields = ('user_link', 'creado_en', 'actualizado_en')
    ordering = ('-creado_en',)
    
    def user_link(self, obj):
        from django.urls import reverse
        from django.utils.html import format_html
        url = reverse('admin:acceso_user_change', args=[obj.user.pk])
        return format_html('<a href="{}">{}</a>', url, obj.user.username)
    user_link.short_description = 'Usuario'


@admin.register(Postulante)
class PostulanteAdmin(admin.ModelAdmin):
    list_display = ('nombre_completo', 'documento_numero', 'email', 'estado', 'total_solicitudes', 'solicitudes_aprobadas', 'creado_en')
    list_filter = ('estado', 'tipo_vivienda', 'tiene_otras_mascotas', 'preferencia_especie', 'creado_en')
    search_fields = ('nombres', 'apellidos', 'documento_numero', 'email')
    readonly_fields = ('creado_en', 'actualizado_en', 'total_solicitudes', 'solicitudes_aprobadas', 'solicitudes_pendientes')
    ordering = ('-creado_en',)
    
    fieldsets = (
        ('Identificación', {'fields': ('documento_tipo', 'documento_numero', 'nombres', 'apellidos', 'email', 'telefono', 'fecha_nacimiento')}),
        ('Domicilio', {'fields': ('direccion', 'distrito', 'provincia', 'departamento')}),
        ('Perfil de adopción', {'fields': ('tipo_vivienda', 'tiene_patio', 'tiene_otras_mascotas', 'detalles_otras_mascotas', 'experiencia_previa')}),
        ('Motivación y preferencias', {'fields': ('motivo_adopcion', 'preferencia_especie', 'preferencia_tamanio', 'preferencia_edad')}),
        ('Estado y control', {'fields': ('estado', 'observaciones_internas')}),
        ('Auditoría', {'fields': ('creado_por', 'creado_en', 'actualizado_en', 'total_solicitudes', 'solicitudes_aprobadas', 'solicitudes_pendientes')}),
    )


@admin.register(SolicitudAdopcion)
class SolicitudAdopcionAdmin(admin.ModelAdmin):
    list_display = ('id', 'postulante_link', 'mascota_link', 'albergue_link', 'estado_badge', 'fecha_solicitud', 'fecha_revision')
    list_filter = ('estado', 'albergue', 'mascota__especie', 'fecha_solicitud')
    search_fields = ('postulante__nombres', 'postulante__apellidos', 'postulante__documento_numero', 'mascota__nombre')
    readonly_fields = ('estado', 'fecha_solicitud', 'actualizado_en', 'postulante_detalle', 'mascota_detalle')
    ordering = ('-fecha_solicitud',)
    # Las decisiones deben pasar por cambiar_estado_solicitud para validar motivo,
    # autorización vigente y bloqueo de la mascota en una única transacción.
    actions = []
    
    fieldsets = (
        ('Información básica', {'fields': ('postulante', 'mascota', 'albergue', 'estado')}),
        ('Detalles', {'fields': ('mensaje_postulante', 'respuestas_cuestionario', 'observaciones_revision')}),
        ('Seguimiento', {'fields': ('revisado_por', 'fecha_revision', 'fecha_entrega')}),
        ('Auditoría', {'fields': ('fecha_solicitud', 'actualizado_en')}),
        ('Detalles vinculados', {'fields': ('postulante_detalle', 'mascota_detalle'), 'classes': ('collapse',)}),
    )
    
    def postulante_link(self, obj):
        from django.urls import reverse
        from django.utils.html import format_html
        url = reverse('admin:acceso_postulante_change', args=[obj.postulante.pk])
        return format_html('<a href="{}">{}</a>', url, obj.postulante.nombre_completo)
    postulante_link.short_description = 'Postulante'
    
    def mascota_link(self, obj):
        from django.urls import reverse
        from django.utils.html import format_html
        url = reverse('admin:mascotas_mascota_change', args=[obj.mascota.pk])
        return format_html('<a href="{}">{}</a>', url, obj.mascota.nombre)
    mascota_link.short_description = 'Mascota'
    
    def albergue_link(self, obj):
        from django.urls import reverse
        from django.utils.html import format_html
        url = reverse('admin:albergues_albergue_change', args=[obj.albergue.pk])
        return format_html('<a href="{}">{}</a>', url, obj.albergue.nombre)
    albergue_link.short_description = 'Albergue'
    
    def estado_badge(self, obj):
        from django.utils.html import format_html
        colors = {
            'PENDIENTE': '#ffc107',
            'OBSERVADA': '#17a2b8',
            'APROBADA': '#28a745',
            'RECHAZADA': '#dc3545',
            'CANCELADA': '#6c757d',
            'ENTREGADA': '#6f42c1',
        }
        color = colors.get(obj.estado, '#000')
        return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_estado_display())
    estado_badge.short_description = 'Estado'
    
    def postulante_detalle(self, obj):
        from django.utils.html import format_html
        p = obj.postulante
        return format_html(
            '<strong>{}</strong> ({})<br>Email: {}<br>Tel: {}<br>Doc: {}',
            p.nombre_completo, p.get_estado_display(), p.email, p.telefono, p.documento_numero
        )
    postulante_detalle.short_description = 'Detalle postulante'
    
    def mascota_detalle(self, obj):
        from django.utils.html import format_html
        m = obj.mascota
        return format_html(
            '<strong>{}</strong> ({})<br>Especie: {} | Tamaño: {} | Edad: {}<br>Albergue: {}',
            m.nombre, m.get_estado_display(), m.get_especie_display(), m.get_tamanio_display(), m.edad_texto, m.albergue.nombre
        )
    mascota_detalle.short_description = 'Detalle mascota'
    
    # Acciones masivas
    def _cambiar_estado_masivo(self, request, queryset, nuevo_estado, mensaje):
        from django.utils import timezone
        count = 0
        for obj in queryset:
            transiciones_validas = {
                'PENDIENTE': ['OBSERVADA', 'RECHAZADA', 'CANCELADA'],
                'OBSERVADA': ['APROBADA', 'RECHAZADA', 'PENDIENTE'],
                'APROBADA': ['ENTREGADA', 'RECHAZADA'],
                'RECHAZADA': [],
                'CANCELADA': [],
                'ENTREGADA': [],
            }
            if nuevo_estado in transiciones_validas.get(obj.estado, []):
                obj.estado = nuevo_estado
                obj.revisado_por = request.user
                obj.fecha_revision = timezone.now()
                if nuevo_estado == 'ENTREGADA':
                    obj.fecha_entrega = timezone.now().date()
                    m = obj.mascota
                    if m.estado != 'ADOPTADO':
                        m.estado = 'ADOPTADO'
                        m.fecha_adopcion = timezone.now().date()
                        m.save(update_fields=['estado', 'fecha_adopcion', 'actualizado_en'])
                obj.save(update_fields=['estado', 'revisado_por', 'fecha_revision', 'fecha_entrega', 'actualizado_en'])
                count += 1
        self.message_user(request, f'{count} solicitudes cambiadas a {mensaje}.')
    
    def cambiar_a_en_revision(self, request, queryset):
        self._cambiar_estado_masivo(request, queryset, 'OBSERVADA', 'Observada')
    cambiar_a_en_revision.short_description = 'Cambiar a Observada'
    
    def cambiar_a_aprobada(self, request, queryset):
        self._cambiar_estado_masivo(request, queryset, 'APROBADA', 'Aprobada')
    cambiar_a_aprobada.short_description = 'Cambiar a Aprobada'
    
    def cambiar_a_rechazada(self, request, queryset):
        self._cambiar_estado_masivo(request, queryset, 'RECHAZADA', 'Rechazada')
    cambiar_a_rechazada.short_description = 'Cambiar a Rechazada'
    
    def cambiar_a_entregada(self, request, queryset):
        self._cambiar_estado_masivo(request, queryset, 'ENTREGADA', 'Entregada')
    cambiar_a_entregada.short_description = 'Cambiar a Entregada (Adopción finalizada)'
