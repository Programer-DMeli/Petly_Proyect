from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0016_intento_kyc"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AddField(
            model_name="verificacionkyc",
            name="motivo_revision",
            field=models.CharField(blank=True, max_length=500),
        ),
        migrations.AddField(
            model_name="verificacionkyc",
            name="fecha_revision",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="verificacionkyc",
            name="revisado_por",
            field=models.ForeignKey(blank=True, limit_choices_to={"rol": "ADMIN"}, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name="verificaciones_kyc_revisadas", to=settings.AUTH_USER_MODEL),
        ),
        migrations.AlterField(
            model_name="verificacionkyc",
            name="estado",
            field=models.CharField(choices=[("PENDIENTE", "Pendiente de consulta"), ("VERIFICADO", "Verificado por RENIEC"), ("PENDIENTE_REVISION", "Pendiente de revisión"), ("APROBADO_MANUAL", "Aprobado por revisión manual"), ("RECHAZADO_MANUAL", "Rechazado por revisión manual")], db_index=True, default="PENDIENTE", max_length=24),
        ),
    ]
