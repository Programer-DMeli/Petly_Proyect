# Rediseño de los paneles Petly

27 de octubre de 2026. Alcance: frontend React, estilos y pruebas dentro de `web/`.

Las tres imágenes de referencia se utilizaron únicamente para orientar estructura, jerarquía y distribución. Se conservaron el verde Refugio, los tonos menta, el acento cálido y el fondo original de patitas. Se reutilizaron las imágenes locales de Petly y las fotografías proporcionadas por las APIs.

## Cambios

- Navegación principal superior, con grupos por tarea, indicación de sección activa y menú de cuenta. En tableta y móvil se convierte en navegación desplegable.
- ADOPTANTE: Explorar mascotas; Mi adopción (solicitudes, favoritos y eventos); Mi perfil (cuestionario, disponibilidad, autorizaciones e identidad).
- ALBERGUE: Inicio; Mi albergue (datos e infraestructura); Adopciones (mascotas, solicitudes y perfiles autorizados); Comunidad (eventos). Los enlaces a las secciones del panel abren sus formularios existentes.
- ADMIN: inicio con composición amplia y accesos a las herramientas; menú Gestión para albergues/responsables y revisión de identidad. Se conservan los indicadores con datos reales, búsquedas, registro, edición, detalle y eliminación.
- Carruseles manuales de tres mensajes en los inicios de Adoptante y Albergue, con llamadas a funciones existentes, flechas, indicadores y anuncio accesible del mensaje. No hay avance automático.
- Catálogo con filtros laterales en escritorio y desplegables en pantallas pequeñas. Cuatro columnas desde 1200 px; tres entre 960 y 1199; dos entre 600 y 959; una por debajo de 600. Paginación de 12 elementos mediante el parámetro existente `pageSize`.
- Tarjetas uniformes con fotos de proporción original (`object-fit: contain`), disponibilidad, datos, compatibilidad recibida del servidor y acceso al detalle.
- Corazón vacío/lleno para quitar o agregar favoritos, con `aria-pressed`, nombre de la mascota, progreso y mensajes de error. La lista y el catálogo comparten el estado consultado/persistido mediante las APIs existentes.
- Formularios y superficies con márgenes, separación, tipografía y controles consistentes. Se conservaron las rutas, validaciones, permisos y operaciones existentes.

## Accesibilidad

- Enlace para saltar al contenido principal.
- Foco visible en enlaces, botones, formularios y controles del carrusel.
- Menús de tipo disclosure con `aria-expanded`/`aria-controls`, apertura con teclado, ArrowDown para entrar a sus enlaces y Escape con recuperación del foco.
- Diálogos administrativos y de autorización con foco inicial, recorrido de Tab contenido en el diálogo, cierre por Escape cuando corresponde y devolución del foco al control de origen.
- Iconos con nombres accesibles, mensajes de estado y alternativas para fotografías ausentes.
- Carruseles sin reproducción automática y respeto a la preferencia de movimiento reducido.

## Comprobaciones finales

- `npm run build`: TypeScript y Vite correctos.
- `npm run lint`: correcto, sin advertencias.
- 14 pruebas Playwright aprobadas: nueve combinaciones de rol y tamaño, cuatro pruebas de catálogo/seguimiento y una revisión de los tres roles con APIs reales en los tres tamaños.
- Resoluciones: escritorio 1440 × 1000, tableta 834 × 1112 y móvil 390 × 844.
- Se verificaron controles de carrusel, cuatro/dos/una columnas en esas resoluciones, filtros, paginación, favoritos, guardado de cuestionario y horarios, autorizaciones, cancelación, decisiones, publicación/cancelación de eventos y diálogos de administración.
- Comprobación de teclado: entrada y cierre de desplegables, restauración del foco, Tab dentro de los diálogos y Escape.
- Sin desbordamiento horizontal en las resoluciones probadas; sin errores JavaScript en los recorridos finales.
- Las operaciones que modifican datos se probaron con respuestas de API simuladas. La revisión real permitió únicamente consultas e inicio de sesión; bloqueó otras operaciones de escritura.
- Se compararon hashes antes/después: ningún archivo fuera de `web/` fue modificado o creado por este rediseño. No se modificaron backend, esquema, contratos ni reglas de negocio.

Vite se reinició para regenerar una caché de módulos desactualizada después del formateo. La validación final se ejecutó con el servidor reiniciado en `http://localhost:5173`.

## Capturas

En `web/output/redisenio/` hay capturas de los tres roles en las tres resoluciones, con respuestas simuladas y con las APIs locales reales. Los archivos `real-*.png` corresponden a la revisión real. Son evidencias locales; revisar cualquier dato visible antes de compartirlas.

## Repetir las pruebas

Desde `web/`:

```bash
npm run build
npm run lint
npx playwright test tests/redisenio-paneles.spec.js tests/catalog-order.spec.js tests/solicitud-tracking.spec.js
```

Para incluir la revisión real, definir `PETLY_UI_ACCOUNTS` con un archivo privado de cuentas locales. No guardar credenciales en el repositorio:

```bash
PETLY_UI_ACCOUNTS=/ruta/privada/cuentas.json npx playwright test tests/redisenio-paneles.spec.js tests/catalog-order.spec.js tests/solicitud-tracking.spec.js tests/redisenio-lectura-real.spec.js
```

Las pruebas anteriores del proyecto que crean registros reales no se ejecutaron como parte de este rediseño para conservar los datos existentes.
