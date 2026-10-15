# Planificación mejorada del Sprint 2 - Petly

Fecha de elaboración: 14 de octubre de 2026.

Fuente: sección Sprint 2 del PDF proporcionado (páginas 3 a 6). Se conservan sus 25 historias e identificadores; se precisan comportamientos, límites y dependencias. Responsables y revisores propuestos según el liderazgo del equipo.

Estimación restante del alcance completo: 247 h, sin reserva. Capacidad disponible: 2 semanas x 5 días x 3 h = 30 horas-persona totales. Los SP del PDF no equivalen a horas. Todas las estimaciones incluyen integración y pruebas propias, y suponen reutilizar lo ya implementado.

Compromiso recomendado para 30 h: US-06 (8 h), US-07 (7 h), US-08 (8 h) y US-10 (3 h): 26 h; más 4 h de integración final, correcciones y entrega. Las otras 21 US quedan pendientes de selección. Las nuevas solicitudes se comprometen en React y APIs; Android se incluye solo donde la historia lo indica.

Las horas son esfuerzo total por historia, no horas por integrante. IA, firma y RENIEC dependen de proveedores, credenciales y entornos de prueba disponibles; las esperas externas no se incluyen. Los títulos y criterios describen alcances acotados, no una promesa de completar las 25 US en dos semanas.

## Cuadro de planificación

| Historia | Responsable principal | Revisor | Colaboración necesaria | Tiempo estimado (h) |
| --- | --- | --- | --- | --- |
| US-01 - Mostrar mascotas recomendadas con porcentaje de compatibilidad | Angie | Meliton | Mayra define datos de mascotas; Meliton integra catálogo Android. | 14 |
| US-02 - Ordenar los solicitantes de una mascota por compatibilidad | Mayra | Angie | Angie expone el cálculo y los datos autorizados; Meliton revisa escenarios. | 8 |
| US-03 - Recalcular mi compatibilidad al guardar cambios del perfil | Meliton | Angie | Angie recalcula en Spring; Mayra valida la consistencia con mascotas. | 4 |
| US-04 - Consultar el aporte de cada criterio al porcentaje de compatibilidad | Meliton | Mayra | Angie expone el desglose de Spring; Mayra valida etiquetas y datos. | 5 |
| US-05 - Configurar los pesos de los criterios de compatibilidad desde ADMIN | Angie | Mayra | Meliton verifica recálculo en los clientes; Mayra revisa permisos. | 8 |
| US-06 - Enviar una solicitud de adopción para una mascota disponible | Angie | Meliton | Mayra implementa solicitudes en Django y contrato interno con Spring. | 8 |
| US-07 - Aprobar, observar o rechazar solicitudes del propio albergue | Mayra | Angie | Angie integra perfil/consentimiento; Meliton prueba el flujo completo. | 7 |
| US-08 - Actualizar automáticamente el estado y el motivo de mis solicitudes | Angie | Meliton | Mayra expone los cambios de estado desde Django. | 8 |
| US-09 - Generar y firmar el contrato digital de una solicitud aprobada | Mayra | Angie | Angie integra identidad; Meliton verifica documento y trazabilidad. | 24 |
| US-10 - Cancelar una solicitud pendiente u observada antes de su aprobación | Angie | Meliton | Mayra aplica la transición y conserva el historial en Django. | 3 |
| US-26 - Generar y editar una descripción con IA antes de publicarla | Mayra | Angie | Angie apoya la API del proveedor; Meliton verifica errores y revisión. | 12 |
| US-27 - Proponer y confirmar un recorte de la foto mediante IA | Mayra | Meliton | Angie apoya la integración del proveedor y validaciones del servicio. | 14 |
| US-29 - Sugerir raza y rango de edad desde una foto para revisión del albergue | Mayra | Meliton | Angie integra el modelo de visión y los datos de respuesta. | 16 |
| US-28 - Generar y descargar un afiche de una mascota para redes sociales | Mayra | Meliton | Angie apoya el acceso a datos públicos de mascota y albergue. | 8 |
| US-30 - Sugerir etiquetas de temperamento desde observaciones registradas | Mayra | Angie | Meliton comprueba etiquetas y su visualización en el catálogo. | 12 |
| US-49 - Publicar necesidades y registrar donaciones recibidas por el albergue | Mayra | Angie | Meliton revisa consulta pública y validación de cantidades. | 8 |
| US-50 - Publicar y actualizar eventos presenciales de adopción | Mayra | Meliton | Angie apoya el acceso del adoptante a la lista pública. | 7 |
| US-19 - Adjuntar fotos privadas de la vivienda a una solicitud desde Android | Meliton | Mayra | Angie vincula perfil/solicitud; Mayra implementa almacenamiento privado. | 10 |
| US-14 - Guardar, consultar y quitar mascotas favoritas en web y Android | Meliton | Angie | Angie implementa persistencia y API Spring; Mayra aporta catálogo. | 6 |
| US-18 - Consultar el perfil y las solicitudes previas autorizadas de un adoptante | Angie | Mayra | Mayra integra panel del albergue; Meliton revisa revocación y privacidad. | 5 |
| US-15 - Cargar o reemplazar la foto principal de una mascota del albergue | Mayra | Angie | Meliton comprueba visualización de la misma foto en Android. | 1 |
| US-25 - Activar un segundo factor TOTP para el acceso del albergue | Angie | Mayra | Meliton revisa recuperación y escenarios de acceso; Mayra integra React. | 14 |
| US-22 - Verificar la identidad del adoptante con DNI mediante un servicio RENIEC autorizado | Angie | Mayra | Meliton integra Android; Mayra protege documentos y estados KYC. | 28 |
| US-23 - Aprobar o rechazar verificaciones de identidad desde ADMIN en React | Mayra | Angie | Angie expone estados e identidad; Meliton verifica acceso denegado. | 12 |
| US-20 - Guardar y editar días y franjas horarias disponibles del adoptante | Meliton | Angie | Angie persiste horarios en Spring; Mayra revisa consulta autorizada. | 5 |
| **Total del alcance completo** | | | **Sin reserva** | **247** |

