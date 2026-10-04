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

## Estado

Solo está la pantalla inicial (`MainActivity`) con la presentación de Petly.
El resto de pantallas del adoptante (registro, acceso, recuperación, catálogo,
detalle, cuestionario y autorización de perfil) están previstas para el
Sprint 1 según PLANNING v2 §9.2 y siguen **pendientes**.
