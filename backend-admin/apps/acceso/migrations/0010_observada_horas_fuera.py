from django.db import migrations, models


def renombrar_estado(apps, schema_editor):
    Solicitud = apps.get_model("acceso", "SolicitudAdopcion")
    Solicitud.objects.filter(estado="EN_REVISION").update(estado="OBSERVADA")


def revertir_estado(apps, schema_editor):
    Solicitud = apps.get_model("acceso", "SolicitudAdopcion")
    Solicitud.objects.filter(estado="OBSERVADA").update(estado="EN_REVISION")


class Migration(migrations.Migration):
    dependencies = [("acceso", "0009_merge_20261013_0842")]

    operations = [
        migrations.RemoveConstraint(
            model_name="solicitudadopcion",
            name="unique_pending_solicitud_per_mascota",
        ),
        migrations.RunPython(renombrar_estado, revertir_estado),
        migrations.AlterField(
            model_name="solicitudadopcion",
            name="estado",
            field=models.CharField(
                choices=[
                    ("PENDIENTE", "Pendiente"), ("OBSERVADA", "Observada"),
                    ("APROBADA", "Aprobada"), ("RECHAZADA", "Rechazada"),
                    ("CANCELADA", "Cancelada por postulante"),
                    ("ENTREGADA", "Entregada (adopción finalizada)"),
                ], db_index=True, default="PENDIENTE", max_length=15,
                verbose_name="Estado",
            ),
        ),
        migrations.AddConstraint(
            model_name="solicitudadopcion",
            constraint=models.UniqueConstraint(
                condition=models.Q(estado__in=["PENDIENTE", "OBSERVADA"]),
                fields=("postulante", "mascota"),
                name="unique_pending_solicitud_per_mascota",
            ),
        ),
        migrations.AddField(
            model_name="adoptanteprofile",
            name="horas_fuera",
            field=models.PositiveSmallIntegerField(
                blank=True, null=True, verbose_name="Horas fuera de casa al día"
            ),
        ),
    ]
