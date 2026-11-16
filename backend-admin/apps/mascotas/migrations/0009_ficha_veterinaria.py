from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [("mascotas", "0008_pedidoafiche_datos_afiche_pedidoafiche_error_codigo")]

    operations = [
        migrations.AddField(model_name="mascota", name="estado_salud_general", field=models.TextField(blank=True, default="", verbose_name="Estado de salud general"), preserve_default=False),
        migrations.AddField(model_name="mascota", name="evidencia_vacunacion", field=models.ImageField(blank=True, null=True, upload_to="mascotas/veterinaria/vacunacion/", verbose_name="Evidencia de vacunación")),
        migrations.AddField(model_name="mascota", name="evidencia_esterilizacion", field=models.ImageField(blank=True, null=True, upload_to="mascotas/veterinaria/esterilizacion/", verbose_name="Evidencia de esterilización")),
    ]
