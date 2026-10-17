# Petly móvil (Kotlin + Jetpack Compose)

Experiencia Android del adoptante conectada a Spring Boot (`:8080`).
Namespace y applicationId preexistentes: **`com.petly_app`** (PLANNING v2 §7
propone `com.petly.mobile`, pero se conserva el existente para no romper el
namespace ni la build).

## Comandos (Windows / PowerShell)

```powershell
cd mobile_petly
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Estructura de paquetes (`app/src/main/java/com/petly_app/`)

```
MainActivity.kt            # pantalla inicial de Petly
core/
├── network/               # cliente HTTP y URL base (10.0.2.2 en emulador)
├── session/               # sesión/token del usuario
└── navigation/            # rutas Compose
data/
├── remote/                # llamadas a Spring Boot
├── model/                 # DTOs
└── repository/            # fuentes de datos
ui/
├── auth/                  # registro, acceso, recuperación (US-21, US-24)
├── perfil/                # cuestionario y convivencia (US-16, US-17)
├── catalogo/              # catálogo con filtros (US-12)
└── theme/                 # tema de Petly (preexistente)
```

Las carpetas sin implementar llevan `.gitkeep`; no se crean clases de relleno.

## Conexión con el backend

- Emulador → Spring Boot: `http://10.0.2.2:8080`
- Dispositivo físico → IP local del equipo en el mismo Wi-Fi.
- HTTP solo en desarrollo: usar `networkSecurityConfig` por build type o
  `android:usesCleartextTraffic="false"` en la build de producción.
- Android **nunca** accede directamente a la base de datos.

## Interfaz de acceso

La pantalla de inicio de sesión usa Jetpack Compose y Material 3 con fondo
menta, tipografía serif, marca vectorial y tarjeta blanca según la referencia
visual. Los controles mantienen un área táctil de al menos 48 dp y el formulario
permite desplazarse al abrir el teclado o aumentar el tamaño de fuente.

- Acceso por correo conectado al flujo existente, con errores y bloqueo temporal.
- Contraseña con visibilidad alternable y envío desde el teclado.
- «Crear cuenta» y «Regístrate gratis» abren el registro existente.
- Recuperación conectada al flujo existente.
- Google y Huella muestran un aviso de disponibilidad; su autenticación está pendiente.
- Solo en debug: «Opciones de desarrollo» permite comprobar conexión o abrir
  las pantallas con datos ficticios, sin iniciar una sesión.

`LoginReferencePreview` permite revisar la pantalla a 352 × 796 dp en Android
Studio. El logo es una interpretación vectorial local de la marca mostrada en
la referencia. La paleta de acceso permanece clara también en modo oscuro.

[Captura de la pantalla en el emulador](../docs/evidencias/mobile-acceso-compose.png).

Validación de interfaz sin cuentas ni backend:

```bash
bash gradlew :app:assembleDebug :app:testDebugUnitTest
bash gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.petly_app.LoginDesignInstrumentedTest
```
