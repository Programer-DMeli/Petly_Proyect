"""Vistas de templates para el panel web del albergue."""
from django.shortcuts import render, get_object_or_404, redirect
from django.contrib.auth.decorators import login_required
from django.contrib import messages
from django.http import HttpResponse
from django.urls import reverse

from apps.albergues.models import Albergue, Infraestructura


@login_required
def panel_home(request):
    """Dashboard principal del panel."""
    albergue = getattr(request.user, 'albergue', None)
    context = {
        'albergue': albergue,
        'total_mascotas': 0,  # Se actualizará en Fase 2
        'mascotas_disponibles': 0,
        'mascotas_en_proceso': 0,
        'mascotas_adoptadas': 0,
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