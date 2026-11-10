import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("albergues", "0004_respuestaautomatica_chatbot"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="ConversacionAdopcion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("creada_en", models.DateTimeField(auto_now_add=True)),
                ("actualizada_en", models.DateTimeField(auto_now=True, db_index=True)),
                ("adoptante", models.ForeignKey(
                    limit_choices_to={"rol": "ADOPTANTE"},
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="conversaciones_adopcion",
                    to=settings.AUTH_USER_MODEL,
                )),
                ("albergue", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="conversaciones_adopcion",
                    to="albergues.albergue",
                )),
            ],
            options={"ordering": ["-actualizada_en", "-id"]},
        ),
        migrations.CreateModel(
            name="MensajeConversacion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("contenido", models.CharField(max_length=4000)),
                ("creado_en", models.DateTimeField(auto_now_add=True, db_index=True)),
                ("conversacion", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="mensajes",
                    to="albergues.conversacionadopcion",
                )),
                ("remitente", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="mensajes_adopcion",
                    to=settings.AUTH_USER_MODEL,
                )),
            ],
            options={"ordering": ["creado_en", "id"]},
        ),
        migrations.AddConstraint(
            model_name="conversacionadopcion",
            constraint=models.UniqueConstraint(
                fields=("adoptante", "albergue"),
                name="conversacion_adoptante_albergue_unica",
            ),
        ),
        migrations.AddIndex(
            model_name="mensajeconversacion",
            index=models.Index(fields=["conversacion", "creado_en"], name="albergues_m_convers_225dfd_idx"),
        ),
    ]
