import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0022_renombrar_usuario_spring_id"),
        ("albergues", "0008_retirar_tablas_chat_antiguo"),
    ]
    operations = [
        migrations.AlterField(
            model_name="albergue",
            name="usuario",
            field=models.OneToOneField(
                db_column="propietario_django_id",
                on_delete=django.db.models.deletion.CASCADE,
                related_name="albergue",
                to=settings.AUTH_USER_MODEL,
                verbose_name="Usuario propietario",
            ),
        ),
        migrations.AlterModelTable(name="albergue", table="albergues"),
    ]
