# Integración local — 27 de octubre de 2026

Comparación con `docs/sprint-02-descripciones-originales.md`. Los estados describen código y comprobaciones de esta sesión; no sustituyen la revisión cruzada ni acreditan el cierre de todos los criterios de aceptación.

## 1. Sincronización

- `main` avanzó de `a7d5dcb` a `b7914cd`; diferencia con `origin/main`: 0 commits en ambos sentidos.
- Se incorporaron tres commits de documentación, sin cambios funcionales remotos nuevos.
- Se conservaron las modificaciones previas en PLANNING, URLs Django, documentación de base/arquitectura, `docs/propiedad-tablas.md` y `tmp/`.
- PLANNING se protegió y recuperó sin conflictos. Se conserva el stash `petly-integracion-2026-10-27-planning` como respaldo.
- No se hicieron commits ni push.

## 2. Ejecución y datos

| Componente | Resultado |
| --- | --- |
| MariaDB 11.8.8 | Instancia permanente existente, `database_petly`, `127.0.0.1:13306`. |
| Spring Boot | Activo en `http://localhost:8080`; salud HTTP 200, `UP`. |
| Django | Activo en `http://localhost:8000`; salud HTTP 200, `UP`. |
| React | Activo en `http://localhost:5173`; HTTP 200. |
| Kotlin | Compilado, instalado y abierto en Medium_Phone, Android 14. Se usa `com.petly_app.integration` para conservar la aplicación existente, firmada con otra clave. |

Respaldo previo a migraciones: `/home/meli_dev/.local/share/petly/backups/petly-20261027-072758-0500-q_pzyxbz.sql` (privado, fuera de Git).

Se verificó Flyway V1–V5, todas exitosas. Tras inspeccionar operaciones aditivas se aplicaron acceso 0014–0017 y mascotas 0005. El plan Django quedó vacío. El lanzador verificó coincidencia de contraseña y secreto JWT entre backends.

MariaDB advierte que no soporta restricciones únicas condicionales de Django para solicitudes. El código usa validaciones y bloqueos, pero las pruebas de carrera no se ejecutaron contra MariaDB en esta sesión; no se acredita la concurrencia solo con SQLite.

## 3. Validación realizada

- React: compilación TypeScript/Vite correcta; 4 pruebas Playwright de orden de catálogo y seguimiento correctas. Esas cuatro pruebas simulan respuestas de API.
- Spring: 60 pruebas, cero fallos/errores, base H2 aislada.
- Django: 123 pruebas encontradas, 111 ejecutadas correctamente, 12 omitidas, base SQLite aislada; incluye omisiones de concurrencia.
- Kotlin: APK correcto y 22 pruebas unitarias sin fallos. Una prueba instrumentada real contra los backends pasó: registro/acceso, borrador y finalización del perfil, catálogo/detalle y autorización/revocación. Esta prueba creó una cuenta Android de prueba.
- APIs reales: acceso de ADMIN, ALBERGUE y ADOPTANTE HTTP 200; configuración de compatibilidad, albergues, mascotas, solicitudes y catálogo HTTP 200.
- Chromium con cuentas reales: ADOPTANTE llegó a `/adoptante/catalogo`, ALBERGUE a `/albergue` y ADMIN a `/administracion/albergues`, con sus títulos visibles. La comprobación inicial esperaba un botón de cierre de sesión dentro de un menú oculto; se verificó usando ruta y título del panel.
- Hallazgo real: `GET /api/perfil/disponibilidad` devuelve HTTP 500; el log identifica `NoResourceFoundException`, pues no existe esa ruta Spring.
- `git diff --check` correcto.

Correcciones locales limitadas a preparación de pruebas: se creó el albergue propietario en la prueba Django de revocación; las pruebas React de seguimiento ahora simulan también catálogo y opciones, y admiten consultas repetidas de StrictMode sin avanzar artificialmente el estado. No se alteró la lógica funcional para dar por implementadas historias pendientes.

## 4. Avance por historia

| Historias | Implementación observada y límite |
| --- | --- |
| US-01, US-02, US-03, US-04 | Motor Spring, orden antes de paginar, recálculo con perfil vigente y desglose en React/Android. Datos insuficientes identificados. Pesos iniciales 30/35/35; requieren revisión del equipo. |
| US-05 | Configuración ADMIN de pesos y versiones persistidas y compartidas por ambos backends. Consulta real y pruebas Spring correctas. |
| US-06, US-07, US-08, US-10 | Flujo React–Spring–Django para envío, decisiones del propietario, eventos/seguimiento y cancelación. Cubierto por suites; falta recorrido completo de decisiones con ambos servicios y concurrencia MariaDB. El formulario móvil nuevo guarda/envía solo a un gestor en memoria; no acredita persistencia en servidor. |
| US-18 | Perfil autorizado e historial del mismo albergue implementados en Django/React. Prueba de revocación corregida y aprobada; autorización/revocación real comprobada desde Android. |
| US-15 | Carga/reemplazo y validación de foto implementados, con consumo desde catálogo. No se repitió aquí el recorrido completo de carga/reemplazo/descarga visual. |
| US-28 | Plantilla React con vista previa y descarga PNG 1080 × 1350; requiere foto y contacto público. Falta validación visual del afiche en esta sesión. |
| US-29 | Endpoint y editor de borrador preparados; faltan `PET_DESCRIPTION_API_URL` y `PET_DESCRIPTION_API_TOKEN`. Sin prueba de proveedor real. |
| US-14 | Icono y lista Android en memoria. Falta persistencia Spring y lista compartida React/Android entre sesiones. Parcial. |
| US-20 | Pantalla, validaciones y cliente Android preparados. Falta ruta/persistencia Spring y formulario React. La consulta real falla. Parcial. |
| US-19 | No se encontró el flujo requerido de hasta tres fotos privadas de vivienda vinculadas a solicitud. Pendiente. |
| US-50 | No se encontró CRUD/listado de eventos presenciales. Pendiente. Los eventos del seguimiento de solicitudes son otra funcionalidad. |
| US-09 | Aplazada en el plan; hay borrador y adaptador de firma, pero faltan proveedor, credenciales, webhook y términos acordados. Sin firma real. |
| US-22, US-23 | Aplazadas en el plan, aunque existen avances KYC, documentos privados y revisión manual. RENIEC no está configurado. El botón «Verificar DNI» del nuevo formulario Android solo comprueba longitud y marca un estado local: no verifica identidad. No se acredita cierre. |
| US-30, US-49 | Aplazadas; sin implementación completa identificada de etiquetado IA ni necesidades/donaciones. |

Pendientes prioritarios de integración: persistencia de solicitudes del formulario móvil, favoritos y disponibilidad; fotos privadas de vivienda; eventos; recorrido de decisiones con ambos backends; carreras en MariaDB; revisión visual y cruzada. IA, firma y RENIEC requieren proveedores reales configurados para validar sus respectivos criterios.

Los backends conservan sus logs en `/home/meli_dev/.local/share/petly/logs/`; las salidas de pruebas de esta sesión están en `/tmp/petly-*-20261027.log` y son temporales.
