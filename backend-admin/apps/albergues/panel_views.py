"""Vistas de templates para el panel web del albergue."""
from django.shortcuts import render, get_object_or_404, redirect
from django.contrib.auth.decorators import login_required
from django.contrib import messages
from django.http import HttpResponse
from django.urls import reverse
from django.db.models import Q

from apps.albergues.models import Albergue, Infraestructura
from apps.mascotas.models import Mascota


@login_required
def panel_home(request):
    """Dashboard principal del panel."""
    albergue = getattr(request.user, 'albergue', None)
    
    # Estadísticas de mascotas
    total_mascotas = 0
    mascotas_disponibles = 0
    mascotas_en_proceso = 0
    mascotas_adoptadas = 0
    
    if albergue:
        mascotas_qs = albergue.mascotas.all()
        total_mascotas = mascotas_qs.count()
        mascotas_disponibles = mascotas_qs.filter(estado=Mascota.Estado.DISPONIBLE).count()
        mascotas_en_proceso = mascotas_qs.filter(estado=Mascota.Estado.EN_PROCESO).count()
        mascotas_adoptadas = mascotas_qs.filter(estado=Mascota.Estado.ADOPTADO).count()
    
    context = {
        'albergue': albergue,
        'total_mascotas': total_mascotas,
        'mascotas_disponibles': mascotas_disponibles,
        'mascotas_en_proceso': mascotas_en_proceso,
        'mascotas_adoptadas': mascotas_adoptadas,
    }
    return render(request, 'panel/home.html', context)


@login_required
def albergue_perfil(request):
    """Vista del perfil del albergue (lectura + edición HTMX)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    
    if request.method == 'POST' and request.headers.get('HX-Request'):
        # Actualización parcial via HTMX
        campos_editables = [
            'nombre', 'descripcion', 'direccion', 'telefono', 'email',
            'sitio_web', 'capacidad_maxima', 'horario_atencion',
            'responsable_nombre', 'responsable_telefono', 'responsable_email',
        ]
        for campo in campos_editables:
            if campo in request.POST:
                setattr(albergue, campo, request.POST[campo])
        albergue.save()
        messages.success(request, 'Datos actualizados correctamente')
        return render(request, 'albergues/partials/perfil_detalle.html', {'albergue': albergue})
    
    context = {'albergue': albergue}
    return render(request, 'albergues/perfil.html', context)


@login_required
def albergue_infraestructura(request):
    """Gestión de infraestructura del albergue."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    
    if request.method == 'POST' and request.headers.get('HX-Request'):
        # Crear nueva infraestructura
        tipo = request.POST.get('tipo')
        nombre = request.POST.get('nombre')
        if tipo and nombre:
            Infraestructura.objects.create(
                albergue=albergue,
                tipo=tipo,
                nombre=nombre,
                descripcion=request.POST.get('descripcion', ''),
                metros_cuadrados=request.POST.get('metros_cuadrados') or None,
                capacidad=request.POST.get('capacidad') or None,
                equipamiento=request.POST.get('equipamiento', ''),
            )
            messages.success(request, 'Área agregada correctamente')
    
    infraestructura = albergue.infraestructura.all()
    context = {'albergue': albergue, 'infraestructura': infraestructura}
    return render(request, 'albergues/infraestructura.html', context)


@login_required
def infraestructura_eliminar(request, infra_id):
    """Elimina una infraestructura (HTMX DELETE)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    infra = get_object_or_404(Infraestructura, pk=infra_id, albergue=albergue)
    
    if request.method == 'POST' or request.method == 'DELETE':
        infra.delete()
        messages.success(request, 'Área eliminada')
        return HttpResponse('', headers={'HX-Trigger': 'infraestructuraCambiada'})
    
    return HttpResponse('Método no permitido', status=405)


@login_required
def infraestructura_editar(request, infra_id):
    """Edita una infraestructura (GET formulario, POST actualiza)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    infra = get_object_or_404(Infraestructura, pk=infra_id, albergue=albergue)
    
    if request.method == 'POST':
        infra.tipo = request.POST.get('tipo', infra.tipo)
        infra.nombre = request.POST.get('nombre', infra.nombre)
        infra.descripcion = request.POST.get('descripcion', infra.descripcion)
        infra.metros_cuadrados = request.POST.get('metros_cuadrados') or None
        infra.capacidad = request.POST.get('capacidad') or None
        infra.equipamiento = request.POST.get('equipamiento', infra.equipamiento)
        infra.activo = 'activo' in request.POST
        infra.save()
        messages.success(request, 'Área actualizada')
        return render(request, 'albergues/partials/infraestructura_fila.html', {'infra': infra})
    
    context = {'infra': infra, 'tipos': Infraestructura.TipoArea.choices}
    return render(request, 'albergues/partials/infraestructura_form.html', context)


