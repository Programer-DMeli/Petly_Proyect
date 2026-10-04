# Petly backend administrativo (Django)

Gestión de albergues y mascotas; posteriormente evaluación de solicitudes,
publicaciones con IA y seguimiento. Django + Django REST Framework. Puerto **8000**.

## Requisitos

- Python 3.14 (comprobado en este equipo).
- MariaDB de XAMPP con la base `database_petly` (ver `../docs/instalacion.md`).

## Puesta en marcha (Windows / PowerShell)

```powershell
cd backend-admin
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env      # .env queda fuera de Git; ajustar credenciales
python manage.py check           # chequeos del proyecto
python manage.py runserver       # http://127.0.0.1:8000
```

Comprobación del endpoint de salud:

```powershell
curl http://127.0.0.1:8000/api/health/     # {"status":"UP"}
```

### Alternativa si `mysqlclient` no instala en Windows

```powershell
pip uninstall mysqlclient
pip install PyMySQL==1.2.3
```

Y añadir al inicio de `config/__init__.py`:

```python
import pymysql
pymysql.install_as_MySQLdb()
```

Documentar el cambio en el README del equipo antes de compartirlo.

## Estructura

```
backend-admin/
├── manage.py
├── requirements.txt        # dependencias fijadas
├── .env.example            # variables de entorno de ejemplo (sin secretos)
├── config/                 # settings, urls, wsgi, asgi
└── apps/
    ├── albergues/          # información institucional del albergue (US-46)
    ├── mascotas/           # catálogo y estados (US-11, US-12, US-13)
    └── acceso/             # validación de tokens de Spring Boot (US-21, US-18)
```

Cada aplicación sigue la organización indicada en `PLANNING.md` v2 §7:
`__init__.py`, `apps.py`, `models.py`, `serializers.py`, `views.py`, `urls.py`,
`migrations/__init__.py` y `tests/__init__.py`.

## Reglas importantes

- **Migraciones de negocio:** ninguna todavía. Django será el responsable de
  las tablas de albergues y mascotas cuando toque implementar (ver
  `../database/modelo-datos.md`).
- **Cuentas:** Django no crea cuentas de negocio ni guarda contraseñas de
  Petly; esas viven en Spring Boot. Los usuarios internos de Django
  (`contrib.auth`), si se usan, se documentan y se separan de ellos.
- **CORS:** solo orígenes locales explícitos (`DJANGO_ALLOWED_ORIGINS`).
- **Secretos:** `.env` nunca se sube a Git. `.env.example` lleva valores de ejemplo.
- **Fotografías:** se guardarán en almacenamiento local (`media/`), excluido de Git.
