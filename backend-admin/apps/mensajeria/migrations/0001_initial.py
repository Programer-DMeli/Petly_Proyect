import apps.mensajeria.models
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    initial = True
    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("acceso", "0020_criteriosaviso_solicitudevento_destinatario_and_more"),
        ("albergues", "0004_respuestaautomatica_chatbot"),
    ]
    operations = [
        migrations.CreateModel(name="BienvenidaAlbergue", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("texto", models.CharField(max_length=1000)),
            ("actualizado_en", models.DateTimeField(auto_now=True)),
            ("albergue", models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name="bienvenida_chat", to="albergues.albergue")),
        ]),
        migrations.CreateModel(name="Conversacion", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("creada_en", models.DateTimeField(auto_now_add=True)),
            ("actualizada_en", models.DateTimeField(auto_now=True, db_index=True)),
            ("adoptante", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="conversaciones_adoptante", to=settings.AUTH_USER_MODEL)),
            ("albergue", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="conversaciones", to="albergues.albergue")),
            ("solicitud", models.OneToOneField(on_delete=django.db.models.deletion.PROTECT, related_name="conversacion", to="acceso.solicitudadopcion")),
        ], options={"ordering": ["-actualizada_en", "-id"]}),
        migrations.CreateModel(name="ParticipanteConversacion", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("leido_hasta", models.DateTimeField(blank=True, null=True)),
            ("conversacion", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="participantes", to="mensajeria.conversacion")),
            ("usuario", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="participaciones_chat", to=settings.AUTH_USER_MODEL)),
        ]),
        migrations.AddConstraint(model_name="participanteconversacion", constraint=models.UniqueConstraint(fields=("conversacion", "usuario"), name="chat_participante_unico")),
        migrations.CreateModel(name="Mensaje", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("texto", models.TextField(blank=True, max_length=4000)),
            ("bienvenida_automatica", models.BooleanField(default=False)),
            ("creado_en", models.DateTimeField(auto_now_add=True, db_index=True)),
            ("conversacion", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="mensajes", to="mensajeria.conversacion")),
            ("remitente", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="mensajes_chat", to=settings.AUTH_USER_MODEL)),
        ], options={"ordering": ["creado_en", "id"]}),
        migrations.CreateModel(name="AdjuntoMensaje", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("archivo", models.FileField(storage=apps.mensajeria.models.PrivateMessageStorage(), upload_to=apps.mensajeria.models.private_message_upload_path)),
            ("nombre_original", models.CharField(max_length=255)),
            ("content_type", models.CharField(max_length=100)),
            ("tamaño_bytes", models.PositiveIntegerField()),
            ("mensaje", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="adjuntos", to="mensajeria.mensaje")),
        ]),
        migrations.CreateModel(name="BloqueoConversacion", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("motivo", models.CharField(blank=True, max_length=500)),
            ("creado_en", models.DateTimeField(auto_now_add=True)),
            ("bloqueador", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="bloqueos_chat", to=settings.AUTH_USER_MODEL)),
            ("conversacion", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="bloqueos", to="mensajeria.conversacion")),
        ]),
        migrations.AddConstraint(model_name="bloqueoconversacion", constraint=models.UniqueConstraint(fields=("conversacion", "bloqueador"), name="chat_bloqueo_unico")),
        migrations.CreateModel(name="ReporteConversacion", fields=[
            ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
            ("motivo", models.CharField(choices=[("ACOSO", "Acoso o amenazas"), ("FRAUDE", "Fraude o suplantación"), ("CONTENIDO", "Contenido inapropiado"), ("OTRO", "Otro motivo")], max_length=12)),
            ("detalle", models.CharField(blank=True, max_length=1000)),
            ("creado_en", models.DateTimeField(auto_now_add=True)),
            ("conversacion", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="reportes", to="mensajeria.conversacion")),
            ("reportante", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name="reportes_chat", to=settings.AUTH_USER_MODEL)),
        ], options={"ordering": ["-creado_en"]}),
    ]
