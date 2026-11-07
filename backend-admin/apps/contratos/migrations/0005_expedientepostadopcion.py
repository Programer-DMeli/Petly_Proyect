# Generated for US-38: formal adoption follow-up closure.
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("contratos", "0004_solicitud_asesoria_veterinaria"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="ExpedientePostAdopcion",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("calificacion_adaptacion", models.PositiveSmallIntegerField()),
                ("observaciones_cierre", models.TextField(blank=True)),
                ("cerrado_en", models.DateTimeField(auto_now_add=True)),
                ("cerrado_por", models.ForeignKey(
                    null=True,
                    on_delete=django.db.models.deletion.SET_NULL,
                    related_name="expedientes_post_adopcion_cerrados",
                    to=settings.AUTH_USER_MODEL,
                )),
                ("solicitud", models.OneToOneField(
                    on_delete=django.db.models.deletion.PROTECT,
                    related_name="expediente_post_adopcion",
                    to="acceso.solicitudadopcion",
                )),
            ],
            options={"ordering": ["-cerrado_en"]},
        ),
        migrations.AddConstraint(
            model_name="expedientepostadopcion",
            constraint=models.CheckConstraint(
                check=models.Q(calificacion_adaptacion__gte=1, calificacion_adaptacion__lte=5),
                name="contratos_exp_calif_1_5",
            ),
        ),
    ]
