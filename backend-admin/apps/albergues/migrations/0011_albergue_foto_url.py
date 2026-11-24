from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("albergues", "0010_remove_metricarespuestachatbot_albergue_chat_metric_dia_uniq_and_more"),
    ]

    operations = [
        migrations.AddField(
            model_name="albergue",
            name="foto_url",
            field=models.URLField(blank=True, max_length=500, verbose_name="URL de la foto"),
        ),
    ]
