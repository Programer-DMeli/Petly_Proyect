from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("acceso", "0017_revision_manual_kyc"),
    ]

    operations = [
        migrations.AlterField(
            model_name="solicitudevento",
            name="tipo",
            field=models.CharField(
                choices=[
                    ("REGISTRO_HISTORICO", "Registro histórico"),
                    ("SOLICITUD_CREADA", "Solicitud creada"),
                    ("OBSERVADA", "Solicitud observada"),
                    ("APROBADA", "Solicitud aprobada"),
                    ("RECHAZADA", "Solicitud rechazada"),
                    ("CANCELADA", "Solicitud cancelada"),
                    ("CONTRATO_GENERADO", "Contrato generado"),
                    ("CONTRATO_ENVIADO", "Contrato enviado a firma"),
                    ("CONTRATO_FIRMADO", "Contrato firmado"),
                    ("CONTRATO_APROBADO_CORREO", "Contrato aprobado por correo"),
                    ("CONTRATO_RECHAZADO", "Contrato rechazado"),
                    ("CONTRATO_EXPIRADO", "Contrato expirado"),
                    ("ENTREGADA", "Mascota entregada"),
                ],
                db_index=True,
                max_length=24,
            ),
        ),
    ]
