# Actividades de Meliton para cerrar el Sprint 1

Fecha de revisión: 11 de octubre de 2026 (America/Lima).
Base: PLANNING.md §9 y código integrado desde las ramas remotas.
Esta lista es una propuesta de ejecución; no declara historias terminadas ni añade estimaciones aprobadas.

## Responsabilidad

Meliton coordina US-16 (cuestionario), US-12 (catálogo) y US-17 (convivencia), y lidera Android e integración. Angie implementa las APIs y persistencia Spring Boot; Mayra proporciona los datos Django. Coordinar una historia incluye obtener esas dependencias y demostrar el recorrido completo.

## 1. Preparar una base verificable — prioridad inmediata

- [ ] Crear una rama de trabajo desde main integrado; llevar los cambios Android locales conservados a esa rama antes de seguir desarrollando.
- [ ] Acordar con Angie y Mayra el contrato: rutas, métodos, JSON, IDs, campos nulos, errores, paginación, edad y unidades, categorías de temperamento y estados.
- [ ] Resolver con Angie la diferencia de autenticación: Android usa /api/auth/register y email; Spring Boot usa /api/auth/registro y correo. Revisar también la respuesta de /me.
- [ ] Documentar el entorno reproducible: Java 21, SDK Android, MariaDB, configuración local, servicios requeridos y orden de migraciones. Mantener secretos fuera de Git.
- [ ] Comprobar con Mayra el grafo de migraciones Django: 0002_add_tiene_ninos y 0003_alter_adoptanteprofile_apellidos_and_more dejan dos extremos en acceso. Resolverlo de forma compatible con las bases existentes.
- [ ] Coordinar con Angie y Mayra la corrección de los bloqueos de compilación detectados en Spring Boot: DTOs de recuperación con Lombok sin dependencia y AuthService que usa getters ausentes en RegistroRequest. Volver a ejecutar Maven antes de integrar Android.
- [ ] Comprobar registro, login y una consulta privada desde emulador; verificar conectividad desde teléfono físico si forma parte de la demostración.

Cierre: otro integrante puede reproducir el entorno y Android recibe respuestas reales del backend.

## 2. US-16 — Cuestionario de vivienda y estilo de vida

Estado observado: formulario, ViewModel y validaciones presentes; repositorio real pendiente. PerfilApi está vacío y AppContainer usa PendingPerfilRepository fuera de la demostración.

- [ ] Acordar con Angie los campos de vivienda, horas fuera, presupuesto y estado parcial/finalizado, sus validaciones y el contrato para consultar/guardar/actualizar el perfil propio.
- [ ] Obtener de Angie la API autenticada y persistencia Spring Boot; evitar duplicar la propiedad del perfil en Django.
- [ ] Implementar PerfilApi, DTOs y repositorio real; conectarlo desde AppContainer.
- [ ] Guardar respuestas parciales, recuperar las existentes, editarlas y finalizar el cuestionario. El guardado debe sobrevivir al reinicio de la app y del servidor.
- [ ] Mostrar carga, guardado, errores de campo, fallo de red y sesión vencida; conservar lo escrito cuando falle una petición y evitar envíos duplicados.
- [ ] Verificar entradas inválidas, valores límites, respuestas parciales/finalizadas y recuperación después de volver a entrar.
- [ ] Comprobar con dos cuentas que un adoptante no puede leer ni modificar el perfil del otro, también mediante llamadas directas a la API.
- [ ] Entregar evidencia y solicitar revisión de Mayra.

Cierre: vivienda, rutina y presupuesto se guardan y recuperan desde la cuenta real con permisos comprobados.

## 3. US-17 — Convivencia

Estado observado: tieneNinos y tieneOtrasMascotas están en PerfilDraft; falta persistencia integrada. Django añadió tiene_ninos, pero eso no sustituye la API de perfiles Spring Boot acordada.

- [ ] Acordar con Angie la diferencia entre sin responder, Sí y No; conservarla entre interfaz, JSON y base de datos.
- [ ] Integrar niños y otras mascotas en el mismo cuestionario de US-16 y usar una única fuente de datos.
- [ ] Verificar guardar, recuperar y cambiar Sí a No y No a Sí, tanto en borrador como al finalizar.
- [ ] Comprobar privacidad entre cuentas y comportamiento ante sesión vencida.
- [ ] Entregar evidencia y solicitar revisión de Angie.

Cierre: los valores de convivencia persisten, pueden actualizarse y no son modificables por otro adoptante. El cálculo de compatibilidad queda fuera de esta historia.

