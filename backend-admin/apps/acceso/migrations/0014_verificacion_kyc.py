import django.core.validators
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0013_alter_solicitudadopcion_postulante_and_more"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="VerificacionKyc",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("dni", models.CharField(max_length=8, validators=[django.core.validators.RegexValidator("^\\d{8}$", "El DNI debe contener exactamente ocho dígitos.")], verbose_name="DNI")),
                ("nombres", models.CharField(max_length=100)),
                ("apellidos", models.CharField(max_length=100)),
                ("estado", models.CharField(choices=[("PENDIENTE", "Pendiente de consulta"), ("VERIFICADO", "Verificado por RENIEC"), ("PENDIENTE_REVISION", "Pendiente de revisión")], db_index=True, default="PENDIENTE", max_length=24)),
                ("coincide", models.BooleanField(blank=True, null=True)),
                ("proveedor", models.CharField(blank=True, max_length=80)),
                ("referencia_reniec", models.CharField(blank=True, max_length=160)),
                ("fecha_consulta", models.DateTimeField(blank=True, null=True)),
                ("error_servicio", models.CharField(blank=True, max_length=500)),
                ("creado_en", models.DateTimeField(auto_now_add=True)),
                ("actualizado_en", models.DateTimeField(auto_now=True)),
                ("adoptante", models.OneToOneField(limit_choices_to={"rol": "ADOPTANTE"}, on_delete=django.db.models.deletion.CASCADE, related_name="verificacion_kyc", to=settings.AUTH_USER_MODEL)),
            ],
            options={
                "verbose_name": "Verificación KYC",
                "verbose_name_plural": "Verificaciones KYC",
                "ordering": ["-actualizado_en"],
            },
        ),
    ]
