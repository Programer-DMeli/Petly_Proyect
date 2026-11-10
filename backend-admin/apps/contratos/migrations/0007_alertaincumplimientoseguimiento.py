from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [
        ("contratos", "0006_reportebienestarpostadopcion"),
    ]

    operations = [
        migrations.CreateModel(
            name="AlertaIncumplimientoSeguimiento",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("estado", models.CharField(choices=[("PENDIENTE", "Pendiente"), ("RESUELTA", "Resuelta")], db_index=True, default="PENDIENTE", max_length=10)),
                ("generada_en", models.DateTimeField(auto_now_add=True)),
                ("resuelta_en", models.DateTimeField(blank=True, null=True)),
                ("seguimiento", models.OneToOneField(on_delete=django.db.models.deletion.PROTECT, related_name="alerta_incumplimiento", to="contratos.seguimientopostadopcion")),
            ],
            options={"ordering": ["-generada_en", "-id"]},
        ),
    ]
