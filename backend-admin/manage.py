#!/usr/bin/env python
"""Script de gestión de Django (backend administrativo de Petly)."""
import os
import sys


def main():
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "No se pudo importar Django. Comprueba que el entorno virtual está "
            "activado y que las dependencias de requirements.txt están instaladas."
        ) from exc
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()