## Criterios de aceptación por historia

### US-01 - Mostrar mascotas recomendadas con porcentaje de compatibilidad

Como adoptante, quiero ver qué mascotas se ajustan a mi vivienda, rutina y convivencia para priorizar mi búsqueda.

- Con perfil finalizado, el catálogo React/Android muestra un porcentaje de 0 a 100 calculado en Spring mediante reglas y pesos documentados; los mismos datos producen el mismo resultado.
- El cálculo define cómo tratar datos faltantes y muestra “Información insuficiente” cuando no puede evaluarse. Una mascota con datos desconocidos no recibe una coincidencia perfecta por defecto.
- Las recomendaciones solo incluyen mascotas disponibles de albergues activos; se prueba al menos un perfil compatible, uno poco compatible y uno incompleto. El puntaje orienta y no aprueba adopciones.

### US-02 - Ordenar los solicitantes de una mascota por compatibilidad

Como albergue, quiero consultar los solicitantes de cada mascota ordenados por compatibilidad para apoyar su evaluación.

- Solo el albergue propietario consulta las solicitudes de su mascota; la lista muestra porcentaje y se ordena de mayor a menor, con desempate por fecha de solicitud.
- Los candidatos sin datos suficientes aparecen identificados al final; el acceso al perfil requiere autorización vigente. El orden no genera aprobaciones automáticas.
- Depende de US-01 y US-06; se comprueba que un albergue no pueda consultar candidatos de otro.

### US-03 - Recalcular mi compatibilidad al guardar cambios del perfil

Como adoptante, quiero visualizar el puntaje recalculado al actualizar mis preferencias para mantener el catálogo personalizado.

- Guardar cambios válidos de vivienda, rutina o convivencia invalida el cálculo anterior; la siguiente consulta muestra porcentajes calculados con el perfil actualizado en React y Android.
- Guardar un formulario inválido no cambia el perfil ni los resultados; una operación fallida informa el error y permite reintentar.
- Depende de US-01; una prueba modifica un criterio relevante y comprueba que no se conserve un resultado obsoleto.

### US-04 - Consultar el aporte de cada criterio al porcentaje de compatibilidad

Como adoptante, quiero conocer qué criterios favorecen o reducen la compatibilidad para entender la recomendación.

- El detalle React/Android muestra vivienda, rutina y convivencia, con peso, aporte y explicación comprensible de cada criterio.
- La suma de aportes coincide con el porcentaje mostrado, aplicando el redondeo documentado; los datos desconocidos se identifican.
- Depende de US-01 y utiliza exactamente la misma versión de reglas que el resultado del catálogo.

### US-05 - Configurar los pesos de los criterios de compatibilidad desde ADMIN

Como ADMIN, quiero ajustar los pesos del cálculo para mantener las recomendaciones según los criterios acordados.

