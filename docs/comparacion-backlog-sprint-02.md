# Planificación del Sprint 2 - Petly

Propuesta ajustada a 30 horas-persona. Fecha de elaboración: 14 de octubre de 2026.

## 1. Objetivo y capacidad del Sprint 2

Planificación propuesta | Petly | Elaborada el 14 de octubre de 2026. Duración: dos semanas. Fechas de inicio y fin por definir.

Objetivo: cerrar la aceptación pendiente del Sprint 1 y entregar en React el recorrido de solicitud de adopción: enviar, evaluar, consultar y cancelar. Se reutilizan cuentas, perfil, consentimiento, catálogo y fotografías existentes.

Capacidad base: 10 días laborables x 3 horas = 30 horas-persona. Se asume que las 3 horas corresponden a la capacidad total disponible indicada; no se multiplican por tres integrantes sin confirmación. Con trabajo los 14 días serían 42 horas; este plan conserva 30.

Compromiso propuesto: 4 h de cierre de Sprint 1 + 21 h para cuatro historias de solicitudes + 5 h de integración final, correcciones y entrega = 30 h. Las horas de cada US ya incluyen sus pruebas específicas.

El PDF usa SP (puntos de historia), no horas. No se convierten 1:1. Las cifras de esta planificación son estimaciones nuevas de esfuerzo restante, ajustadas al código existente.

Todos los pendientes se registran, pero no todos caben en este sprint: cierre 4 h + solicitudes 21 h + pendientes adicionales de Sprint 1 93 h + pendientes restantes del Sprint 2 140 h = 258 h, antes de reservas. Es una suma orientativa de alcances acotados; RENIEC y proveedores pueden aumentar el esfuerzo.

Las nuevas solicitudes se comprometen solo en React y APIs. Android conserva el Sprint 1; ampliar las cuatro US nuevas a Kotlin requeriría aproximadamente 12 h adicionales y una nueva selección de alcance.

El objetivo amplio del PDF (matchmaking + IA + solicitudes) se reduce explícitamente para respetar las 30 h. Matchmaking e IA figuran como pendientes estimados, sin prometer su entrega en estas dos semanas.

## 2. Sprint 1: implementación existente y cierre pendiente

El PDF enumera 18 US para Sprint 1. Nueve corresponden al alcance acordado y ya tienen implementación técnica; no deben reprogramarse desde cero. La revisión cruzada humana sigue pendiente. El código local y la evidencia del 13 de octubre prevalecen sobre documentos anteriores.

Las horas siguientes cubren revisión y evidencia del alcance existente, no nueva programación. Si aparecen defectos, consumir la reserva; si la superan, retirar alcance nuevo. No declarar aprobada una US sin su revisor.

| US | Título claro | Cierre (h) |
| --- | --- | --- |
| US-21 | Registrar adoptantes e iniciar sesión con bloqueo temporal | 0,5 |
| US-16 | Guardar y editar el cuestionario de vivienda y estilo de vida | 0,5 |
| US-17 | Registrar convivencia con niños y otras mascotas | 0,25 |
| US-12 | Buscar y filtrar mascotas en el catálogo web y móvil | 0,5 |
| US-24 | Recuperar la contraseña por correo con un código de un solo uso | 0,5 |
| US-18 | Compartir y revocar el perfil con un albergue elegido | 0,75 |
| US-46 | Gestionar información e infraestructura del albergue | 0,5 |
| US-11 | Registrar y editar mascotas con una fotografía validada | 0,25 |
| US-13 | Actualizar la disponibilidad de una mascota del albergue | 0,25 |

### US-15 | Cubierta por US-11 | 0 h de implementación adicional

La carga y el reemplazo de una foto JPEG/PNG por mascota ya existen en React/Django y se visualizan en React/Android. Aceptarla junto con US-11 (0,25 h compartida); no contar dos veces ni prometer galería de fotos.

### Comprobaciones de cierre