# ==================== VISTAS DE MASCOTAS ====================

@login_required
def mascota_lista(request):
    """Lista de mascotas del albergue con filtros y búsqueda."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    
    # Filtros
    estado_filtro = request.GET.get('estado', '')
    especie_filtro = request.GET.get('especie', '')
    busqueda = request.GET.get('q', '')
    
    mascotas = albergue.mascotas.all()
    
    if estado_filtro:
        mascotas = mascotas.filter(estado=estado_filtro)
    if especie_filtro:
        mascotas = mascotas.filter(especie=especie_filtro)
    if busqueda:
        mascotas = mascotas.filter(
            Q(nombre__icontains=busqueda) |
            Q(codigo__icontains=busqueda) |
            Q(raza__icontains=busqueda) |
            Q(descripcion__icontains=busqueda)
        )
    
    mascotas = mascotas.order_by('-creado_en')
    
    context = {
        'albergue': albergue,
        'mascotas': mascotas,
        'estado_filtro': estado_filtro,
        'especie_filtro': especie_filtro,
        'busqueda': busqueda,
        'estados': Mascota.Estado.choices,
        'especies': Mascota.Especie.choices,
    }
    return render(request, 'mascotas/lista.html', context)


@login_required
def mascota_crear(request):
    """Crear nueva mascota (HTMX formulario)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    
    if request.method == 'POST':
        # Crear mascota
        mascota = Mascota.objects.create(
            albergue=albergue,
            nombre=request.POST.get('nombre'),
            codigo=request.POST.get('codigo'),
            especie=request.POST.get('especie'),
            raza=request.POST.get('raza', ''),
            sexo=request.POST.get('sexo'),
            tamanio=request.POST.get('tamanio', ''),
            edad_anos=request.POST.get('edad_anos') or None,
            edad_meses=request.POST.get('edad_meses') or None,
            descripcion=request.POST.get('descripcion', ''),
            esterilizado='esterilizado' in request.POST,
            vacunas_al_dia='vacunas_al_dia' in request.POST,
            desparasitado='desparasitado' in request.POST,
            necesidades_especiales=request.POST.get('necesidades_especiales', ''),
            creado_por=request.user,
        )
        
        # Manejar foto principal
        if 'foto_principal' in request.FILES:
            mascota.foto_principal = request.FILES['foto_principal']
            mascota.save()
        
        messages.success(request, f'Mascota "{mascota.nombre}" creada correctamente')
        
        if request.headers.get('HX-Request'):
            return render(request, 'mascotas/partials/mascota_fila.html', {'mascota': mascota})
        return redirect('mascota_lista')
    
    context = {
        'albergue': albergue,
        'especies': Mascota.Especie.choices,
        'sexos': Mascota.Sexo.choices,
        'tamanios': Mascota.Tamanio.choices,
        'estados': Mascota.Estado.choices,
    }
    return render(request, 'mascotas/partials/mascota_form.html', context)


