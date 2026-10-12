from django.contrib import admin
from django.utils.html import format_html
from .models import Mascota


@admin.register(Mascota)
class MascotaAdmin(admin.ModelAdmin):
    list_display = ('foto_thumbnail', 'nombre', 'codigo', 'especie_badge', 'tamanio_badge', 'temperamento_badge', 'edad_texto', 'estado_badge', 'albergue_link', 'creado_en')
    list_filter = ('especie', 'tamanio', 'temperamento', 'estado', 'albergue', 'sexo', 'esterilizado', 'vacunas_al_dia', 'desparasitado', 'creado_en')
    search_fields = ('nombre', 'codigo', 'raza', 'descripcion', 'albergue__nombre')
    readonly_fields = ('foto_preview', 'codigo', 'edad_texto', 'fecha_ingreso', 'fecha_adopcion', 'creado_en', 'actualizado_en', 'creado_por')
    ordering = ('-creado_en',)
    list_per_page = 25
    date_hierarchy = 'creado_en'
    
    fieldsets = (
        ('Identificación', {'fields': ('nombre', 'codigo', 'especie', 'raza', 'sexo')}),
        ('Características', {'fields': ('tamanio', 'temperamento', 'edad_anos', 'edad_meses', 'edad_texto')}),
        ('Estado y salud', {'fields': ('estado', 'esterilizado', 'vacunas_al_dia', 'desparasitado', 'necesidades_especiales')}),
        ('Descripción', {'fields': ('descripcion',)}),
        ('Imágenes', {'fields': ('foto_principal', 'foto_preview', 'fotos_adicionales')}),
        ('Fechas', {'fields': ('fecha_ingreso', 'fecha_adopcion')}),
        ('Relaciones y auditoría', {'fields': ('albergue', 'creado_por', 'creado_en', 'actualizado_en')}),
    )
    
    def foto_thumbnail(self, obj):
        if obj.foto_principal:
            return format_html('<img src="{}" width="50" height="50" style="object-fit:cover; border-radius:4px;" />', obj.foto_principal.url)
        return '—'
    foto_thumbnail.short_description = 'Foto'
    
    def foto_preview(self, obj):
        if obj.foto_principal:
            return format_html('<img src="{}" width="300" style="max-width:100%; border-radius:4px;" />', obj.foto_principal.url)
        return 'Sin imagen'
    foto_preview.short_description = 'Vista previa'
    
    def especie_badge(self, obj):
        from django.utils.html import format_html
        colors = {'PERRO': '#17a2b8', 'GATO': '#ffc107', 'OTRO': '#6c757d'}
        color = colors.get(obj.especie, '#000')
        return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_especie_display())
    especie_badge.short_description = 'Especie'
    
    def tamanio_badge(self, obj):
        from django.utils.html import format_html
        colors = {'PEQUENO': '#28a745', 'MEDIANO': '#ffc107', 'GRANDE': '#dc3545'}
        color = colors.get(obj.tamanio, '#000')
        if obj.tamanio:
            return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_tamanio_display())
        return '—'
    tamanio_badge.short_description = 'Tamaño'
    
    def temperamento_badge(self, obj):
        from django.utils.html import format_html
        colors = {
            'TRANQUILO': '#28a745',
            'ACTIVO': '#ffc107',
            'JUGUETON': '#fd7e14',
            'TIMIDO': '#6f42c1',
            'SOCIABLE': '#17a2b8',
            'INDEPENDIENTE': '#6c757d',
            'POR_EVALUAR': '#dc3545',
        }
        color = colors.get(obj.temperamento, '#000')
        if obj.temperamento:
            return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_temperamento_display())
        return '—'
    temperamento_badge.short_description = 'Temperamento'
    
    def estado_badge(self, obj):
        from django.utils.html import format_html
        colors = {'DISPONIBLE': '#28a745', 'EN_PROCESO': '#ffc107', 'ADOPTADO': '#6f42c1'}
        color = colors.get(obj.estado, '#000')
        return format_html('<span style="background:{}; color:white; padding:2px 8px; border-radius:3px;">{}</span>', color, obj.get_estado_display())
    estado_badge.short_description = 'Estado'
    
    def albergue_link(self, obj):
        from django.urls import reverse
        url = reverse('admin:albergues_albergue_change', args=[obj.albergue.pk])
        return format_html('<a href="{}">{}</a>', url, obj.albergue.nombre)
    albergue_link.short_description = 'Albergue'
    
    actions = ['marcar_disponible', 'marcar_en_proceso', 'marcar_adoptado']
    
    def _cambiar_estado(self, request, queryset, nuevo_estado, mensaje):
        from django.utils import timezone
        transiciones_validas = {
            'DISPONIBLE': ['EN_PROCESO', 'ADOPTADO'],
            'EN_PROCESO': ['DISPONIBLE', 'ADOPTADO'],
            'ADOPTADO': [],
        }
        count = 0
        for obj in queryset:
            if nuevo_estado in transiciones_validas.get(obj.estado, []):
                obj.estado = nuevo_estado
                if nuevo_estado == 'ADOPTADO' and not obj.fecha_adopcion:
                    obj.fecha_adopcion = timezone.now().date()
                obj.save(update_fields=['estado', 'fecha_adopcion', 'actualizado_en'])
                count += 1
        self.message_user(request, f'{count} mascotas cambiadas a {mensaje}.')
    
    def marcar_disponible(self, request, queryset):
        self._cambiar_estado(request, queryset, 'DISPONIBLE', 'Disponible')
    marcar_disponible.short_description = 'Marcar como Disponible'
    
    def marcar_en_proceso(self, request, queryset):
        self._cambiar_estado(request, queryset, 'EN_PROCESO', 'En Proceso')
    marcar_en_proceso.short_description = 'Marcar como En Proceso'
    
    def marcar_adoptado(self, request, queryset):
        self._cambiar_estado(request, queryset, 'ADOPTADO', 'Adoptado')
    marcar_adoptado.short_description = 'Marcar como Adoptado'