US-21: bloqueo de 1 minuto; US-24: código de 22 caracteres, no el mecanismo anterior. US-18 comparte perfil, sin antecedentes externos. US-13 modifica estado manual, sin expediente de adopción. SMTP externo, teléfono físico y producción HTTPS no son requisitos de este incremento local.

## 3. Pendientes adicionales del Sprint 1

Ocho US del PDF no están completas dentro del alcance vigente. Su esfuerzo restante estimado suma 93 h. Se arrastran al backlog revisado de Sprint 2 y se aplazan por capacidad; no se borran ni se consideran entregadas. Estimaciones incluyen integración y pruebas para el alcance descrito.

### US-19 | Adjuntar fotos de la vivienda desde Android | 10 h

Pendiente completo. Subir hasta tres JPEG/PNG de 5 MB, guardar en almacenamiento privado Django y asociar al perfil de Spring. Solo propietario y albergue con permiso vigente pueden verlas. Vincular a la solicitud cuando exista. Depende de US-06/US-18.

### US-14 | Guardar y quitar mascotas favoritas en web y Android | 6 h

Pendiente. Guardar relación adoptante-mascota sin duplicados, mostrar favoritos persistidos y permitir quitarlos. Reutilizar catálogo; impedir acceso a favoritos ajenos.

### US-47 | Administrar trabajadores y permisos de un albergue | 16 h

Pendiente. El propietario agrega, desactiva y lista trabajadores. Spring conserva las cuentas; Django la pertenencia. Definir permiso de consulta/gestión y evitar acceso a otro albergue. No basta agregar nombres.

### US-48 | Consultar la ficha pública y las reseñas de un albergue | 10 h

Parcial: los datos institucionales existen; faltan ficha pública y reputación. Mostrar datos/ubicación y reseñas verificadas asociadas a adopciones concluidas. Sin reseñas, mostrar “Sin reseñas”. Depende de un cierre de adopción verificable; no inventar puntuaciones.

### US-20 | Guardar días y horarios disponibles para adoptar | 5 h

Pendiente. Añadir franjas semanales al perfil Spring y formularios React/Android; validar inicio anterior al fin y permitir edición. Las horas fuera de casa de US-16 no cubren horarios de disponibilidad.

### US-25 | Proteger el acceso del albergue con un segundo factor | 14 h

Pendiente. Implementar TOTP, activación/desactivación autenticada, segundo paso en React y códigos de recuperación. Mantener Spring como autoridad de acceso. Requiere pruebas de pérdida del segundo factor.

### US-22 | Presentar DNI y recibir un resultado de verificación de identidad | 20 h

Pendiente y condicionado. Formularios web/Android, archivo privado, estados y trazabilidad. Esta cifra cubre revisión manual e integración inicial, no garantiza consulta RENIEC. La validación automática del PDF exige acceso oficial y contrato disponible; reestimar antes de comprometerla.

### US-23 | Revisar documentos de identidad desde el panel ADMIN | 12 h

Pendiente. Lista de verificaciones, acceso privado al documento, aprobación/rechazo con motivo y registro del revisor. Solo ADMIN; no permitir autoaprobación. Depende de US-22 y comparte sus estados.

## 4. Historias comprometidas para Sprint 2

Prioridad de las cuatro historias: alta. Épica: E08, Solicitudes y Proceso de Adopción. Las estimaciones suponen desarrollo sobre el entorno local existente y reutilización del código que resulte válido. Los responsables coordinan; su esfuerzo se contabiliza en las 30 h totales.

### US-06 | Enviar una solicitud de adopción desde la ficha de una mascota | 8 h

Como adoptante, quiero enviar mi solicitud para una mascota disponible y recibir un número de registro. Contrato y migración Django: 2 h; envío/API Spring y validaciones: 3 h; formulario React: 2 h; pruebas de permisos y duplicados: 1 h.

