# Compatibilidad y Gemini: implementación y ejecución local

Fecha de comprobación: 27 de octubre de 2026. Alcance: US-01, US-02, US-03, US-04, US-05, US-29 y US-30.

## Entorno ejecutado

| Componente | Dirección / variante | Comprobación |
| --- | --- | --- |
| MariaDB | `127.0.0.1:13306`, `database_petly` | MariaDB 11.8.8; 33 tablas de Petly |
| Spring Boot | `http://localhost:8080/api/health/` | HTTP 200 |
| Django | `http://localhost:8000/api/health/` | HTTP 200 |
| React | `http://localhost:5173` | HTTP 200, TypeScript/Vite y ESLint correctos |
| Kotlin/Android | Medium_Phone, `com.petly_app.integration` | Instalado y abierto; dos pruebas instrumentales aprobadas |

Se conservó la aplicación `com.petly_app` preexistente. El emulador completó el arranque usando `-gpu host`, Vulkan y cámaras virtuales desactivadas. Las variantes con SwiftShader fallaban por segmentación del emulador, fuera de la aplicación.

## Cambios funcionales y verificación

| Historia | Implementación y evidencia |
| --- | --- |
| US-01 | Motor determinista existente conservado. Datos desconocidos no reciben 100; se corrigió energía nula y tamaño vacío. Pruebas de perfiles compatibles, poco compatibles e incompletos en Spring y recorrido HTTP real. |
| US-02 | Orden descendente por puntaje, empate por ID ascendente e insuficientes al final; filtros y paginación conservan orden. Solo disponibles de albergues activos. Se comprobó exclusión después de cambiar disponibilidad. React/Android muestran el puntaje y colores orientativos. |
| US-03 | Sin caché de resultados: cada consulta usa perfil y reglas actuales. Android refresca catálogo al guardar y cancela consejos pendientes al recargar detalle. Guardado inválido conserva perfil y resultados. Comprobado en Spring, HTTP, React y Android real. |
| US-04 | React/Android muestran vivienda, rutina, convivencia, pesos, aportes, cobertura y versión. La suma se redondea `HALF_UP` al entero mostrado. Criterios desconocidos con peso cero se identifican y no causan errores. Verificado en pruebas y pantalla Android. |
| US-05 | Pantalla `/administracion/compatibilidad` y API ADMIN existentes integradas. Pesos enteros, no negativos y suma 100; se rechazan fracciones sin cambiar configuración. Pruebas verifican persistencia, versión, fecha, responsable y denegación para adoptante/albergue. Las pruebas de cambios válidos usan H2 aislada; el recorrido sobre la base permanente no sustituye sus pesos. |
| US-29 | Adaptador Gemini con imagen real, límites, JSON estructurado y validación conservadora. Borrador separado del texto vigente: editar, descartar, aplicar al editor y guardar explícitamente. Pruebas Django/React verifican conservación ante error. Hubo generación real válida mediante el adaptador; la prueba HTTP de visión registra fallos intermitentes y aún no confirma el recorrido real completo de guardado desde Django. |
| US-30 | Foto y perfil de la mascota, categorías permitidas, explicación y cita de observación registrada. Una cita meramente visual no respalda conducta y se devuelve Por evaluar. La sugerencia no se guarda automáticamente; el albergue puede aplicarla, corregirla y guardar. Persistencia/permisos probados en Django y React con respuestas simuladas; hubo respuesta real válida del adaptador y del endpoint. Falta cerrar la confirmación HTTP real conjunta con US-29. |

No se declaran cerradas las verificaciones reales pendientes de US-29/US-30. El proveedor ha respondido con 503 y timeouts: las interfaces permiten reintentar y conservan los datos vigentes. El detalle HTTP de intentos exitosos y fallidos queda en las evidencias; no se presenta una prueba simulada como Gemini real.

## Decisiones respecto del plan adjunto

Se respetó la instrucción explícita del integrador: Gemini aporta texto complementario, nunca puntuaciones, orden ni decisiones. Por ello no se añadieron embeddings, filtros excluyentes nuevos ni un reranker que cambie el catálogo. Los empates siguen resolviéndose por ID. Los campos de puntuación que pudieran aparecer en una respuesta IA se descartan.

