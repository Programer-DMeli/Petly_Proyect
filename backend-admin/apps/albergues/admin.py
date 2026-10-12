from django.contrib import admin
from django.utils.html import format_html
from .models import Albergue, Infraestructura


class InfraestructuraInline(admin.TabularInline):
    model = Infraestructura
    extra = 1
    fields = ('tipo', 'nombre', 'descripcion', 'metros_cuadrados', 'capacidad', 'equipamiento', 'activo')
    readonly_fields = ('creado_en', 'actualizado_en')


@admin.register(Albergue)
class AlbergueAdmin(admin.ModelAdmin):
    list_display = ('nombre', 'usuario_link', 'capacidad_maxima', 'activo', 'infraestructura_count', 'mascotas_count', 'creado_en')
    list_filter = ('activo', 'creado_en')
    search_fields = ('nombre', 'usuario__username', 'usuario__email', 'direccion', 'email')
    readonly_fields = ('usuario_link', 'creado_en', 'actualizado_en', 'infraestructura_count', 'mascotas_count')
    ordering = ('nombre',)
    
    fieldsets = (
        ('Información básica', {'fields': ('usuario', 'usuario_link', 'nombre', 'descripcion', 'activo')}),
        ('Ubicación y contacto', {'fields': ('direccion', 'telefono', 'email', 'sitio_web', 'horario_atencion')}),
        ('Capacidad', {'fields': ('capacidad_maxima',)}),
        ('Responsable', {'fields': ('responsable_nombre', 'responsable_telefono', 'responsable_email')}),
        ('Estadísticas', {'fields': ('infraestructura_count', 'mascotas_count'), 'classes': ('collapse',)}),
        ('Auditoría', {'fields': ('creado_en', 'actualizado_en'), 'classes': ('collapse',)}),
    )
    
    inlines = [InfraestructuraInline]
    
    def usuario_link(self, obj):
        from django.urls import reverse
        if obj.usuario:
            url = reverse('admin:acceso_user_change', args=[obj.usuario.pk])
            return format_html('<a href="{}">{}</a>', url, obj.usuario.username)
        return '—'
    usuario_link.short_description = 'Usuario propietario'
    
    def infraestructura_count(self, obj):
        return obj.infraestructura.count()
    infraestructura_count.short_description = 'Áreas'
    
    def mascotas_count(self, obj):
        return obj.mascotas.count()
    mascotas_count.short_description = 'Mascotas'


@admin.register(Infraestructura)
class InfraestructuraAdmin(admin.ModelAdmin):
    list_display = ('nombre', 'albergue_link', 'tipo_badge', 'metros_cuadrados', 'capacidad', 'activo', 'creado_en')
    list_filter = ('tipo', 'activo', 'albergue', 'creado_en')
    search_fields = ('nombre', 'albergue__nombre', 'descripcion')
    readonly_fields = ('albergue_link', 'creado_en', 'actualizado_en')
    ordering = ('albergue', 'tipo', 'nombre')
    
    def albergue_link(self, obj):
        from django.urls import reverse
        url = reverse('admin:albergues_albergue_change', args=[obj.albergue.pk])
        return format_html('<a href="{}">{}</a>', url, obj.albergue.nombre)
    albergue_link.short_description = 'Albergue'
    
    def tipo_badge(self, obj):
        from django.utils.html import format_html
        colors = {
            'RECEPCION': '#6c757d',
            'OFICINA': '#6c757d',
            'ZONA_CUARENTENA': '#dc3545',
            'ZONA_CACHORROS': '#ffc107',
            'ZONA_ADULTOS': '#17a2b8',
            'ZONA_GATOS': '#6f42c1',
            'ENFERMERIA': '#28a745',
            'QUIROFANO': '#dc3545',
            'ALMACEN': '#6c757d',
            'PATIO': '#28a745',
            'BANOS': '#17a2b8',
            'OTRO': '#6c757d',
        }
        color = colors.get(obj.tipo, '#000')
        return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_tipo_display())
    tipo_badge.short_description = 'Tipo'