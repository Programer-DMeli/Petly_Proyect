from django.db import migrations, models
class Migration(migrations.Migration):
    dependencies = [("acceso", "0004_merge_perfil")]
    operations = [migrations.AddField(model_name="user", name="spring_id", field=models.PositiveBigIntegerField(null=True, blank=True, unique=True))]
