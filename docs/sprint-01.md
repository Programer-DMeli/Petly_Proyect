# Sprint 1 — estado de implementación

Actualización: 12 de octubre de 2026. Rama feature/petly_proyect. La entrega Android previa se publicó en cc83ea3; React y las correcciones posteriores son cambios locales pendientes de publicación.

## Alcance vigente y resultado

Una aplicación React para ADMIN, ALBERGUE y ADOPTANTE; Kotlin para ADOPTANTE. Spring es autoridad única de acceso y recuperación; Django gestiona instituciones/mascotas y consulta autorizada. MariaDB compartida con propiedad de migraciones separada. El portal React adoptante se adelantó al Sprint 1 por instrucción posterior del usuario.

| Historia | Principal / revisor | Implementación técnica | Revisión humana |
| --- | --- | --- | --- |
| US-21 Acceso | Angie / Meliton | Registro adoptante, acceso de tres roles en React, acceso adoptante Android; JWT, hash, bloqueo y cuota. | Pendiente |
| US-16 Cuestionario | Meliton / Mayra | React/Android, borrador/finalización, persistencia Spring, edición, validación y privacidad. | Pendiente |
| US-17 Convivencia | Meliton / Angie | React/Android; Sí, No y sin responder; actualización persistida en Spring. | Pendiente |
| US-12 Catálogo | Meliton / Angie | React/Android desde Spring: búsqueda, filtros combinados, paginación, foto, detalle y disponibilidad. | Pendiente |
| US-24 Recuperación | Angie / Mayra | Correo SMTP de pruebas recibido; enlace web/Android, token 15 minutos/uso único y sesiones revocadas. | Pendiente |
| US-18 Consulta autorizada | Angie / Mayra | Consentimiento/revocación React y Android; listado y consulta del albergue por Django con verificación Spring. | Pendiente |
| US-46 Albergues | Mayra / Meliton | React/Django: información institucional, ubicación, capacidad e infraestructura. ADMIN registra, consulta, edita, desactiva y elimina instituciones vacías, vinculadas a cuentas ALBERGUE existentes. | Pendiente |
| US-11 Mascotas | Mayra / Angie | CRUD React/Django, una fotografía validada, datos y propiedad; catálogo Spring consume sus tablas. | Pendiente |
| US-13 Disponibilidad | Mayra / Meliton | Corrección manual entre tres estados, validación/propiedad y reflejo al consultar el catálogo. | Pendiente |

Las interfaces del alcance están implementadas; cierre formal Scrum requiere revisión de otro integrante conforme a PLANNING §9.5. No se sustituye esa aprobación humana con pruebas del agente. Ver resultados comprobados y límites en entrega-sprint-01.md, contratos en api.md e instalación en instalacion.md.

## Pendientes de aceptación del equipo

- Mayra revisa US-16, cambios Django, consentimiento y panel administrativo.
- Angie revisa US-17/US-12, cambios Spring y contrato de datos.
- Meliton revisa US-21/US-46/US-13 y verifica la demostración integrada.
- Revisar configuración/migraciones antes de conectar a la base XAMPP real del equipo: no existe backend-admin/.env local para comprobarla en esta sesión. La implementación se ejecutó sobre MariaDB 11.8.8 aislada.
- Confirmar campos/unidades propuestos del perfil y categorías registradas. Acordar capacidad, fecha y nueva estimación por inclusión de React adoptante. La discrepancia 26/30 h permanece visible.
- Teléfono físico, SMTP propio y HTTPS de despliegue no comprobados. El correo SMTP de pruebas documentado satisface el criterio mínimo de US-24; no se afirma entrega externa.

No se añadieron matchmaking, IA, solicitudes, chatbot, mensajería ni seguimiento. Las cuentas de negocio se crean por procedimiento documentado; no se implementó una pantalla de creación administrativa ni una jerarquía de trabajadores dentro de un albergue.
