# Acuerdos del equipo

Fuente: `PLANNING.md` v2, secciones 2, 9.3 y 9.5. No se inventan nombres ni nuevas estimaciones.

## Reparto acordado

- **Persona A:** lidera Django y el panel del albergue.
- **Persona B:** lidera Spring Boot y la web del adoptante.
- **Persona C:** lidera Kotlin y la integración.

Cada persona tiene tres historias como responsable en el Sprint 1. El responsable coordina la historia completa, sus dependencias y su evidencia; no desarrolla necesariamente todos sus componentes.

## Historias del Sprint 1 con revisor

| Historia | Responsable | Revisor |
| --- | --- | --- |
| US-21 Registro y acceso | Persona B | Persona C |
| US-16 Cuestionario | Persona C | Persona A |
| US-11 Registro de mascotas | Persona A | Persona B |
| US-46 Registro de albergues | Persona A | Persona C |
| US-12 Búsqueda y filtros | Persona C | Persona B |
| US-17 Convivencia | Persona C | Persona B |
| US-24 Recuperación por correo | Persona B | Persona A |
| US-18 Consulta autorizada | Persona B | Persona A |
| US-13 Estado de la mascota | Persona A | Persona C |

Ninguna historia se aprueba únicamente por su propio responsable.

## Reglas de cierre de historia

- Criterios cumplidos en las interfaces comprometidas para este sprint y revisión de otro integrante.
- Backend e interfaz integrados con datos persistidos; no son pantallas o respuestas simuladas.
- Se verifican errores relevantes, acceso autorizado y acceso denegado.
- Cambios de API, configuración y migraciones documentados, sin secretos.
- Si falta componentes o verificación, se registra como pendiente y la historia **no** se declara terminada.

## Pendientes abiertos (no corregir en silencio)

- Confirmar capacidad semanal real y reestimar el alcance.
- Aclarar la diferencia entre 26 y 30 horas del PDF del Sprint 1.
- Fijar fechas exactas de entrega.
- Definir las categorías de temperamento (incluido «Por evaluar») y su correspondencia entre registro y filtros.
- Definir el formato del token compartido entre Spring Boot y Django antes de integrar autenticación.
