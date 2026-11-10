from django.db import migrations


class Migration(migrations.Migration):
    dependencies = [
        ("albergues", "0005_conversacionadopcion_mensajeconversacion"),
        ("mensajeria", "0003_importar_chat_legacy"),
    ]

    # The old tables may contain conversation history. Remove the duplicate
    # models from Django's state without dropping those tables or their data.
    operations = [
        migrations.SeparateDatabaseAndState(
            database_operations=[],
            state_operations=[
                migrations.DeleteModel(name="MensajeConversacion"),
                migrations.DeleteModel(name="ConversacionAdopcion"),
            ],
        ),
    ]
