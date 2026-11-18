import uuid

from django.db import migrations, models
import django.utils.timezone


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0029_hitos_preentrega"),
    ]

    operations = [
        migrations.CreateModel(
            name="EventoAuditoria",
            fields=[
                (
                    "id",
                    models.BigAutoField(
                        auto_created=True,
                        primary_key=True,
                        serialize=False,
                        verbose_name="ID",
                    ),
                ),
                (
                    "evento_id",
                    models.UUIDField(default=uuid.uuid4, editable=False, unique=True),
                ),
                ("actor_usuario_id", models.PositiveBigIntegerField(blank=True, null=True)),
                ("actor_rol", models.CharField(max_length=20)),
                ("accion", models.CharField(max_length=80)),
                (
                    "origen",
                    models.CharField(
                        choices=[("DJANGO", "Django")],
                        default="DJANGO",
                        editable=False,
                        max_length=10,
                    ),
                ),
                ("recurso_tipo", models.CharField(blank=True, max_length=80)),
                ("recurso_id", models.CharField(blank=True, max_length=100)),
                (
                    "ocurrido_en",
                    models.DateTimeField(default=django.utils.timezone.now, editable=False),
                ),
            ],
            options={
                "verbose_name": "Evento de auditoría",
                "verbose_name_plural": "Eventos de auditoría",
                "db_table": "acceso_eventoauditoria",
                "ordering": ["-ocurrido_en", "-id"],
            },
        ),
        migrations.AddIndex(
            model_name="eventoauditoria",
            index=models.Index(
                fields=["actor_usuario_id", "ocurrido_en"],
                name="acceso_audit_actor_dt",
            ),
        ),
        migrations.AddIndex(
            model_name="eventoauditoria",
            index=models.Index(
                fields=["accion", "ocurrido_en"],
                name="acceso_audit_accion_dt",
            ),
        ),
        migrations.AddIndex(
            model_name="eventoauditoria",
            index=models.Index(fields=["ocurrido_en"], name="acceso_audit_fecha"),
        ),
    ]
