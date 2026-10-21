from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0007_user_bloqueado_hasta_user_intentos_fallidos_and_more"),
        ("mascotas", "0004_alter_mascota_sexo"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AlterField(
            model_name="solicitudadopcion",
            name="postulante",
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.CASCADE, related_name="solicitudes", to="acceso.postulante", verbose_name="Postulante"),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="adoptante",
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name="solicitudes_adopcion", to=settings.AUTH_USER_MODEL, verbose_name="Cuenta adoptante"),
        ),
        migrations.AlterField(
            model_name="solicitudadopcion",
            name="estado",
            field=models.CharField(choices=[("PENDIENTE", "Pendiente"), ("OBSERVADA", "Observada"), ("EN_REVISION", "En Revisión"), ("APROBADA", "Aprobada"), ("RECHAZADA", "Rechazada"), ("CANCELADA", "Cancelada por postulante"), ("ENTREGADA", "Entregada (adopción finalizada)")], db_index=True, default="PENDIENTE", max_length=15, verbose_name="Estado"),
        ),
        migrations.AddConstraint(
            model_name="solicitudadopcion",
            constraint=models.UniqueConstraint(condition=models.Q(estado__in=["PENDIENTE", "OBSERVADA", "EN_REVISION"]), fields=("adoptante", "mascota"), name="unique_active_adoptante_mascota"),
        ),
        migrations.RemoveConstraint(
            model_name="solicitudadopcion",
            name="unique_pending_solicitud_per_mascota",
        ),
        migrations.AddConstraint(
            model_name="solicitudadopcion",
            constraint=models.UniqueConstraint(condition=models.Q(estado__in=["PENDIENTE", "OBSERVADA", "EN_REVISION"]), fields=("postulante", "mascota"), name="unique_pending_solicitud_per_mascota"),
        ),
        migrations.CreateModel(
            name="SolicitudEvento",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("tipo", models.CharField(choices=[("SOLICITUD_CREADA", "Solicitud creada"), ("OBSERVADA", "Solicitud observada"), ("APROBADA", "Solicitud aprobada"), ("RECHAZADA", "Solicitud rechazada"), ("CANCELADA", "Solicitud cancelada"), ("CONTRATO_GENERADO", "Contrato generado"), ("CONTRATO_ENVIADO", "Contrato enviado a firma"), ("CONTRATO_FIRMADO", "Contrato firmado"), ("CONTRATO_RECHAZADO", "Contrato rechazado"), ("CONTRATO_EXPIRADO", "Contrato expirado"), ("ENTREGADA", "Mascota entregada")], max_length=24)),
                ("estado_anterior", models.CharField(blank=True, max_length=20)),
                ("estado_nuevo", models.CharField(max_length=20)),
                ("descripcion", models.TextField(blank=True)),
                ("visible_adoptante", models.BooleanField(default=True)),
                ("ocurrido_en", models.DateTimeField(auto_now_add=True, db_index=True)),
                ("actor", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, to=settings.AUTH_USER_MODEL)),
                ("solicitud", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="eventos", to="acceso.solicitudadopcion")),
            ],
            options={"ordering": ["ocurrido_en", "id"]},
        ),
    ]