- Solo ADMIN edita pesos no negativos cuya suma sea 100; se rechazan valores inválidos sin reemplazar la configuración vigente.
- Guardar registra versión, fecha y usuario responsable; las nuevas consultas utilizan una única versión y recalculan los resultados afectados.
- Depende de US-01 y US-03; se prueba acceso denegado para adoptantes y albergues.

### US-06 - Enviar una solicitud de adopción para una mascota disponible

Como adoptante, quiero enviar una solicitud desde la ficha de una mascota para iniciar su evaluación.

- React permite enviar cuando el perfil está finalizado, la mascota está disponible y existe autorización expresa vigente para su albergue.
- Se persiste la solicitud con identificador, titular, mascota, fecha y estado PENDIENTE; se muestra confirmación al adoptante.
- Se rechazan duplicados activos, albergues inactivos y operaciones a nombre de otra persona. Enviar no reserva la mascota; contrato, migración e integración Spring-Django forman parte de esta US.

### US-07 - Aprobar, observar o rechazar solicitudes del propio albergue

Como albergue, quiero registrar una decisión y su motivo para evaluar solicitudes de mis mascotas.

- Solo el albergue propietario lista y evalúa sus solicitudes desde React. Observar o rechazar exige motivo; aprobar exige autorización vigente y mascota disponible.
- Aprobar actualiza la mascota a En Proceso en una transacción y evita dos aprobaciones activas para la misma mascota, incluso con peticiones simultáneas.
- PENDIENTE u OBSERVADA admiten aprobación o rechazo; la decisión conserva fecha y responsable. Aprobar no registra una adopción finalizada. Se impide liberar manualmente una mascota con aprobación activa.

### US-08 - Actualizar automáticamente el estado y el motivo de mis solicitudes

Como adoptante, quiero ver el avance de mis solicitudes sin recargar la página para conocer las decisiones del albergue.

- React muestra mascota, albergue, estado, última fecha de cambio y motivo; solo el titular puede consultar sus solicitudes.
- Mientras la vista esté abierta y visible, consulta los cambios cada 10 segundos; una decisión guardada aparece en el siguiente ciclo exitoso. Al volver a la vista consulta inmediatamente.
- Ante pérdida de conexión muestra aviso, conserva el último dato con su fecha y permite reintentar; evita solicitudes solapadas y detiene el ciclo al cerrar la vista. Es actualización periódica cercana al tiempo real, sin WebSocket ni push.

### US-09 - Generar y firmar el contrato digital de una solicitud aprobada

Como albergue, quiero generar un contrato y recoger las firmas de las partes para registrar la entrega acordada.

- Desde una solicitud aprobada se genera un contrato con adoptante, mascota, albergue y condiciones de una plantilla revisada por el equipo.
- El proveedor acordado recoge las firmas de ambas partes; se conserva identificador de operación, estado, fechas y documento final descargable solo por las partes autorizadas.
- Solo con firmas confirmadas y entrega registrada se concluye la adopción y se actualiza a Adoptado. Se contemplan rechazo y fallo del proveedor sin duplicar contratos. Requiere proveedor disponible; descargar un PDF o marcar una casilla no satisface la firma integrada.

### US-10 - Cancelar una solicitud pendiente u observada antes de su aprobación

Como adoptante, quiero retirar una solicitud que aún no se ha aprobado para detener mi postulación.

- Solo el titular puede cancelar una solicitud PENDIENTE u OBSERVADA después de confirmar la acción.
- Se conserva la solicitud con estado CANCELADA y fecha; no se permiten decisiones posteriores ni cancelación de APROBADA o RECHAZADA.
- Si aprobación y cancelación coinciden, solo una transición se completa. Retirar una solicitud ya aprobada requiere otro procedimiento y queda fuera de este alcance.

### US-26 - Generar y editar una descripción con IA antes de publicarla

Como albergue, quiero obtener un borrador a partir de la foto y los datos de una mascota para redactar su publicación.

- React solicita a Django un borrador usando datos registrados y una foto; el proveedor configurado devuelve un texto editable.
- Nada se publica sin confirmación del albergue. El contenido evita inventar salud, raza o conducta no registradas y distingue los datos desconocidos.
- Si el proveedor falla o agota el tiempo, se conserva el texto existente y se permite reintentar; las claves permanecen en el servidor. Requiere proveedor y presupuesto disponibles.

### US-27 - Proponer y confirmar un recorte de la foto mediante IA

Como albergue, quiero revisar un encuadre sugerido por IA para mejorar la foto de una mascota sin perder el original.

