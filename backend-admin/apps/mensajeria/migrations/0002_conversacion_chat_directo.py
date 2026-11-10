import django.db.models.deletion
from django.db.models import Count
from django.db import migrations, models


def consolidar_conversaciones(apps, schema_editor):
    alias = schema_editor.connection.alias
    Conversacion = apps.get_model("mensajeria", "Conversacion")
    Mensaje = apps.get_model("mensajeria", "Mensaje")
    Participante = apps.get_model("mensajeria", "ParticipanteConversacion")
    Bloqueo = apps.get_model("mensajeria", "BloqueoConversacion")
    Reporte = apps.get_model("mensajeria", "ReporteConversacion")

    pares = list(
        Conversacion.objects.using(alias)
        .values("adoptante_id", "albergue_id")
        .annotate(total=Count("id"))
        .filter(total__gt=1)
    )
    for par in pares:
        hilos = list(Conversacion.objects.using(alias).filter(
            adoptante_id=par["adoptante_id"], albergue_id=par["albergue_id"],
        ).order_by("id"))
        conservar = hilos[0]
        for duplicada in hilos[1:]:
            Mensaje.objects.using(alias).filter(conversacion_id=duplicada.pk).update(conversacion_id=conservar.pk)
            for participante in Participante.objects.using(alias).filter(conversacion_id=duplicada.pk):
                existente = Participante.objects.using(alias).filter(
                    conversacion_id=conservar.pk, usuario_id=participante.usuario_id,
                ).first()
                if existente:
                    fechas = [f for f in (existente.leido_hasta, participante.leido_hasta) if f]
                    if fechas:
                        existente.leido_hasta = max(fechas)
                        existente.save(update_fields=["leido_hasta"])
                    participante.delete()
                else:
                    participante.conversacion_id = conservar.pk
                    participante.save(update_fields=["conversacion"])
            for bloqueo in Bloqueo.objects.using(alias).filter(conversacion_id=duplicada.pk):
                existente = Bloqueo.objects.using(alias).filter(
                    conversacion_id=conservar.pk, bloqueador_id=bloqueo.bloqueador_id,
                ).first()
                if existente:
                    if not existente.motivo and bloqueo.motivo:
                        existente.motivo = bloqueo.motivo
                        existente.save(update_fields=["motivo"])
                    bloqueo.delete()
                else:
                    bloqueo.conversacion_id = conservar.pk
                    bloqueo.save(update_fields=["conversacion"])
            Reporte.objects.using(alias).filter(conversacion_id=duplicada.pk).update(conversacion_id=conservar.pk)
            if conservar.solicitud_id is None and duplicada.solicitud_id is not None:
                conservar.solicitud_id = duplicada.solicitud_id
            conservar.creada_en = min(conservar.creada_en, duplicada.creada_en)
            conservar.actualizada_en = max(conservar.actualizada_en, duplicada.actualizada_en)
            duplicada.delete()
        conservar.save(update_fields=["solicitud", "creada_en", "actualizada_en"])


class Migration(migrations.Migration):
    dependencies = [("mensajeria", "0001_initial")]

    operations = [
        migrations.AlterField(
            model_name="conversacion",
            name="solicitud",
            field=models.OneToOneField(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.PROTECT,
                related_name="conversacion",
                to="acceso.solicitudadopcion",
            ),
        ),
        migrations.RunPython(consolidar_conversaciones, migrations.RunPython.noop),
        migrations.AddConstraint(
            model_name="conversacion",
            constraint=models.UniqueConstraint(
                fields=("adoptante", "albergue"),
                name="chat_adoptante_albergue_unico",
            ),
        ),
    ]
