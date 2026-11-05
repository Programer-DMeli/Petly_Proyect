import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0017_revision_manual_kyc"),
        ("contratos", "0002_contratoadopcion_documento_firmado"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="SeguimientoPostAdopcion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("programado_para", models.DateTimeField(db_index=True)),
                ("modalidad", models.CharField(choices=[("VISITA", "Visita"), ("LLAMADA", "Llamada")], max_length=10)),
                ("estado", models.CharField(choices=[("PROGRAMADO", "Programado"), ("REALIZADO", "Realizado"), ("CANCELADO", "Cancelado")], db_index=True, default="PROGRAMADO", max_length=12)),
                ("notas", models.CharField(blank=True, max_length=500)),
                ("creado_en", models.DateTimeField(auto_now_add=True)),
                ("actualizado_en", models.DateTimeField(auto_now=True)),
                ("creado_por", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name="seguimientos_post_adopcion_creados", to=settings.AUTH_USER_MODEL)),
                ("solicitud", models.ForeignKey(limit_choices_to={"estado": "ENTREGADA"}, on_delete=django.db.models.deletion.PROTECT, related_name="seguimientos_post_adopcion", to="acceso.solicitudadopcion")),
            ],
            options={"ordering": ["programado_para", "id"]},
        ),
        migrations.AddIndex(
            model_name="seguimientopostadopcion",
            index=models.Index(fields=["estado", "programado_para"], name="contratos_se_estado_992825_idx"),
        ),
    ]
