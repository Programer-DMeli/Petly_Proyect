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

## Entrega externa de cambios de estado (US-45 → US-41)

1. Arranca Spring con Flyway y aplica las migraciones de Django en la base compartida. El vínculo es `acceso_user.usuario_spring_id → usuarios.id` a partir de `acceso.0022`. `albergues.propietario_django_id` referencia la cuenta local `acceso_user.id` a partir de `albergues.0009`. Los tres canales se consultan en `preferencias_notificacion`, con los valores por defecto de Spring cuando no hay preferencias guardadas.
2. Configura `FRONTEND_URL` para que apunte a la web accesible por los destinatarios. Activa el backend SMTP y sus credenciales si vas a recibir correo en una bandeja. El backend de desarrollo escribe el correo en archivos.
3. Para FCM, instala `requirements-fcm.txt` en el entorno del worker. Configura `AVISOS_FCM_PROJECT` y las credenciales ADC de una identidad autorizada a enviar FCM en ese proyecto. En Android, compila con `-PpetlyFirebaseAppId`, `-PpetlyFirebaseApiKey`, `-PpetlyFirebaseSenderId` y `-PpetlyFirebaseProjectId` del mismo proyecto. Registra en Firebase el application ID del APK que instales, incluido el sufijo `.integration` si lo usas. Concede permiso de notificaciones en Android 13 o superior.
4. Para WhatsApp, configura el endpoint `/messages` de Meta, su token, el nombre y el idioma de una plantilla aprobada con dos parámetros posicionales en el cuerpo, en orden estado y enlace. Para la prueba, usa el número de envío de prueba y agrega el número de la cuenta titular como destinatario permitido. El teléfono del titular se lee de Spring y debe incluir el prefijo internacional.
5. Ejecuta `python manage.py procesar_avisos_externos --watch` como proceso separado. Sin `--watch` ejecuta un lote. La cola sólo incluye transiciones registradas a partir de la migración y del despliegue de este código.

Cada fila de `EntregaExterna` guarda el canal, la huella del destino, los intentos y el resultado. `ACEPTADA` indica que el proveedor aceptó el envío. `REINTENTAR` se usa para fallos previos al envío o un HTTP 429, hasta cinco intentos. `INCIERTA` requiere conciliación manual con el proveedor para evitar duplicados tras un timeout o una interrupción. El worker revalida la preferencia y el destino antes del intento. Android elimina localmente la vinculación y las notificaciones al salir. Si el servidor no responde, reintenta la revocación con el proceso activo y al volver a iniciar la aplicación.

Para la aceptación, usa dos cuentas con preferencias opuestas y registra sus dispositivos. Genera una transición nueva por combinación, prueba email, push y WhatsApp por separado y los tres apagados. Luego habilita email y push en la titular, deja Android en segundo plano o cierra su actividad y verifica recepción en la bandeja externa y en el sistema Android. Abre ambos enlaces y comprueba el número de solicitud. La segunda cuenta no debe recibir el evento ni poder abrirlo con la ruta autenticada.
