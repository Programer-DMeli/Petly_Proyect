from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [("acceso", "0015_documentos_privados_kyc")]

    operations = [
        migrations.AddField(
            model_name="verificacionkyc",
            name="ultimo_intento_en",
            field=models.DateTimeField(blank=True, null=True),
        ),
    ]
