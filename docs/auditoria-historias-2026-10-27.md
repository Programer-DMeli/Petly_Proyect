# Auditoría rápida de historias — 27 de octubre de 2026

## Alcance y criterio

Esta revisión compara las historias identificadas en `PLANNING.md`, `docs/sprint-02-descripciones-originales.md`, `docs/comparacion-backlog-sprint-02.md` y la evidencia de Sprint 1 con los informes locales más recientes: `docs/integracion-sprint2-revision.md` y `docs/estado-compatibilidad-sprint2.md`. El informe `docs/evidencias/integracion-local-2026-10-27.md` es diagnóstico inicial y quedó reemplazado por `docs/integracion-sprint2-revision.md`.

“Implementada técnicamente” no equivale a aceptación Scrum: la revisión cruzada del equipo y, donde corresponda, la configuración de servicios externos todavía pueden estar pendientes. Esta auditoría cubre las historias enumeradas en los documentos encontrados (US-01–US-30, US-46–US-50 y US-59); no hay en el repositorio una especificación completa de las 75 historias de visión, así que no se atribuye estado a historias sin criterios documentados.

## Historias con evidencia de implementación

| Historia | Estado según evidencia disponible | Pendiente para cierre |
| --- | --- | --- |
| US-01–US-05 | Implementadas técnicamente: motor determinista, ordenamiento, recálculo, desglose y configuración versionada. Evidencia en `estado-compatibilidad-sprint2.md`. | Revisión/aceptación del equipo; validar pesos como decisión de producto. |
| US-06–US-08, US-10 | Integradas entre Android/React, Spring y Django; persistencia, permisos, decisiones, historial y cancelación comprobados. | Revisión visual y aceptación del encargado de Integración. |
| US-11–US-13, US-15–US-18, US-21, US-24, US-46 | Implementadas en el alcance Sprint 1 descrito en `docs/entrega-sprint-01.md`; no consta aceptación cruzada final. | Cierre Scrum/revisión cruzada; SMTP externo de US-24 no se acredita. |
| US-14, US-19, US-20 | Favoritos, fotos privadas de vivienda y horarios disponibles integrados y comprobados según `integracion-sprint2-revision.md`. | Revisión del equipo. |
| US-28 | Afiche PNG de 1080 × 1350 generado y descargado en recorrido integrado. | Revisión del equipo; publicación en redes no forma parte del alcance. |
| US-50 | CRUD de eventos del albergue y consulta pública integrados y verificados. | Revisión del equipo. |
| US-29–US-30 | Adaptadores, endpoints y revisión humana desde React implementados. La evidencia Gemini registra respuestas válidas, pero también fallos intermitentes y un recorrido real conjunto con guardado aún no cerrado. | Repetir el recorrido real exitoso de generación, revisión humana y guardado con proveedor disponible. |
| US-23 | Panel ADMIN de KYC: lista, descarga privada, decisión manual con motivo y auditoría de revisor/fecha implementados. | Recorrido de aceptación con caso KYC de prueba; no sustituye consulta RENIEC automática de US-22. |
| US-49 | Implementada en Django y React: necesidades abiertas públicas, gestión del propietario y registro/consulta privada de aportes. Migración aplicada a la base local tras respaldo. | Revisión funcional del flujo por el equipo. |

## Historias parciales o pendientes

| Historia | Estado | Brecha concreta |
| --- | --- | --- |
| US-09 | Parcial | Borrador/adaptador disponibles, pero faltan proveedor acordado y configurado, credenciales, webhook público y términos aprobados. No hay firma real. |
| US-22 | Parcial / condicionada | Formulario y almacenamiento privado existen; la consulta automática requiere credenciales y acceso autorizado a un proveedor RENIEC compatible, no disponibles en este entorno. No se marca verificado sin respuesta real. |
| US-25 | Pendiente | No consta segundo factor TOTP ni recuperación de acceso de albergue. |
| US-26 | Parcial | Comparte la capacidad de borrador asistido, pero el proveedor real no completa de forma consistente el recorrido integrado de guardar/publicar. US-29 cubre una variante de generación con alcance distinto; no asumir equivalencia total. |
| US-27 | Pendiente | No consta propuesta de recorte/encuadre con IA y confirmación del albergue. |
| US-47 | Pendiente | No consta gestión de trabajadores/permisos de un albergue. |
| US-48 | Pendiente | La ficha institucional existe parcialmente; faltan reseñas verificadas ligadas a adopciones concluidas. |
| US-49 | Implementada en este cambio | API, pantallas y migración aplicadas a la base local; falta aceptación funcional. |
| US-59 | Fuera del alcance documentado | PLANNING lo excluye de la entrega vigente; no hay criterios ni evidencia suficiente para evaluar cumplimiento. |

## Corrección de estado documental

Se consolida aquí la lectura del avance: el informe inicial `docs/evidencias/integracion-local-2026-10-27.md` describía brechas de integración antes de las correcciones. Para el estado actual de solicitudes, favoritos, horarios, fotos privadas y eventos prevalece `docs/integracion-sprint2-revision.md`. Para compatibilidad e IA, prevalece `docs/estado-compatibilidad-sprint2.md`, incluidos sus límites y fallos intermitentes. US-49 tiene implementación y migración aplicada a la base local tras respaldo. US-09 y US-22 siguen condicionadas por proveedor, credenciales, webhook/entorno y términos que el equipo debe acordar.

## Próximo cierre recomendado

1. Completar la revisión manual/cross-platform indicada en `docs/integracion-sprint2-revision.md` y registrar aceptación o incidencias por historia.
2. Repetir el recorrido real completo de US-29/US-30 hasta guardar una sugerencia revisada.
3. Decidir con el equipo si US-09, US-22/23, US-25–27 y US-47–49 permanecen aplazadas o se incorporan a un sprint con sus dependencias y proveedores resueltos.
