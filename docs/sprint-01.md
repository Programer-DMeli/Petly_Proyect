# Sprint 1 — estado de implementación

Actualización: 12 de octubre de 2026. Rama `feature/petly_proyect`; sin commit ni push de esta implementación.

El recorrido Android → Spring → datos persistidos y la integración administrativa Django fueron verificados sobre una instancia aislada MariaDB 11.8.8. No se modificó la base del equipo. Ver contratos en `api.md`, configuración en `instalacion.md` y evidencia en `entrega-sprint-01.md`.

| Historia | Principal / revisor | Estado formal | Implementación y pendiente |
| --- | --- | --- | --- |
| US-16 Cuestionario | Meliton / Mayra | Parcial | Implementación y pruebas técnicas completas: borrador, finalización, edición, persistencia, errores y privacidad. Falta revisión de Mayra. |
| US-17 Convivencia | Meliton / Angie | Parcial | Implementación y pruebas técnicas completas: false distinto de null, persistencia y actualización. Falta revisión de Angie. |
| US-12 Catálogo | Meliton / Angie | Parcial | Implementación y pruebas técnicas completas: consulta Spring real, filtros combinados, edad total, paginación, detalle/foto y disponibilidad. Falta revisión de Angie. |
| US-21 Acceso | Angie / Meliton | Parcial | Apoyo Android y unificación JWT completos técnicamente; registro/login, bloqueo, tokens y permisos comprobados. Falta revisión integral de Angie y acceso/panel React del equipo. |
| US-24 Recuperación | Angie / Mayra | Parcial | Apoyo Android, enlace y API corregida: correo SMTP de pruebas recibido, 15 minutos, uso único, sesión anterior revocada. Falta revisión A/B y SMTP del entorno del equipo. |
| US-18 Consulta autorizada | Angie / Mayra | Parcial | Consentimiento/revocación Android+Spring y consulta protegida Django comprobados. Falta vista del albergue en React y revisión A/B. |
| US-46 Albergues | Mayra / Meliton | Parcial | Registro y aislamiento de propietarios comprobados por API. Falta panel React, vínculo/verificación de cuentas reales del equipo y revisión de Mayra. |
| US-13 Disponibilidad | Mayra / Meliton | Parcial | Cambio Django y consulta Spring verificados con datos reales. Falta revisión integral del panel albergue por Mayra. |
| US-11 Mascotas | Mayra / Angie | Parcial | CRUD existente, imagen validada y campos de energía/temperamento integrados. Falta revisión del alcance completo y panel React por A/B. |

Parcial expresa las condiciones de cierre de PLANNING §9.5, que requieren revisión de otro integrante; no significa que estas pantallas sigan usando simulaciones. El modo de muestra Android sigue disponible únicamente en debug y está identificado como ficticio; el acceso real utiliza repositorios HTTP.

## Revisión cruzada solicitada al equipo, sin envío de mensajes

- Mayra: revisar US-16, migraciones Django aditivas, identidad `spring_id`, validación de imágenes, consentimiento Django y separación de acceso técnico/negocio. Conservar identidades antiguas y revisar su vínculo antes de aplicar cambios en la base existente.
- Angie: revisar US-17/US-12, contratos JSON, las tablas Spring/Flyway y las correcciones de acceso, bloqueo y recuperación. Validar las decisiones propuestas de campos/unidades/visibilidad.
- Ambos: confirmar decisiones propuestas, revisar panel React pendiente, verificar XAMPP 10.4.32, teléfono físico y SMTP del entorno propio; conservar evidencia.

No se añadieron matchmaking, IA, solicitudes, chatbot ni seguimiento. Se conservaron implementaciones antiguas de solicitudes fuera del enrutamiento activo para revisión futura. Las 26/30 horas, capacidad y fecha exacta de entrega continúan pendientes de acuerdo; no se inventaron estimaciones nuevas.
