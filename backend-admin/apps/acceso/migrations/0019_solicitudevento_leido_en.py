from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [("acceso", "0018_fotos_hogar_privadas")]

    operations = [
        migrations.AddField(
            model_name="solicitudevento",
            name="leido_en",
            field=models.DateTimeField(null=True, blank=True),
        ),
    ]