### US-07 | Aprobar, observar o rechazar solicitudes del propio albergue | 7 h

Como responsable del albergue, quiero evaluar solicitudes y registrar una decisión con su motivo. API, permisos y transiciones Django: 3 h; listado/detalle React: 2 h; coherencia con mascota y consentimiento: 1 h; pruebas: 1 h.

### US-08 | Consultar el estado y el motivo de mis solicitudes | 3 h

Como adoptante, quiero consultar mis solicitudes y su última decisión para conocer el avance. Consulta Spring con autorización: 1 h; listado/detalle React: 1 h; pruebas de privacidad y actualización: 1 h.

### US-10 | Cancelar una solicitud antes de que el albergue la apruebe | 3 h

Como adoptante, quiero cancelar una solicitud pendiente u observada para retirar mi postulación. API y transición atómica: 1 h; confirmación React: 1 h; pruebas de propiedad y concurrencia: 1 h.

## 5. Criterios de aceptación del incremento

### US-06 | Enviar una solicitud de adopción desde la ficha de una mascota

Perfil finalizado, mascota disponible y autorización expresa vigente para su albergue. Crear una sola solicitud activa por pareja adoptante-mascota; devolver ID y fecha. Rechazar solicitudes ajenas y mascotas de albergues inactivos. Enviar no reserva la mascota ni registra una adopción.

### US-07 | Aprobar, observar o rechazar solicitudes del propio albergue

Solo el albergue propietario ve sus solicitudes y decide. Observar/rechazar exige motivo; aprobar exige autorización vigente y mascota disponible. Usar operación atómica para impedir dos aprobaciones activas por mascota; al aprobar, cambiar a En Proceso. El perfil compartido respeta US-18. Aprobar no equivale a Adoptado.

### US-08 | Consultar el estado y el motivo de mis solicitudes

Mostrar mascota, albergue, estado, fecha y motivo. Solo el titular puede consultar. Actualizar al abrir la página y con botón Actualizar; manejar lista vacía y errores. Este alcance sustituye el tiempo real del PDF por consulta bajo demanda; push/WebSocket queda pendiente.

### US-10 | Cancelar una solicitud antes de que el albergue la apruebe

Solo el titular cancela PENDIENTE u OBSERVADA. Conservar registro y fecha; ocultar acciones posteriores. Rechazar cancelación de APROBADA/RECHAZADA y repetición inválida. Si aprobación y cancelación coinciden, solo una transición puede ganar. Retirar una solicitud aprobada queda fuera de este incremento.

### Estados y límites del proceso

PENDIENTE -> OBSERVADA, RECHAZADA o APROBADA por el albergue. PENDIENTE/OBSERVADA -> CANCELADA por el titular. OBSERVADA -> APROBADA o RECHAZADA tras evaluación del albergue. APROBADA, RECHAZADA y CANCELADA no admiten cambios en este incremento. No se incluye reenvío o edición de solicitudes observadas, contrato ni cierre de adopción.

### Consentimiento y datos privados

La solicitud no concede autorización implícita. Solicitar/consultar perfil requiere el permiso de US-18; revocarlo bloquea el perfil y una nueva aprobación. Conservar solo datos mínimos de la solicitud, sin copiar el perfil privado completo para eludir la revocación.

### Definición de terminado

Datos reales persistidos, migraciones documentadas y sin secretos, APIs y React integrados, casos positivos y negativos de permisos, duplicados y transiciones probados. Demostración con dos adoptantes y dos albergues, evidencia y revisión por otro integrante. Sin revisión humana queda parcial.

## 6. Organización técnica y reparto propuesto

Solicitudes y su historial: Django es dueño de las tablas y migraciones. Spring mantiene autenticación, perfil y autorización, y expone el acceso del adoptante mediante integración interna autenticada con Django. React del albergue consulta Django. No duplicar cuentas o contraseñas.

