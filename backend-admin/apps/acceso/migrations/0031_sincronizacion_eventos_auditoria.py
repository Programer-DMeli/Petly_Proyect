from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0030_evento_auditoria"),
    ]

    operations = [
        migrations.AddField(
            model_name="eventoauditoria",
            name="sincronizado_spring_en",
            field=models.DateTimeField(blank=True, editable=False, null=True),
        ),
        migrations.AddIndex(
            model_name="eventoauditoria",
            index=models.Index(
                fields=["sincronizado_spring_en", "ocurrido_en"],
                name="acceso_audit_sync_dt",
            ),
        ),
    ]
