import logging
import time

from django.core.management.base import BaseCommand
from django.db import DatabaseError, close_old_connections

from apps.acceso.entregas import procesar_lote

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Entrega avisos de solicitudes confirmadas. Use --watch para mantener el proceso activo."

    def add_arguments(self, parser):
        parser.add_argument("--watch", action="store_true")

    def handle(self, *args, **options):
        while True:
            close_old_connections()
            try:
                procesar_lote()
            except DatabaseError:
                if not options["watch"]:
                    raise
                logger.exception("No se pudo procesar el lote de avisos")
            if not options["watch"]:
                return
            time.sleep(5)