## 4. US-12 — Catálogo, búsqueda y filtros

Estado observado: catálogo, detalle y filtros funcionan en demostración; CatalogoApi está vacío y el repositorio real está pendiente. Django ya añadió temperamento y paginación; Android debe consultar Spring Boot según el alcance.

- [ ] Acordar con Mayra y Angie especie, tamaño, edad, temperamento (incluido Por evaluar), formato de fotografía, estado y estructura de paginación.
- [ ] Obtener de Angie endpoints Spring Boot para catálogo y detalle que consulten los datos reales gestionados por Django, sin crear migraciones sobre tablas ajenas.
- [ ] Implementar CatalogoApi, DTOs y repositorio real; mantener la demostración identificada explícitamente.
- [ ] Integrar búsqueda y filtros individuales/combinados; conservarlos al cambiar de página y reiniciar la página al modificar un filtro.
- [ ] Resolver carga, lista vacía, ausencia de coincidencias, error con reintento, imagen ausente y mascota inexistente.
- [ ] Comprobar detalle y navegación de regreso conservando filtros; impedir que respuestas antiguas reemplacen resultados de búsquedas más recientes.
- [ ] Verificar con datos conocidos cada filtro, combinaciones y varias páginas; incluir una mascota Por evaluar.
- [ ] Pedir a Mayra registrar una mascota y cambiar su estado; comprobar en Android que ambos cambios aparecen al volver a consultar (apoya US-11 y US-13).
- [ ] Entregar evidencia y solicitar revisión de Angie.

Cierre: catálogo y detalle muestran datos persistidos reales a través de Spring Boot, con búsqueda, filtros y paginación coherentes.

## 5. Apoyos de integración que también corresponden a Meliton

- [ ] US-21: revisar con Angie bloqueo de cinco minutos tras dos fallos, acceso válido, token ausente/inválido/vencido y tratamiento correcto de errores Android.
- [ ] US-24: implementar las pantallas y llamadas Android de recuperación con los endpoints reales /api/auth/password/reset y /api/auth/password/reset/confirm. Acordar cómo llega el enlace del correo a la pantalla de nueva contraseña. Angie debe demostrar expiración y uso único; el código actual no muestra consumo persistente del token.
- [ ] US-18: integrar selección de albergue, autorización, listado y revocación cuando Angie entregue la API. Mayra debe sustituir la comprobación provisional que devuelve autorizado=True por existir perfil; Spring Boot y Django también discrepan actualmente entre POST y GET para esa consulta interna. No darla por integrada con esa respuesta provisional.
- [ ] US-46 y US-13: realizar la revisión asignada con cuentas de distintos albergues, permisos y datos de demostración; conservar evidencia.

## 6. Entrega profesional

- [ ] Mantener cambios pequeños por historia, con commits claros y revisión cruzada antes de integrar.
- [ ] Ejecutar pruebas de lógica de validaciones y consulta; añadir pruebas de integración sobre persistencia y permisos que no queden cubiertos. Comprobar los flujos Android en emulador.
- [ ] Revisar accesibilidad básica: etiquetas de campos, mensajes legibles, controles durante carga, desplazamiento y teclado.
- [ ] Preparar datos de prueba sin información personal y una demostración reproducible: acceso → cuestionario → guardar/recuperar → convivencia → catálogo filtrado → detalle → cambio de estado desde el albergue.
- [ ] Actualizar docs/api.md, docs/sprint-01.md y README con resultados comprobados, responsable, revisor y pendientes; adjuntar capturas o reporte de pruebas sin tokens ni contraseñas.
- [ ] Cerrar cada historia solo cuando cumple PLANNING §9.4–9.5, tiene persistencia real, errores y permisos verificados y aprobación del revisor.

Orden recomendado: contratos y entorno → US-16 y US-17 juntas → US-12 → apoyos de integración → evidencia y revisión. No estimar horas definitivas hasta confirmar la disponibilidad del equipo y las APIs pendientes.

## Verificación durante la integración

- Sintaxis de los archivos Python revisada correctamente y git diff --check sin errores.
- Maven test intentado: falló durante compilación, antes de ejecutar pruebas, por los errores Java descritos arriba.
- Typecheck web intentado: no ejecutado porque tsc no está instalado en el entorno local.
- No se verificaron arranque, MariaDB, correo, Android ni el recorrido completo; la fusión no equivale al cierre del sprint.
