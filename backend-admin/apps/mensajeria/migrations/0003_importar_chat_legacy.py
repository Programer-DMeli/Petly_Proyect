from django.db import migrations


def importar_conversaciones(apps, schema_editor):
    alias = schema_editor.connection.alias
    ConversacionAntigua = apps.get_model("albergues", "ConversacionAdopcion")
    MensajeAntiguo = apps.get_model("albergues", "MensajeConversacion")
    ConversacionNueva = apps.get_model("mensajeria", "Conversacion")
    MensajeNuevo = apps.get_model("mensajeria", "Mensaje")
    Participante = apps.get_model("mensajeria", "ParticipanteConversacion")

    for antigua in ConversacionAntigua.objects.using(alias).order_by("id").iterator():
        nueva = ConversacionNueva.objects.using(alias).filter(
            adoptante_id=antigua.adoptante_id,
            albergue_id=antigua.albergue_id,
        ).order_by("id").first()
        if nueva is None:
            nueva = ConversacionNueva.objects.using(alias).create(
                solicitud_id=None,
                adoptante_id=antigua.adoptante_id,
                albergue_id=antigua.albergue_id,
            )
        Participante.objects.using(alias).get_or_create(
            conversacion_id=nueva.pk, usuario_id=antigua.adoptante_id,
        )
        # Recupera al usuario propietario mediante la relación histórica.
        albergue = antigua.albergue
        Participante.objects.using(alias).get_or_create(
            conversacion_id=nueva.pk, usuario_id=albergue.usuario_id,
        )
        for mensaje in MensajeAntiguo.objects.using(alias).filter(conversacion_id=antigua.pk).order_by("creado_en", "id"):
            copia = MensajeNuevo.objects.using(alias).create(
                conversacion_id=nueva.pk,
                remitente_id=mensaje.remitente_id,
                texto=mensaje.contenido,
            )
            MensajeNuevo.objects.using(alias).filter(pk=copia.pk).update(creado_en=mensaje.creado_en)
        ConversacionNueva.objects.using(alias).filter(pk=nueva.pk).update(
            creada_en=min(nueva.creada_en, antigua.creada_en),
            actualizada_en=max(nueva.actualizada_en, antigua.actualizada_en),
        )


class Migration(migrations.Migration):
    dependencies = [
        ("mensajeria", "0002_conversacion_chat_directo"),
        ("albergues", "0005_conversacionadopcion_mensajeconversacion"),
    ]

    operations = [migrations.RunPython(importar_conversaciones, migrations.RunPython.noop)]
