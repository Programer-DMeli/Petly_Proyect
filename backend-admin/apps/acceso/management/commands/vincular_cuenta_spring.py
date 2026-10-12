from django.core.management.base import BaseCommand, CommandError
from django.contrib.auth import get_user_model
from django.db import connection, transaction
class Command(BaseCommand):
    help = "Vincula un usuario Django existente con una cuenta Spring; no copia contraseñas."
    def add_arguments(self, parser):
        parser.add_argument("django_id", type=int)
        parser.add_argument("spring_id", type=int)
    @transaction.atomic
    def handle(self, *args, **options):
        with connection.cursor() as cursor:
            cursor.execute("SELECT correo,rol FROM usuarios WHERE id=%s", [options["spring_id"]])
            row = cursor.fetchone()
        if not row or row[1] not in {"ALBERGUE", "ADMIN"}:
            raise CommandError("Cuenta Spring inexistente o rol incorrecto.")
        user = get_user_model().objects.get(pk=options["django_id"])
        if user.is_superuser or user.is_staff:
            raise CommandError("No vincules cuentas técnicas de administración.")
        if user.email.lower() != row[0].lower():
            raise CommandError("Los correos deben coincidir; revisar propiedad antes de vincular.")
        user.spring_id=options["spring_id"]; user.rol=row[1]; user.set_unusable_password()
        user.save(update_fields=["spring_id", "rol", "password"])
        self.stdout.write("Vínculo actualizado sin transferir credenciales.")
