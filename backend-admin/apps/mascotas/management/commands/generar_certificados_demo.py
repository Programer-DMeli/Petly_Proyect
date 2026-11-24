import os
from getpass import getpass
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from pyhanko.sign.signers import SimpleSigner

from apps.mascotas.certificados_demo import firmar_pdf_demo, generar_identidad_demo, pdf_veterinario_demo
from apps.mascotas.certificados_veterinarios import verificar_firma_pdf
from apps.mascotas.models import CertificadoVeterinario


class Command(BaseCommand):
    help = "Genera los PDFs ficticios firmados de US-59 con una identidad académica reutilizable."

    def add_arguments(self, parser):
        parser.add_argument("--identidad", type=Path, required=True, help="Carpeta fuera del repositorio.")
        parser.add_argument("--salida", type=Path, required=True, help="Carpeta de los PDFs.")

    def handle(self, *args, **options):
        identidad = options["identidad"].resolve()
        if identidad.is_relative_to(settings.BASE_DIR.parent.resolve()):
            raise CommandError("La carpeta de identidad debe estar fuera del repositorio.")
        salida = options["salida"].resolve()
        tipos = CertificadoVeterinario.Tipo.values
        nombres = [
            salida / f"muestra-{tipo.lower()}-{estado}.pdf"
            for tipo in tipos for estado in ("original", "firmada")
        ]
        if any(ruta.exists() for ruta in nombres):
            raise CommandError("Elige otra carpeta de salida para conservar las muestras existentes.")

        identidad.mkdir(mode=0o700, parents=True, exist_ok=True)
        clave = identidad / "clave-demo.pem"
        publico = identidad / "certificado-demo.pem"
        if clave.is_file() != publico.is_file():
            raise CommandError("La identidad está incompleta. Restaura ambos archivos o usa otra carpeta.")
        contrasena = os.getenv("PETLY_CERTIFICADOS_CLAVE_PASSWORD") or getpass("Contraseña de la clave de prueba: ")
        if not contrasena:
            raise CommandError("La clave de prueba necesita una contraseña.")
        contraseña_bytes = contrasena.encode()
        if not clave.exists():
            clave_cifrada, certificado = generar_identidad_demo(contraseña_bytes)
            descriptor = os.open(clave, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            try:
                with os.fdopen(descriptor, "wb") as archivo:
                    archivo.write(clave_cifrada)
                with publico.open("xb") as archivo:
                    archivo.write(certificado)
            except OSError:
                clave.unlink(missing_ok=True)
                raise

        firmante = SimpleSigner.load(str(clave), str(publico), key_passphrase=contraseña_bytes)
        if firmante is None:
            raise CommandError("No se pudo abrir la identidad con la contraseña indicada.")
        salida.mkdir(parents=True, exist_ok=True)
        for tipo, original, firmado in zip(tipos, nombres[::2], nombres[1::2], strict=True):
            contenido = pdf_veterinario_demo(tipo)
            documento = firmar_pdf_demo(contenido, firmante)
            verificar_firma_pdf(documento, firmante.signing_cert)
            with original.open("xb") as archivo:
                archivo.write(contenido)
            with firmado.open("xb") as archivo:
                archivo.write(documento)
        self.stdout.write(f"PDFs de prueba verificados en {salida}")
        self.stdout.write(f"Certificado público en {publico}")
