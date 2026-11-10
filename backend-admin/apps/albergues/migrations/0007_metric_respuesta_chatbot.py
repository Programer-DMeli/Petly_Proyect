from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [
        ("albergues", "0006_retire_legacy_messaging_models"),
    ]

    operations = [
        migrations.CreateModel(
            name="MetricaRespuestaChatbot",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("pregunta_clave", models.CharField(max_length=180)),
                ("fecha", models.DateField()),
                ("consultas", models.PositiveIntegerField(default=1)),
                ("albergue", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="metricas_respuestas_chatbot",
                    to="albergues.albergue",
                )),
            ],
            options={
                "ordering": ["-fecha", "pregunta_clave"],
            },
        ),
        migrations.AddConstraint(
            model_name="metricarespuestachatbot",
            constraint=models.UniqueConstraint(
                fields=("albergue", "pregunta_clave", "fecha"),
                name="albergue_chat_metric_dia_uniq",
            ),
        ),
        migrations.AddIndex(
            model_name="metricarespuestachatbot",
            index=models.Index(fields=["albergue", "fecha"], name="albergue_chat_metric_dia_idx"),
        ),
    ]