En US-06 se define el contrato interno, identificadores, estados, errores, idempotencia y validación de JWT/rol. Reutilizar el secreto de servicio y los IDs Spring existentes. Evitar que un endpoint interno acepte un adoptanteId arbitrario sin comprobar identidad y permiso.

Existe SolicitudAdopcion y código histórico Django, pero las rutas activas de acceso lo retiran y algunas pruebas se omiten. Revisar modelos, relaciones y datos antes de reutilizarlo; su presencia no acredita el flujo integrado ni autoriza a exponer rutas antiguas.

Las aprobaciones deben proteger la solicitud y la mascota en una transacción; documentar que las correcciones manuales de US-13 no pueden dejar dos aprobaciones activas ni cambiar a Disponible una reserva aprobada. Esta integración se incluye en US-07.

La observación registra un motivo; las aclaraciones y documentos adicionales quedan para una ampliación. La vista del adoptante se actualiza bajo demanda, sin prometer notificaciones en tiempo real.

| Trabajo | Principal propuesto | Revisor propuesto |
| --- | --- | --- |
| Cierre Sprint 1 | Responsables existentes | Según PLANNING |
| US-06 y US-08 | Angie: Spring + React | Meliton |
| US-07 | Mayra: Django + React | Angie |
| US-10 | Angie con apoyo de Mayra | Meliton |
| Integración y evidencia | Meliton con ambos responsables | Mayra / Angie |

### Distribución orientativa de las 30 h

Angie 11 h, Mayra 10 h y Meliton 9 h, incluyendo coordinación, revisión y reserva. Es una propuesta de esfuerzo total, no una disponibilidad individual confirmada. Mantener liderazgo previo sin multiplicar la capacidad.

## 7. Calendario de diez sesiones de tres horas

| Día | Trabajo previsto | Horas |
| --- | --- | --- |
| 1 | Revisión cruzada Sprint 1: acceso, perfil, catálogo, consentimientos y mascotas. | 3 |
| 2 | Cerrar evidencia Sprint 1 (1 h); contrato y migración US-06 (2 h). | 3 |
| 3 | US-06: integración/API, validaciones e idempotencia. | 3 |
| 4 | US-06: formulario React y pruebas de envío/duplicados. | 3 |
| 5 | US-07: API, transiciones y permisos del albergue. | 3 |
| 6 | US-07: panel React (2 h); mascota y autorización (1 h). | 3 |
| 7 | US-07: pruebas (1 h); US-08: consulta y pantalla (2 h). | 3 |
| 8 | US-08: pruebas (1 h); US-10: API y confirmación (2 h). | 3 |
| 9 | US-10: pruebas (1 h); reserva para integración/correcciones (2 h). | 3 |
| 10 | Reserva: revisión final, regresión, evidencia y demostración. | 3 |

### Control al terminar la primera semana

US-06 debe funcionar completa; US-07 debe tener API comprobable. Si US-06 supera 8 h o surgen bloqueos de contratos/migraciones, revisar capacidad inmediatamente. No empezar IA, favoritos ni matchmaking para llenar tiempo parcial.

### Uso de la reserva de 5 h

2 h para integración/regresión entre servicios; 2 h para defectos o imprevistos; 1 h para evidencia, revisión final y demo. Si no basta, retirar US-10 primero y dejarla pendiente, documentando que el alcance final ya no incluye cancelación.

### Resultado demostrable

Adoptante con perfil finalizado y autorización envía solicitud; albergue la observa, aprueba o rechaza; adoptante consulta el resultado y cancela una solicitud aún no aprobada. Una segunda aprobación simultánea o el acceso a datos ajenos se rechazan.

## 8. Pendientes de Sprint 2: compatibilidad y publicación

Se conservan los IDs del PDF y se simplifican los títulos. Cada cifra cubre el alcance concreto descrito, con integración y pruebas. Las reducciones frente al PDF son explícitas; requieren reestimación si se exige un proveedor o modalidad más amplia.

