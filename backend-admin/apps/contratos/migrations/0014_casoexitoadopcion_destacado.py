from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("contratos", "0013_casoexitoadopcion"),
    ]

    operations = [
        migrations.AddField(
            model_name="casoexitoadopcion",
            name="destacado",
            field=models.BooleanField(
                default=False,
                help_text="Incluye este caso aprobado en los testimonios públicos del albergue.",
                verbose_name="Destacado",
            ),
        ),
    ]