@login_required
def mascota_editar(request, mascota_id):
    """Editar mascota existente (HTMX formulario)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    mascota = get_object_or_404(Mascota, pk=mascota_id, albergue=albergue)
    
    if request.method == 'POST':
        mascota.nombre = request.POST.get('nombre', mascota.nombre)
        mascota.codigo = request.POST.get('codigo', mascota.codigo)
        mascota.especie = request.POST.get('especie', mascota.especie)
        mascota.raza = request.POST.get('raza', mascota.raza)
        mascota.sexo = request.POST.get('sexo', mascota.sexo)
        mascota.tamanio = request.POST.get('tamanio', mascota.tamanio)
        mascota.edad_anos = request.POST.get('edad_anos') or None
        mascota.edad_meses = request.POST.get('edad_meses') or None
        mascota.descripcion = request.POST.get('descripcion', mascota.descripcion)
        mascota.esterilizado = 'esterilizado' in request.POST
        mascota.vacunas_al_dia = 'vacunas_al_dia' in request.POST
        mascota.desparasitado = 'desparasitado' in request.POST
        mascota.necesidades_especiales = request.POST.get('necesidades_especiales', mascota.necesidades_especiales)
        
        if 'foto_principal' in request.FILES:
            mascota.foto_principal = request.FILES['foto_principal']
        
        mascota.save()
        messages.success(request, f'Mascota "{mascota.nombre}" actualizada')
        
        if request.headers.get('HX-Request'):
            return render(request, 'mascotas/partials/mascota_fila.html', {'mascota': mascota})
        return redirect('mascota_lista')
    
    context = {
        'mascota': mascota,
        'albergue': albergue,
        'especies': Mascota.Especie.choices,
        'sexos': Mascota.Sexo.choices,
        'tamanios': Mascota.Tamanio.choices,
        'estados': Mascota.Estado.choices,
    }
    return render(request, 'mascotas/partials/mascota_form.html', context)


@login_required
def mascota_eliminar(request, mascota_id):
    """Eliminar mascota (HTMX)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    mascota = get_object_or_404(Mascota, pk=mascota_id, albergue=albergue)
    
    if request.method in ['POST', 'DELETE']:
        nombre = mascota.nombre
        mascota.delete()
        messages.success(request, f'Mascota "{nombre}" eliminada')
        return HttpResponse('', headers={'HX-Trigger': 'mascotaCambiada'})
    
    return HttpResponse('Método no permitido', status=405)


@login_required
def mascota_cambiar_estado(request, mascota_id):
    """Cambiar estado de mascota (HTMX POST)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    mascota = get_object_or_404(Mascota, pk=mascota_id, albergue=albergue)
    
    if request.method == 'POST':
        nuevo_estado = request.POST.get('estado')
        
        # Validar transiciones permitidas
        transiciones_validas = {
            Mascota.Estado.DISPONIBLE: [Mascota.Estado.EN_PROCESO, Mascota.Estado.ADOPTADO],
            Mascota.Estado.EN_PROCESO: [Mascota.Estado.DISPONIBLE, Mascota.Estado.ADOPTADO],
            Mascota.Estado.ADOPTADO: [],
        }
        
        if nuevo_estado not in transiciones_validas.get(mascota.estado, []):
            messages.error(request, f'Transición no permitida: {mascota.get_estado_display()} -> {dict(Mascota.Estado.choices).get(nuevo_estado, nuevo_estado)}')
            return render(request, 'mascotas/partials/mascota_fila.html', {'mascota': mascota})
        
        mascota.estado = nuevo_estado
        if nuevo_estado == Mascota.Estado.ADOPTADO and not mascota.fecha_adopcion:
            from django.utils import timezone
            mascota.fecha_adopcion = timezone.now().date()
        mascota.save(update_fields=['estado', 'fecha_adopcion', 'actualizado_en'])
        
        messages.success(request, f'Estado cambiado a {mascota.get_estado_display()}')
        return render(request, 'mascotas/partials/mascota_fila.html', {'mascota': mascota})
    
    return HttpResponse('Método no permitido', status=405)


@login_required
def mascota_detalle(request, mascota_id):
    """Detalle de mascota (modal HTMX)."""
    albergue = get_object_or_404(Albergue, usuario=request.user)
    mascota = get_object_or_404(Mascota, pk=mascota_id, albergue=albergue)
    
    context = {'mascota': mascota, 'albergue': albergue}
    return render(request, 'mascotas/partials/mascota_detalle.html', context)