- Desde una foto válida, el proveedor devuelve una propuesta de recorte que mantiene visible a la mascota; React muestra original y vista previa.
- El albergue acepta o descarta el resultado. Solo aceptar actualiza la foto pública; el original se conserva para restauración.
- Se rechazan archivos o resultados inválidos y se gestionan fallos del proveedor sin perder imágenes. El ajuste se limita al encuadre, sin alterar rasgos del animal; un recorte manual no acredita esta US.

### US-29 - Sugerir raza y rango de edad desde una foto para revisión del albergue

Como albergue, quiero recibir sugerencias orientativas desde una foto para completar la ficha con revisión humana.

- El modelo devuelve una raza sugerida o “No identificable” y un rango de edad estimado, con indicación de incertidumbre.
- Las sugerencias no reemplazan datos confirmados automáticamente; el albergue puede corregirlas o descartarlas antes de guardar.
- Una imagen insuficiente o un fallo no genera datos ficticios. La edad se presenta como estimación orientativa, no exacta ni veterinaria. Requiere proveedor de visión disponible.

### US-28 - Generar y descargar un afiche de una mascota para redes sociales

Como albergue, quiero generar un afiche con la información de una mascota para compartirla en redes.

- El afiche incluye foto, nombre, datos básicos y contacto público del albergue, usando una plantilla legible de 1080 x 1350 píxeles.
- React muestra una vista previa y descarga un PNG sin recortar texto ni deformar la imagen; exige completar los datos públicos necesarios.
- Solo el propietario genera el afiche de su mascota. No incluye publicación automática en redes ni generación de nuevas imágenes del animal.

### US-30 - Sugerir etiquetas de temperamento desde observaciones registradas

Como albergue, quiero que la IA sugiera etiquetas a partir de mis observaciones para documentar el comportamiento de la mascota.

- La IA utiliza notas de conducta proporcionadas por el albergue y propone únicamente categorías permitidas por el catálogo.
- El albergue confirma o corrige las etiquetas antes de guardarlas; se conserva Por evaluar si faltan observaciones suficientes.
- No se infiere temperamento fiable solo desde una fotografía ni se reemplazan evaluaciones confirmadas sin aprobación. Ante fallo se conserva el dato previo.

### US-49 - Publicar necesidades y registrar donaciones recibidas por el albergue

Como albergue, quiero publicar lo que necesito y registrar aportes recibidos para mantener informados a los interesados.

- El propietario crea y edita necesidades con descripción, cantidad, unidad y estado abierta/cubierta; los adoptantes consultan la lista pública.
- Registra manualmente donaciones con fecha, tipo y cantidad válida, sin exponer datos privados del donante en la vista pública.
- Solo el propietario modifica registros de su albergue. No incluye cobros, transferencias ni pasarela de pago.

### US-50 - Publicar y actualizar eventos presenciales de adopción

Como albergue, quiero publicar fecha y lugar de eventos para invitar a personas interesadas en adoptar.

- El propietario crea o edita eventos con título, descripción, fecha, horario, ubicación y contacto público; se rechazan campos incompletos o fechas inválidas.
- Los adoptantes ven próximos eventos y su detalle. Cancelar conserva el registro y muestra claramente el estado cancelado.
- Otro albergue no puede modificar el evento. No incluye inscripción, pagos ni recordatorios automáticos.

### US-19 - Adjuntar fotos privadas de la vivienda a una solicitud desde Android

Como adoptante, quiero adjuntar fotos de mi vivienda desde Android para que el albergue evalúe el espacio disponible.

- Android permite elegir hasta tres fotos JPEG/PNG de máximo 5 MB cada una y adjuntarlas a una solicitud del titular; valida formato y tamaño en el servidor.
- Las imágenes quedan en almacenamiento privado, vinculadas a la solicitud, y se recuperan al volver al formulario; una subida fallida permite reintentar sin duplicados.
- Solo el titular y el albergue destinatario con autorización vigente acceden a ellas. Revocar bloquea la consulta del albergue; no se sirven como imágenes públicas. Depende de US-06 y US-18.

### US-14 - Guardar, consultar y quitar mascotas favoritas en web y Android

Como adoptante, quiero marcar mascotas con el icono de favoritos para volver a consultarlas desde mi perfil.

- El icono permite agregar y quitar favoritos; React y Android recuperan la misma lista persistida tras volver a entrar.
- Una pareja adoptante-mascota aparece una sola vez. La lista conserva la mascota e indica su disponibilidad actual.
- Solo el titular consulta o modifica sus favoritos; se muestran estados claros para lista vacía y errores de conexión.

