"""Lectura de la autoridad Spring en la base compartida; no duplica sus tablas."""
from django.db import connection
from rest_framework.exceptions import ValidationError
from .models import AdoptanteProfile, Autorizacion


def comprobar_elegibilidad(user, albergue_id, bloquear=False):
    if user.spring_id:
        lock = " FOR UPDATE" if bloquear and connection.features.has_select_for_update else ""
        with connection.cursor() as cursor:
            cursor.execute("SELECT finalizado FROM perfiles_adoptantes WHERE usuario_id=%s" + lock, [user.spring_id])
            perfil = cursor.fetchone()
            cursor.execute("SELECT estado FROM autorizaciones_consulta WHERE adoptante_id=%s AND albergue_id=%s" + lock,
                           [user.spring_id, albergue_id])
            permiso = cursor.fetchone()
        if not perfil or not perfil[0]:
            raise ValidationError({"perfil": "Finaliza tu perfil de estilo de vida y convivencia."})
        if not permiso or permiso[0] != "ACTIVA":
            raise ValidationError({"autorizacion": "Se requiere autorización vigente para este albergue."})
        return
    perfil = AdoptanteProfile.objects.filter(user=user, cuestionario_completado=True).first()
    permiso = Autorizacion.objects.filter(adoptante=user, albergue_id=albergue_id, estado="VIGENTE").first()
    if permiso and not permiso.esta_vigente():
        raise ValidationError({"autorizacion": "La autorización está vencida o revocada."})
    if not perfil or not permiso:
        raise ValidationError({"autorizacion": "Se requiere perfil finalizado y autorización vigente."})
