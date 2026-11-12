import os

from django.db import migrations


def verificar_entorno(apps, schema_editor):
    if (
        schema_editor.connection.settings_dict["NAME"] == "database_petly"
        and os.environ.get("PETLY_AUTORIZAR_RENOMBRE_IDENTIDAD") != "1"
    ):
        raise RuntimeError("El renombrado de identidad en database_petly requiere autorización de despliegue.")


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0021_avisoexterno_dispositivopush_entregaexterna_and_more"),
    ]
    operations = [
        migrations.RunPython(verificar_entorno, migrations.RunPython.noop),
        migrations.RenameField(
            model_name="user",
            old_name="spring_id",
            new_name="usuario_spring_id",
        ),
    ]
