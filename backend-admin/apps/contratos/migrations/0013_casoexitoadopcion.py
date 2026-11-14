import apps.contratos.models
import apps.contratos.storage
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0027_avisos_reporte_un_dia"),
        ("contratos", "0012_seguimientopostadopcion_cancelado_en"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="CasoExitoAdopcion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("historia", models.TextField(max_length=4000)),
                ("estado", models.CharField(
                    choices=[
                        ("PENDIENTE", "Pendiente de revisión"),
                        ("APROBADO", "Aprobado"),
                        ("RECHAZADO", "Rechazado"),
                    ],
                    db_index=True,
                    default="PENDIENTE",
                    max_length=12,
                )),
                ("revisado_en", models.DateTimeField(blank=True, null=True)),
                ("motivo_revision", models.CharField(blank=True, max_length=500)),
                ("publicado_en", models.DateTimeField(blank=True, null=True)),
                ("creado_en", models.DateTimeField(auto_now_add=True)),
                ("actualizado_en", models.DateTimeField(auto_now=True)),
                ("autor", models.ForeignKey(
                    on_delete=django.db.models.deletion.PROTECT,
                    related_name="historias_adopcion",
                    to=settings.AUTH_USER_MODEL,
                )),
                ("revisado_por", models.ForeignKey(
                    blank=True,
                    null=True,
                    on_delete=django.db.models.deletion.SET_NULL,
                    related_name="casos_exito_revisados",
                    to=settings.AUTH_USER_MODEL,
                )),
                ("solicitud", models.OneToOneField(
                    on_delete=django.db.models.deletion.PROTECT,
                    related_name="caso_exito",
                    to="acceso.solicitudadopcion",
                )),
            ],
            options={"ordering": ["-creado_en", "-id"]},
        ),
        migrations.CreateModel(
            name="FotoCasoExito",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("archivo", models.ImageField(
                    storage=apps.contratos.storage.PrivateContractStorage(),
                    upload_to=apps.contratos.models.ruta_foto_caso_exito,
                )),
                ("mime_type", models.CharField(
                    choices=[("image/jpeg", "JPEG"), ("image/png", "PNG")],
                    max_length=20,
                )),
                ("tamaño_bytes", models.PositiveIntegerField()),
                ("subido_en", models.DateTimeField(auto_now_add=True)),
                ("caso", models.ForeignKey(
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name="fotos",
                    to="contratos.casoexitoadopcion",
                )),
            ],
            options={"ordering": ["id"]},
        ),
    ]
