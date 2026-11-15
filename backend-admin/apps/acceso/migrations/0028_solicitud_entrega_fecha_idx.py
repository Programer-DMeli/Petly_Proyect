from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0027_avisos_reporte_un_dia"),
    ]

    operations = [
        migrations.AddIndex(
            model_name="solicitudadopcion",
            index=models.Index(
                fields=["estado", "fecha_entrega"],
                name="sol_entregada_fecha_idx",
            ),
        ),
    ]
