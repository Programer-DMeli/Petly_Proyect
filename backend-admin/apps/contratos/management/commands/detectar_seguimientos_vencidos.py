from django.core.management.base import BaseCommand

from apps.contratos.alertas_incumplimiento import generar_alertas_incumplimiento


class Command(BaseCommand):
    help = "Registra alertas únicas para seguimientos postadopción con reporte vencido."

    def handle(self, *args, **options):
        creadas, resueltas = generar_alertas_incumplimiento()
        self.stdout.write(self.style.SUCCESS(
            f"Alertas nuevas: {creadas}. Alertas resueltas: {resueltas}."
        ))
