import apps.acceso.kyc_storage
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [("acceso", "0014_verificacion_kyc")]

    operations = [
        migrations.AddField(
            model_name="verificacionkyc",
            name="documento_anverso",
            field=models.ImageField(blank=True, null=True, storage=apps.acceso.kyc_storage.PrivateKycStorage(), upload_to=apps.acceso.kyc_storage.nombre_archivo_kyc),
        ),
        migrations.AddField(
            model_name="verificacionkyc",
            name="documento_reverso",
            field=models.ImageField(blank=True, null=True, storage=apps.acceso.kyc_storage.PrivateKycStorage(), upload_to=apps.acceso.kyc_storage.nombre_archivo_kyc),
        ),
    ]
