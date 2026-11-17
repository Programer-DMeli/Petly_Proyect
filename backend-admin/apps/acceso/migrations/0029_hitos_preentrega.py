import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("acceso", "0028_solicitud_entrega_fecha_idx"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AddField(
            model_name="solicitudadopcion",
            name="estado_revision_veterinaria",
            field=models.CharField(
                choices=[
                    ("PENDIENTE", "Pendiente"),
                    ("APTA", "Apta para la adopción"),
                    ("OBSERVADA", "Observada"),
                    ("RECHAZADA", "Rechazada"),
                ],
                db_index=True,
                default="PENDIENTE",
                max_length=12,
                verbose_name="Revisión veterinaria",
            ),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="ficha_veterinaria_revisada",
            field=models.JSONField(blank=True, default=dict),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="observaciones_revision_veterinaria",
            field=models.TextField(blank=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="fecha_revision_veterinaria",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="revision_veterinaria_por",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="revisiones_veterinarias_solicitud",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="fecha_recojo_programado",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="mensaje_recojo",
            field=models.TextField(blank=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="recojo_programado_por",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="recojos_solicitud_programados",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="entrega_satisfaccion",
            field=models.PositiveSmallIntegerField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="entrega_observaciones",
            field=models.TextField(blank=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="entrega_confirmada_en",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="solicitudadopcion",
            name="entrega_confirmada_por",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.SET_NULL,
                related_name="entregas_solicitud_confirmadas",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddConstraint(
            model_name="solicitudadopcion",
            constraint=models.CheckConstraint(
                check=(
                    models.Q(entrega_satisfaccion__isnull=True)
                    | models.Q(entrega_satisfaccion__gte=1, entrega_satisfaccion__lte=5)
                ),
                name="solicitud_entrega_satisfaccion_1_5",
            ),
        ),
        migrations.AlterField(
            model_name="solicitudevento",
            name="tipo",
            field=models.CharField(
                choices=[
                    ("NUEVA_COINCIDENCIA", "Nueva mascota para ti"),
                    ("REGISTRO_HISTORICO", "Registro histórico"),
                    ("SOLICITUD_CREADA", "Solicitud creada"),
                    ("OBSERVADA", "Solicitud observada"),
                    ("APROBADA", "Solicitud aprobada"),
                    ("VETERINARIA_APTA", "Revisión veterinaria aprobada"),
                    ("VETERINARIA_OBSERVADA", "Revisión veterinaria observada"),
                    ("VETERINARIA_RECHAZADA", "Revisión veterinaria rechazada"),
                    ("RECOJO_PROGRAMADO", "Recojo programado"),
                    ("RECHAZADA", "Solicitud rechazada"),
                    ("CANCELADA", "Solicitud cancelada"),
                    ("CONTRATO_GENERADO", "Contrato generado"),
                    ("CONTRATO_ENVIADO", "Contrato enviado a firma"),
                    ("CONTRATO_FIRMADO", "Contrato firmado"),
                    ("CONTRATO_RECHAZADO", "Contrato rechazado"),
                    ("CONTRATO_EXPIRADO", "Contrato expirado"),
                    ("ENTREGADA", "Mascota entregada"),
                    ("SEGUIMIENTO_PROGRAMADO", "Nuevo seguimiento postadopción"),
                    ("RECORDATORIO_REPORTE", "Próxima entrega de reporte"),
                    ("REPORTE_1_DIA", "Reporte vence en un día"),
                    ("INCUMPLIMIENTO_REPORTE", "Alerta roja de incumplimiento"),
                ],
                db_index=True,
                max_length=24,
            ),
        ),
    ]