### US-01 | Recomendar mascotas con un porcentaje de compatibilidad | 14 h

Spring calcula un puntaje reproducible con reglas sobre vivienda, rutina y convivencia. Mostrarlo en catálogo React/Android. Definir pesos y tratamiento de datos desconocidos antes de desarrollar; no presentar el puntaje como garantía de adopción.

### US-02 | Ordenar candidatos de una mascota por compatibilidad | 8 h

Panel React del albergue con solicitudes ordenadas por puntaje y desempate por fecha. Solo solicitantes del propio albergue y datos autorizados. Depende de US-01 y US-06; el puntaje no decide automáticamente.

### US-03 | Actualizar el porcentaje de compatibilidad al editar mi perfil | 4 h

Recalcular al guardar preferencias y reflejar el resultado en web/Android. Invalidar resultados anteriores; pruebas con cambios de perfil. Conserva la redacción actualizada de PLANNING y depende de US-01.

### US-04 | Explicar los criterios que componen la compatibilidad | 5 h

Mostrar en detalle web/Android aporte de cada criterio, pesos y datos faltantes. La suma debe coincidir con el porcentaje final. Depende de US-01.

### US-05 | Configurar y validar los pesos del cálculo de compatibilidad | 8 h

Formulario ADMIN React con pesos no negativos que sumen 100, persistencia y versión de configuración. Recalcular sin mezclar versiones. Depende de US-01 y US-03.

### US-26 | Generar un borrador de descripción con IA y revisarlo antes de publicar | 12 h

Django integra un proveedor y React permite generar, editar y confirmar el texto usando datos y foto. Controlar fallo, tiempo de espera y coste; no publicar automáticamente ni inventar salud o comportamiento. Requiere API, credenciales y presupuesto.

### US-27 | Proponer recorte y encuadre de una foto con IA | 14 h

Proveedor propone recorte; React muestra original y vista previa, y el albergue confirma. Conservar original, validar el resultado y permitir cancelar. Un recorte manual sería otra reducción de alcance, no entrega de IA.

## 9. Pendientes de Sprint 2: contrato, IA y albergues

Estas seis US completan la comparación de las 17 historias del Sprint 2 original. Las 13 historias aplazadas de Sprint 2 suman 140 h. No están incluidas en las 30 h comprometidas.

### US-09 | Generar y firmar un contrato de adopción con trazabilidad | 24 h

Preparar contrato desde solicitud aprobada, revisión, firma mediante proveedor, descarga y registro de entrega. Definir quién firma y cuándo pasa a Adoptado. Requiere proveedor y condiciones acordadas; un checkbox o PDF descargable no equivale a la integración de firma del PDF.

### US-29 | Sugerir raza y rango de edad desde una foto para revisión humana | 16 h

Integrar modelo de visión; mostrar sugerencia e incertidumbre y exigir confirmación del albergue. Guardar como dato confirmado solo tras revisión; no inferir edad exacta ni reemplazar la evaluación veterinaria. Depende de proveedor disponible.

### US-28 | Generar y descargar un afiche de una mascota para redes sociales | 8 h

Plantilla con foto, nombre, albergue y contacto, vista previa y descarga PNG desde React. Depende de datos/foto existentes; no incluye publicación automática en redes ni generación visual con IA.

### US-30 | Sugerir etiquetas de temperamento con IA a partir de observaciones | 12 h

Usar notas de conducta registradas por el albergue, mostrar sugerencias y exigir validación humana. Conservar Por evaluar si faltan observaciones. No deducir conducta fiable solo desde una foto; requiere proveedor y datos suficientes.

### US-49 | Publicar necesidades y registrar donaciones del albergue | 8 h

CRUD Django/React de necesidades y registro manual de donaciones con fecha/cantidad. Adoptante consulta lista pública. Solo propietario modifica; no incluye pagos ni pasarela.

### US-50 | Publicar eventos presenciales de adopción del albergue | 7 h