`GET /api/v1/matches/mascota/{id}` reutiliza el resultado de reglas para el adoptante autenticado. `POST /api/v1/matches/mascota/{id}/explicacion` consulta Gemini a petición del usuario, sin bloquear la carga del catálogo ni del desglose. Solo se envían atributos públicos de la mascota y el resultado de criterios; no se envían nombre, correo, DNI, fotografías privadas del hogar ni el perfil completo del adoptante.

## Configuración

La clave está en `.env.gemini` en la raíz, excluido de Git y con permisos `600`. Los dos backends lo cargan. No usar variables `VITE_*` ni incluir la clave en Android.

```dotenv
GEMINI_API_KEY=<clave privada>
GEMINI_MODEL=gemini-3.1-flash-lite
GEMINI_TIMEOUT_SECONDS=30
```

El modelo `gemini-2.5-flash` del plan respondió 404 para la cuenta. El modelo recomendado `3.8-flash` respondió indisponibilidad; `3.1-flash-lite` devolvió respuestas reales válidas y quedó configurado. El timeout se limita a 1–60 segundos. El cliente Android amplía el tiempo de lectura únicamente al pedir consejos, manteniendo los tiempos previos en las otras operaciones.

Sin clave, los consejos devuelven `NO_CONFIGURADO`; si falla el proveedor, `NO_DISPONIBLE`. El motor de reglas funciona en ambos casos. Django conserva el adaptador anterior `PET_DESCRIPTION_API_URL` / `PET_DESCRIPTION_API_TOKEN` como alternativa cuando no hay clave Gemini.

## Ejecución

Desde la raíz del proyecto, cada proceso duradero se mantiene en su terminal:

```bash
python3 scripts/mariadb-permanente.py start
backend-admin/.venv/bin/python scripts/servicios-locales.py
```

El lanzador arranca Spring, aplica migraciones pendientes sin borrar la base y arranca Django. Si los puertos están ocupados, no detiene procesos ajenos. Sus logs quedan en `~/.local/share/petly/logs/`; pueden contener información privada y no deben subirse.

```bash
cd web
npm run dev -- --host 127.0.0.1 --strictPort
```

Android, desde `mobile_petly`, con el emulador conectado:

```bash
bash gradlew :app:testDebugUnitTest :app:installDebug -PpetlyIntegrationInstall=true --offline
```

El emulador usa `10.0.2.2:8080` y `10.0.2.2:8000` para acceder a los backends del equipo.

## Evidencias y repetición

- `docs/evidencias/verificacion-compatibilidad-sprint2.json`: resumen de pruebas, con tipo de base y distinción IA simulada/real.
- `docs/evidencias/compatibilidad-sprint2-real.json`: recorrido HTTP real de reglas, persistencia, paginación y permisos.
- `docs/evidencias/gemini-real-sprint2.json`: respuestas reales válidas de descripción y temperamento del adaptador.
- `docs/evidencias/gemini-http-real-sprint2.json`: intentos reales a través de los backends; incluye fallos manejados.
- `docs/evidencias/android-arranque-sprint2.png`: aplicación instalada y abierta en el emulador.

Pruebas aprobadas: 69 Spring (H2), 80 Django (SQLite), 25 unitarias Kotlin, dos instrumentales Android y cuatro React/Playwright. Una instrumental comprueba pantalla con IA simulada; la otra consulta Spring/MariaDB reales y admite el estado disponible o el fallo manejado de Gemini, sin modificar el puntaje.

```bash
python3 scripts/pruebas-spring.py test
backend-admin/.venv/bin/python backend-admin/manage.py test apps.mascotas apps.acceso.tests.test_sprint2 --settings=config.test_settings
cd web
npm run build
npm run lint
npx playwright test tests/sprint-two-compatibilidad.spec.js tests/catalog-order.spec.js
```

Los scripts `verificar-compatibilidad-sprint2.py` y `verificar-gemini-http.py` crean/utilizan cuentas y mascotas identificadas de prueba en la base local. El segundo realiza peticiones reales al proveedor y confirma datos únicamente en esa mascota de prueba cuando los tres flujos responden correctamente. Las credenciales temporales se guardan en `/tmp/petly-matching-fixtures.json` con permisos privados, no en las evidencias.