### US-18 - Consultar el perfil y las solicitudes previas autorizadas de un adoptante

Como albergue, quiero consultar información autorizada y antecedentes internos de solicitudes para apoyar una evaluación.

- Solo el albergue con autorización vigente consulta vivienda, rutina y convivencia; revocar deniega el acceso desde interfaz y API.
- El historial muestra exclusivamente solicitudes previas del adoptante dirigidas a ese mismo albergue, con estado y fecha; si no hay registros muestra “Sin solicitudes previas”.
- No incluye antecedentes externos, documentos de identidad ni datos de otros albergues. Las 5 h cubren añadir historial interno al consentimiento existente; depende de US-06.

### US-15 - Cargar o reemplazar la foto principal de una mascota del albergue

Como albergue, quiero establecer una foto principal de mi mascota para presentarla en su ficha pública.

- Solo el propietario carga o reemplaza una imagen JPEG/PNG válida de máximo 5 MB; el servidor rechaza archivos inválidos.
- La referencia persiste y la misma foto se muestra en detalle y catálogo React/Android sin deformaciones.
- Un fallo conserva la foto anterior. La estimación es de verificación del flujo ya existente, no una galería de múltiples imágenes.

### US-25 - Activar un segundo factor TOTP para el acceso del albergue

Como albergue, quiero exigir un código temporal además de mi contraseña para proteger el acceso a mi cuenta.

- React permite activar TOTP tras autenticación reciente y confirmar un primer código válido; el secreto no se expone en listados o logs.
- Con TOTP activo, Spring no entrega acceso de negocio hasta validar contraseña y segundo factor; rechaza códigos inválidos, vencidos o reutilizados y limita intentos.
- Se entregan códigos de recuperación de un solo uso y existe desactivación autenticada con segundo factor o recuperación. Se prueban activación, acceso y pérdida del factor.

### US-22 - Verificar la identidad del adoptante con DNI mediante un servicio RENIEC autorizado

Como adoptante, quiero presentar mi DNI y consultar el resultado de verificación para acreditar mi identidad.

- React/Android validan DNI de ocho dígitos y datos requeridos; las imágenes del documento se almacenan de forma privada.
- El backend consulta un servicio autorizado disponible y registra resultado, fecha y referencia. Una coincidencia valida los campos acordados; una discrepancia queda pendiente de revisión.
- Un fallo del servicio no marca la identidad como verificada. No se muestran documentos a otros usuarios. Las 28 h suponen contrato/API y entorno de prueba disponibles; sin ellos queda bloqueada, y una revisión manual no acredita la consulta RENIEC.

### US-23 - Aprobar o rechazar verificaciones de identidad desde ADMIN en React

Como ADMIN, quiero revisar los casos de identidad pendientes para registrar una decisión con trazabilidad.

- Solo ADMIN accede a la lista de verificaciones y abre documentos privados de casos pendientes, con información suficiente para decidir.
- Aprobar o rechazar registra motivo, fecha y revisor; el adoptante consulta su resultado sin acceder a documentos ajenos.
- Se impiden autoaprobaciones y cambios sin permiso, y se conserva historial de decisiones. Depende de US-22; no se presentan revisiones manuales como validaciones automáticas de RENIEC.

### US-20 - Guardar y editar días y franjas horarias disponibles del adoptante

Como adoptante, quiero registrar mis días y horarios disponibles para coordinar una evaluación o visita.

- React/Android permiten agregar, editar y quitar franjas semanales; cada inicio es anterior al fin y las franjas del mismo día no se solapan.
- Los horarios persisten y se recuperan al volver al perfil; se identifica la zona horaria America/Lima y se distinguen de las horas fuera de casa.
- Solo el titular modifica los horarios; el albergue los consulta únicamente con permiso vigente. Un formulario inválido no reemplaza la disponibilidad guardada.

## Criterio común de entrega

Cada historia se considera terminada cuando sus criterios pasan en las interfaces indicadas, los datos persisten, se prueban permisos y errores, se documentan API/migraciones sin secretos y el revisor confirma la evidencia. El responsable coordina la entrega; la colaboración no añade horas fuera de la estimación.

## Aclaraciones de alcance

US-08 utiliza actualización automática cada 10 segundos, cercana al tiempo real; no ofrece push instantáneo. US-10 cubre cancelación antes de aprobar. US-18 limita el historial a solicitudes internas del mismo albergue. US-29 ofrece sugerencias revisables, y US-30 utiliza observaciones de conducta; no deduce temperamento desde una foto. US-22 exige servicio autorizado real.
