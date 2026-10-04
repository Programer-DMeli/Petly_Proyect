# Sprint 1 — alcance y estado

Fuente: `PLANNING.md` v2, sección 9. **Estado: pendiente.** Esta preparación de estructura no implementa ninguna historia.

## Objetivo

Base integrada donde el albergue registre mascotas y actualice su disponibilidad, y el adoptante acceda, complete su perfil y consulte el catálogo.

## Historias (estimaciones originales del PDF, como referencia histórica)

| Historia | Alcance | Horas PDF | Responsable | Estado |
| --- | --- | --- | --- | --- |
| US-21 | Acceso, tokens, bloqueo por intentos fallidos y pruebas | 3 h | Persona B | Pendiente |
| US-16 | Cuestionario de vivienda y estilo de vida | 5 h | Persona C | Pendiente |
| US-11 | Registro de mascotas, CRUD y validaciones | 3,5 h | Persona A | Pendiente |
| US-46 | Registro de información del albergue | 3 h | Persona A | Pendiente |
| US-12 | Búsqueda, filtros, paginación y pruebas | 2,5 h | Persona C | Pendiente |
| US-17 | Datos de convivencia | 2 h | Persona C | Pendiente |
| US-24 | Recuperación de contraseña por correo | 2 h | Persona B | Pendiente |
| US-18 | Consulta autorizada de postulantes | 3 h | Persona B | Pendiente |
| US-13 | Estados Disponible / En Proceso / Adoptado | 2 h | Persona A | Pendiente |

El PDF suma 26 horas en 33 tareas pero declara 30 horas: pendiente de aclarar si las 4 restantes son reserva.

## Decisiones aprobadas (v2) que condicionan la implementación

- **US-21:** registro solo con correo y contraseña; cuentas de albergue y administrador creadas por el equipo sin contraseñas en Git; bloqueo de 5 minutos tras 2 fallos consecutivos; limitación de peticiones; sin registro por redes sociales.
- **US-11:** una fotografía por mascota, JPEG o PNG, máximo 5 MB, validada en Django; no subir fotos privadas a Git.
- **US-12:** filtros por especie, tamaño, edad y temperamento; categorías predefinidas con «Por evaluar»; no inferir temperamento con IA.
- **US-24:** solo correo, token de un solo uso de 15 minutos; SMS fuera de este sprint.
- **US-18:** consulta solo con autorización expresa hacia un albergue específico, revocable; sin antecedentes externos ni historial en el Sprint 1.

## Pantallas (indicación literal del usuario)

> React: acceso, recuperación, catálogo, cuestionario, panel del albergue. Kotlin: experiencia completa del adoptante y el portal React del adoptante pasa al siguiente sprint

Interpretación registrada:

- **Sprint 1 React:** acceso y recuperación (personal del albergue y administrador), panel del albergue (perfil institucional, registro de mascotas, estados, consulta autorizada de perfiles) y administración general mínima.
- **Sprint 1 Kotlin:** experiencia completa del adoptante dentro de las historias de este sprint.
- **Sprint 2 React:** portal del adoptante; sus carpetas y rutas ya están previstas en `web/` pero **no** están terminadas.
- «Experiencia completa» no incluye matchmaking, solicitudes, chatbot ni otras historias fuera del sprint.

## Fuera del alcance del Sprint 1

Motor de compatibilidad, IA, solicitudes de adopción, chatbot, mensajería ni seguimiento.

## Condiciones comunes de cierre

Ver `PLANNING.md` v2 §9.5 y `docs/acuerdos-equipo.md`. Resumen: integración real con persistencia, revisión cruzada, verificación de acceso autorizado y denegado, documentación sin secretos y evidencia de las comprobaciones.
