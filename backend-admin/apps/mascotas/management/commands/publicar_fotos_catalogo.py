from django.core.files.storage import default_storage
from django.core.management.base import BaseCommand, CommandError

from apps.mascotas.models import Mascota
from apps.mascotas.storage import ErrorFotosCatalogo


class Command(BaseCommand):
    help = "Copia a Supabase las fotos principales existentes respetando su ruta en MariaDB."

    def add_arguments(self, parser):
        parser.add_argument("--apply", action="store_true", help="Sube las fotos que faltan en Supabase.")

    def handle(self, *args, **options):
        storage = Mascota._meta.get_field("foto_principal").storage
        if not storage.activo:
            raise CommandError("Configura SUPABASE_URL antes de usar este comando.")

        rutas = (Mascota.objects.order_by().exclude(foto_principal__isnull=True)
                 .exclude(foto_principal="").values_list("foto_principal", flat=True).distinct())
        existentes = pendientes = ausentes = subidas = 0
        for ruta in rutas.iterator():
            try:
                if storage.existe_en_supabase(ruta):
                    existentes += 1
                elif not default_storage.exists(ruta):
                    self.stderr.write(f"Sin copia local ni pública: {ruta}")
                    ausentes += 1
                elif options["apply"]:
                    with default_storage.open(ruta, "rb") as foto:
                        storage.publicar(ruta, foto)
                    subidas += 1
                else:
                    pendientes += 1
            except (ErrorFotosCatalogo, FileNotFoundError) as exc:
                raise CommandError(f"{ruta}: {exc}") from exc
        self.stdout.write(f"Existentes: {existentes}. Pendientes: {pendientes}. Subidas: {subidas}. Ausentes: {ausentes}.")
        if ausentes:
            raise CommandError("Faltan archivos de origen para completar la publicación.")
