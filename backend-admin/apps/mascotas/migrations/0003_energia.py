from django.db import migrations, models
class Migration(migrations.Migration):
    dependencies = [("mascotas", "0002_add_temperamento")]
    operations = [migrations.AddField(model_name="mascota", name="energia", field=models.CharField(max_length=20, default="POR_EVALUAR", choices=[("BAJA", "Baja"), ("MEDIA", "Media"), ("ALTA", "Alta"), ("POR_EVALUAR", "Por evaluar")]))]
