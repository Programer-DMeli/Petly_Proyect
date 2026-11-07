from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [
        ("albergues", "0003_necesidadalbergue_donacionalbergue_and_more"),
    ]

    operations = [
        migrations.CreateModel(
            name="RespuestaAutomaticaChatbot",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("pregunta_clave", models.CharField(max_length=180)),
                ("disparadores", models.JSONField(blank=True, default=list)),
                ("respuesta", models.CharField(max_length=1200)),
                ("activa", models.BooleanField(default=True)),
                ("creado_en", models.DateTimeField(auto_now_add=True)),
                ("actualizado_en", models.DateTimeField(auto_now=True)),
                ("albergue", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="respuestas_chatbot",
                    to="albergues.albergue",
                )),
            ],
            options={"ordering": ["pregunta_clave", "id"]},
        ),
        migrations.AddConstraint(
            model_name="respuestaautomaticachatbot",
            constraint=models.UniqueConstraint(
                fields=("albergue", "pregunta_clave"),
                name="albergue_chatbot_pregunta_unica",
            ),
        ),
    ]