Django y React permiten crear/editar/cancelar eventos con lugar, fecha y contacto; lista pública para adoptantes. Validar fecha y propiedad. No incluye inscripciones, recordatorios ni pagos.

## 10. Dependencias, estimaciones y trazabilidad

Orden recomendado del backlog, sin asignar otro sprint: favoritos y horarios; compatibilidad base US-01, explicación US-04 y recálculo US-03; clasificación US-02 y configuración US-05; descripción asistida US-26; contrato/cierre US-09 antes de reseñas verificadas US-48. Fotos de vivienda y KYC requieren almacenamiento privado; personal y 2FA requieren revisar roles y acceso.

RENIEC no se presume disponible. El alcance manual de US-22 no satisface la consulta automática del PDF. Firma, IA y visión requieren proveedor, credenciales y condiciones antes de fijar fechas. Las horas de integración con proveedores son orientativas y no incluyen esperas de alta, aprobación externa ni contratación.

US-28 utiliza una plantilla automática; no promete generación visual con IA. US-30 propone etiquetas desde observaciones y confirmación humana. US-08 ofrece actualización bajo demanda. US-10 solo cancela antes de aprobar. Estas decisiones evitan atribuir como completas modalidades que no se implementarán.

El análisis es documental y estático del estado local; no se ejecutaron nuevas pruebas ni se aprobaron historias por cuenta del equipo. Hay cambios locales existentes que se conservaron. No se cambiaron las US del Sprint 1, el código de la aplicación ni los registros de aprobación.

### Fuentes revisadas

Planificacion de Sprint_2.pdf (6 páginas): 18 US de Sprint 1 y 17 US de Sprint 2. PLANNING.md: alcance acordado, arquitectura, responsables y criterios. docs/sprint-01.md: revisión humana pendiente. docs/presentacion-clase.md y docs/PAUSA.md: evidencia más reciente y recuperación actualizada.

### Comprobación del código

web/src/routes/index.tsx: rutas vigentes de acceso, catálogo, perfil, consentimientos y paneles. backend-admin/apps/acceso/urls.py: rutas de solicitudes históricas retiradas. Modelos Django y módulos Spring/Kotlin: recursos existentes y ausencia del recorrido nuevo entregado.

### Resumen de esfuerzo

Sprint 1: 9 US con cierre estimado 4 h; US-15 cubierta, sin doble conteo; 8 pendientes adicionales, 93 h. Sprint 2: 4 US seleccionadas, 21 h; 13 aplazadas, 140 h. Trabajo identificado: 258 h orientativas. Plan de dos semanas: 25 h seleccionadas + 5 h reserva = 30 h. No se asume que las 35 US completas caben en este sprint.

## Detalle de revisión por US del Sprint 1

- **US-21 (0,5 h):** Revisar roles, permisos y bloqueo de un minuto tras dos fallos. Registro, JWT y clientes ya existen.
- **US-16 (0,5 h):** Aceptar borrador, finalización, edición y privacidad en React/Android.
- **US-17 (0,25 h):** Aceptar Sí, No y sin responder; comprobar persistencia y privacidad.
- **US-12 (0,5 h):** Aceptar filtros combinados, paginación, detalle y fotografías.
- **US-24 (0,5 h):** Aceptar código de 22 caracteres, 15 minutos, Mailpit y revocación de sesión. Recepción web/Android ya documentada.
- **US-18 (0,75 h):** Aceptar autorización específica, consulta Django y rechazo tras revocar. No implica solicitud ni antecedentes externos.
- **US-46 (0,5 h):** Aceptar CRUD ADMIN y edición del propietario; borrado protegido y desactivación.
- **US-11 (0,25 h):** Aceptar CRUD, propiedad, JPEG/PNG y límite de 5 MB.
- **US-13 (0,25 h):** Aceptar Disponible, En Proceso y Adoptado; reflejo al consultar el catálogo.
