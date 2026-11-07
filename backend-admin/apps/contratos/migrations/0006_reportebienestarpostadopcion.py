import django.db.models.deletion
import apps.contratos.storage
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("contratos", "0005_expedientepostadopcion"),
    ]

    operations = [
        migrations.CreateModel(
            name="ReporteBienestarPostAdopcion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("estado_bienestar", models.CharField(
                    choices=[("EXCELENTE", "Excelente"), ("BUENO", "Bueno"), ("REGULAR", "Requiere atención"), ("PREOCUPANTE", "Preocupante")],
                    max_length=12,
                )),
                ("observaciones", models.TextField(blank=True, max_length=2000)),
                ("enviado_en", models.DateTimeField(auto_now_add=True)),
                ("seguimiento", models.OneToOneField(
                    on_delete=django.db.models.deletion.PROTECT,
                    related_name="reporte_bienestar",
                    to="contratos.seguimientopostadopcion",
                )),
            ],
            options={"ordering": ["-enviado_en"]},
        ),
        migrations.CreateModel(
            name="FotoReporteBienestar",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("archivo", models.ImageField(
                    storage=apps.contratos.storage.PrivateContractStorage(),
                    upload_to="seguimientos/reportes/",
                )),
                ("mime_type", models.CharField(choices=[("image/jpeg", "JPEG"), ("image/png", "PNG")], max_length=20)),
                ("tamaño_bytes", models.PositiveIntegerField()),
                ("subido_en", models.DateTimeField(auto_now_add=True)),
                ("reporte", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="fotos",
                    to="contratos.reportebienestarpostadopcion",
                )),
            ],
            options={"ordering": ["id"]},
        ),
    ]
