# Sprint 2 - Planificación con descripciones originales

Actualización: 15 de octubre de 2026.

Objetivo del Sprint: Desplegar el algoritmo de compatibilidad (Matchmaking), los servicios de IA generativa/visión para publicaciones y el flujo central de solicitudes de adopción.

Se conservan las 22 descripciones originales, responsables, revisores, colaboración y criterios de aceptación. Se corrige la capacidad: las 3 horas diarias corresponden a cada integrante. El reparto incluye trabajo compartido y revisión, sin contar una misma hora dos veces.

Capacidad confirmada: 14 días de trabajo x 3 horas diarias x 3 integrantes = 126 horas-persona. Cada integrante dispone de 42 h en las dos semanas, equivalentes a 21 h por semana. Este cálculo incluye los 14 días completos, también los fines de semana.

Distribución propuesta: 17 historias seleccionadas, 114 h; integración final, correcciones y entrega, 12 h. Total del alcance completo planificado para el Sprint 2: 126 h. Las otras 5 historias quedan pendientes con 0 h asignadas; no significa que su implementación no requiera esfuerzo.

Tiempo Estimado (h) expresa el esfuerzo asignado al Sprint 2, sumado entre los tres integrantes. La reserva de 12 h cubre integración/regresión (6 h), correcciones (4 h) y evidencia/demostración (2 h). US-09, US-30, US-49, US-22 y US-23 permanecen aplazadas. US-29 exige disponer del proveedor IA antes de iniciar.

## Cuadro de planificación

| Historia | Responsable principal | Revisor | Colaboración necesaria entre los tres integrantes | Tiempo Estimado (h) |
| --- | --- | --- | --- | --- |
| US-01 - Motor de Recomendación con % de Compatibilidad entre Mascota y Adoptante. | Angie | Meliton | Angie define reglas, pesos y API Spring. Mayra expone atributos de mascotas en Django. Meliton integra el porcentaje en Android y prueba perfiles. | 14 |
| US-06 - Formulario y Envío de Solicitud de Adopción por parte del rol de Adoptante. | Angie | Meliton | Angie implementa formulario React y envío desde Spring. Mayra crea persistencia y validaciones Django. Meliton prueba el envío integrado y los duplicados. | 8 |
| US-07 - Aprobación, Observación o Rechazo de Solicitud desde el Panel de Albergue | Mayra | Angie | Mayra implementa panel React del albergue y decisiones Django. Angie conecta perfil, permisos y consentimiento Spring. Meliton prueba transiciones y aprobaciones simultáneas. | 7 |
| US-02 - Clasificación de posibles Candidatos Mascotas por Compatibilidad entre Mascota y Adoptante a partir del perfil del Adoptante. | Angie | Meliton | Angie ordena mascotas por puntaje y crea el listado React. Mayra garantiza disponibilidad y datos de mascotas. Meliton integra el orden en Android y verifica desempates. | 6 |
| US-08 - Seguimiento de Estado de Solicitud con línea de Tiempo | Angie | Meliton | Angie implementa consulta Spring y línea de tiempo React. Mayra registra eventos y fechas en Django. Meliton verifica orden de eventos, privacidad y estados vacíos. | 5 |
| US-03 - Recálculo de Compatibilidad al Actualizar el Perfil de Adoptante. | Meliton | Angie | Meliton conecta guardado de perfil y refresco en Android. Angie invalida resultados en Spring y actualiza React. Mayra comprueba que se usen los datos vigentes de mascotas. | 4 |
| US-09 - Firma e Integración de Contrato Digital de Adopción | Mayra | Angie | Mayra genera el contrato y conecta el proveedor de firma. Angie verifica identidad de firmantes y permisos. Meliton prueba descarga, fallos y registro de entrega. | 0 (aplazada) |
| US-29 - Descripción generada de la mascota partir de una foto | Mayra | Meliton | Mayra conecta foto, proveedor IA y editor React del albergue. Angie implementa validación de respuesta y gestión de errores. Meliton prueba generación, edición y conservación del texto. | 12 |
| US-04 - Desglose Explicativo del % de Compatibilidad de Mascota en el Panel de Adoptante | Meliton | Mayra | Meliton presenta aportes y explicaciones en Android. Angie expone el desglose Spring y lo muestra en React. Mayra valida categorías y coherencia con la ficha de mascota. | 5 |
| US-28 - Generación Automática de Afiches para Redes Sociales como primer borrador en rol de Albergue | Mayra | Meliton | Mayra crea plantilla, vista previa y descarga en React. Angie facilita datos públicos y validaciones. Meliton comprueba legibilidad, resolución y manejo de fotos faltantes. | 8 |
| US-10 - Cancelación o Desistimiento de Solicitud de Adopcion por parte del Adoptante | Angie | Meliton | Angie implementa confirmación React y petición Spring. Mayra persiste la cancelación y protege la transición en Django. Meliton prueba propiedad y concurrencia con aprobación. | 3 |
| US-05 - Ajustar el peso de los criterios de compatibilidad (ejemplo vivienda, rutina y convivencia) para medir la compatibilidad entre mascota y adoptante. | Angie | Mayra | Angie implementa configuración ADMIN y versiones en Spring. Mayra revisa permisos y validez de los criterios. Meliton verifica recálculo y consistencia de puntajes en Android. | 8 |
| US-30 - Etiquetado Automático de Temperamento con IA según una perfil y foto de la mascota | Mayra | Angie | Mayra conecta perfil, foto y sugerencias IA en el panel. Angie valida etiquetas permitidas y respuesta del proveedor. Meliton prueba confirmación humana y visualización en catálogo. | 0 (aplazada) |
| US-49 - Registro de Necesidades y Donaciones para el Albergue | Mayra | Angie | Mayra implementa necesidades y registro manual de donaciones en Django/React. Angie conecta consulta pública para adoptantes. Meliton prueba cantidades, permisos y privacidad del donante. | 0 (aplazada) |
| US-50 - Publicación de Eventos de Adopción Presencial | Mayra | Meliton | Mayra implementa CRUD de eventos y cancelación Django/React. Angie integra listado público del adoptante. Meliton revisa fechas, ubicación y rechazo a cambios de otro albergue. | 7 |
| US-19 - Carga de Fotos de la Vivienda del Adoptante en el formulario de adopción, desde la app Móvil | Meliton | Mayra | Meliton implementa selección y carga desde Android. Angie vincula identidad, autorización y solicitud desde Spring. Mayra almacena fotos privadas y aplica permisos Django. | 10 |
| US-14 - Guardar Mascotas Favoritas desde el perfil del Adoptante con el Icono de Favoritos. | Meliton | Angie | Meliton implementa icono y lista en Android. Angie persiste favoritos en Spring e integra React. Mayra comprueba que se conserve la disponibilidad real de cada mascota. | 6 |
| US-18 - Verificación de Datos e Historial de Usuarios por Albergue | Angie | Mayra | Angie verifica consentimiento y expone el perfil Spring. Mayra muestra perfil e historial interno autorizado en React/Django. Meliton prueba revocación y acceso desde otro albergue. | 5 |
| US-15 - Carga de Foto de Presentación de la Mascota | Mayra | Angie | Mayra verifica carga, reemplazo y validación Django/React. Angie comprueba la URL y datos de foto en catálogo Spring/React. Meliton verifica descarga y visualización Android. | 1 |
| US-22 - Verificación KYC de Documentos (DNI del Adoptante verificando con datos de Reniec según el DNI) | Angie | Mayra | Angie integra servicio autorizado RENIEC y estados KYC. Mayra protege documentos y consulta administrativa. Meliton implementa captura/carga Android y consulta del resultado. | 0 (aplazada) |
| US-23 - Gestión Administradora de Verificación KYC desde el Panel de Admin en React | Mayra | Angie | Mayra implementa panel ADMIN React y revisión de documentos. Angie integra estados KYC, permisos y auditoría. Meliton verifica resultado visible al adoptante y acceso denegado. | 0 (aplazada) |
| US-20 - Actualización de Disponibilidad y Horarios del Adoptante | Meliton | Angie | Meliton implementa horarios y validación Android. Angie persiste franjas en Spring y crea formulario React. Mayra integra consulta del albergue con autorización vigente. | 5 |
| Integración final, correcciones y entrega | Meliton | Angie y Mayra | Angie: 4 h; Mayra: 4 h; Meliton: 4 h. | 12 |
| **TOTAL DEL ALCANCE COMPLETO DEL SPRINT 2** | | | **Incluye reserva de 12 h** | **126** |

## Reparto de horas por integrante

Las horas de cada fila suman el tiempo asignado de esa historia. No son estimaciones adicionales.

| Trabajo | Angie (h) | Mayra (h) | Meliton (h) | Total (h) |
| --- | --- | --- | --- | --- |
| US-01 | 6 | 4,5 | 3,5 | 14,0 |
| US-06 | 3 | 4 | 1 | 8 |
| US-07 | 2 | 4 | 1 | 7 |
| US-02 | 3 | 1 | 2 | 6 |
| US-08 | 2 | 2 | 1 | 5 |
| US-03 | 1 | 1 | 2 | 4 |
| US-04 | 2 | 1 | 2 | 5 |
| US-10 | 1 | 1 | 1 | 3 |
| US-19 | 2 | 3 | 5 | 10 |
| US-14 | 2 | 1 | 3 | 6 |
| US-18 | 2 | 2 | 1 | 5 |
| US-15 | 0 | 0,5 | 0,5 | 1,0 |
| US-20 | 1 | 1 | 3 | 5 |
| US-05 | 4 | 2 | 2 | 8 |
| US-29 | 2 | 6 | 4 | 12 |
| US-28 | 2 | 3 | 3 | 8 |
| US-50 | 3 | 1 | 3 | 7 |
| Subtotal de historias | 38 | 38 | 38 | 114 |
| Integración, correcciones y entrega | 4 | 4 | 4 | 12 |
| **Total por integrante** | **42** | **42** | **42** | **126** |

## Criterios de aceptación por historia

### US-01 - Motor de Recomendación con % de Compatibilidad entre Mascota y Adoptante.

- Con perfil finalizado, el catálogo React/Android muestra un porcentaje de 0 a 100 calculado en Spring mediante reglas y pesos documentados; los mismos datos producen el mismo resultado.
- El cálculo define cómo tratar datos faltantes y muestra “Información insuficiente” cuando no puede evaluarse. Una mascota con datos desconocidos no recibe una coincidencia perfecta por defecto.
- Las recomendaciones solo incluyen mascotas disponibles de albergues activos; se prueba al menos un perfil compatible, uno poco compatible y uno incompleto. El puntaje orienta y no aprueba adopciones.

### US-06 - Formulario y Envío de Solicitud de Adopción por parte del rol de Adoptante.

- React permite enviar cuando el perfil está finalizado, la mascota está disponible y existe autorización expresa vigente para su albergue.
- Se persiste la solicitud con identificador, titular, mascota, fecha y estado PENDIENTE; se muestra confirmación al adoptante.
- Se rechazan duplicados activos, albergues inactivos y operaciones a nombre de otra persona. Enviar no reserva la mascota; contrato, migración e integración Spring-Django forman parte de esta US.

### US-07 - Aprobación, Observación o Rechazo de Solicitud desde el Panel de Albergue

- Solo el albergue propietario lista y evalúa sus solicitudes desde React. Observar o rechazar exige motivo; aprobar exige autorización vigente y mascota disponible.
- Aprobar actualiza la mascota a En Proceso en una transacción y evita dos aprobaciones activas para la misma mascota, incluso con peticiones simultáneas.
- PENDIENTE u OBSERVADA admiten aprobación o rechazo; la decisión conserva fecha y responsable. Aprobar no registra una adopción finalizada. Se impide liberar manualmente una mascota con aprobación activa.

### US-02 - Clasificación de posibles Candidatos Mascotas por Compatibilidad entre Mascota y Adoptante a partir del perfil del Adoptante.

- El catálogo React/Android clasifica mascotas disponibles de albergues activos usando el perfil del adoptante autenticado y el puntaje de US-01.
- El orden es de mayor a menor compatibilidad; empates se resuelven por identificador de mascota ascendente. Las mascotas con información insuficiente se identifican y aparecen al final.
- Los filtros y la paginación mantienen el orden. Cambiar el perfil y recalcular actualiza la clasificación; no se expone el perfil privado a otros adoptantes.

### US-08 - Seguimiento de Estado de Solicitud con línea de Tiempo

- El titular consulta una línea de tiempo de cada solicitud con estado, fecha y motivo de los eventos registrados, ordenados del más antiguo al más reciente.
- Cada envío, observación, aprobación, rechazo o cancelación agrega un evento sin sobrescribir los anteriores. La línea destaca el estado actual y solo muestra eventos realmente ocurridos.
- Al abrir o pulsar Actualizar se recuperan los eventos persistidos; se manejan lista vacía y error. Otro adoptante no puede acceder. No exige actualización automática ni notificaciones push.

### US-03 - Recálculo de Compatibilidad al Actualizar el Perfil de Adoptante.

- Guardar cambios válidos de vivienda, rutina o convivencia invalida el cálculo anterior; la siguiente consulta muestra porcentajes calculados con el perfil actualizado en React y Android.
- Guardar un formulario inválido no cambia el perfil ni los resultados; una operación fallida informa el error y permite reintentar.
- Depende de US-01; una prueba modifica un criterio relevante y comprueba que no se conserve un resultado obsoleto.

### US-09 - Firma e Integración de Contrato Digital de Adopción

- Desde una solicitud aprobada se genera un contrato con adoptante, mascota, albergue y condiciones de una plantilla revisada por el equipo.
- El proveedor acordado recoge las firmas de ambas partes; se conserva identificador de operación, estado, fechas y documento final descargable solo por las partes autorizadas.
- Solo con firmas confirmadas y entrega registrada se concluye la adopción y se actualiza a Adoptado. Se contemplan rechazo y fallo del proveedor sin duplicar contratos. Requiere proveedor disponible; descargar un PDF o marcar una casilla no satisface la firma integrada.

### US-29 - Descripción generada de la mascota partir de una foto

- El albergue propietario selecciona una foto válida de su mascota y solicita a la IA un borrador de descripción; el proveedor procesa la imagen y devuelve texto editable.
- El borrador describe rasgos visibles y no inventa nombre, salud, edad exacta, raza confirmada ni conducta. Cuando no puede identificar un rasgo, lo omite o indica que no está determinado.
- El albergue puede editar, descartar o confirmar el borrador antes de guardarlo o publicarlo. Un fallo conserva la descripción anterior y permite reintentar; la foto no se publica por generar texto.

### US-04 - Desglose Explicativo del % de Compatibilidad de Mascota en el Panel de Adoptante

- El detalle React/Android muestra vivienda, rutina y convivencia, con peso, aporte y explicación comprensible de cada criterio.
- La suma de aportes coincide con el porcentaje mostrado, aplicando el redondeo documentado; los datos desconocidos se identifican.
- Depende de US-01 y utiliza exactamente la misma versión de reglas que el resultado del catálogo.

### US-28 - Generación Automática de Afiches para Redes Sociales como primer borrador en rol de Albergue

- El afiche incluye foto, nombre, datos básicos y contacto público del albergue, usando una plantilla legible de 1080 x 1350 píxeles.
- React muestra una vista previa y descarga un PNG sin recortar texto ni deformar la imagen; exige completar los datos públicos necesarios.
- Solo el propietario genera el afiche de su mascota. No incluye publicación automática en redes ni generación de nuevas imágenes del animal.

### US-10 - Cancelación o Desistimiento de Solicitud de Adopcion por parte del Adoptante

- Solo el titular puede cancelar una solicitud PENDIENTE u OBSERVADA después de confirmar la acción.
- Se conserva la solicitud con estado CANCELADA y fecha; no se permiten decisiones posteriores ni cancelación de APROBADA o RECHAZADA.
- Si aprobación y cancelación coinciden, solo una transición se completa. Retirar una solicitud ya aprobada requiere otro procedimiento y queda fuera de este alcance.

### US-05 - Ajustar el peso de los criterios de compatibilidad (ejemplo vivienda, rutina y convivencia) para medir la compatibilidad entre mascota y adoptante.

- Solo ADMIN edita pesos no negativos cuya suma sea 100; se rechazan valores inválidos sin reemplazar la configuración vigente.
- Guardar registra versión, fecha y usuario responsable; las nuevas consultas utilizan una única versión y recalculan los resultados afectados.
- Depende de US-01 y US-03; se prueba acceso denegado para adoptantes y albergues.

### US-30 - Etiquetado Automático de Temperamento con IA según una perfil y foto de la mascota

- El albergue propietario solicita etiquetas usando el perfil registrado de la mascota y su foto, sin sustituirlos por el perfil del adoptante; se valida que ambos recursos estén disponibles.
- La IA devuelve sugerencias de categorías permitidas y su explicación. No se considera la imagen una prueba concluyente de conducta: los rasgos deben apoyarse en observaciones registradas; si faltan, se conserva Por evaluar.
- Las etiquetas se muestran como sugerencias hasta confirmación o corrección del albergue. Un error del proveedor no reemplaza las etiquetas vigentes; las confirmadas se reflejan en catálogo y filtros.

### US-49 - Registro de Necesidades y Donaciones para el Albergue

- El propietario crea y edita necesidades con descripción, cantidad, unidad y estado abierta/cubierta; los adoptantes consultan la lista pública.
- Registra manualmente donaciones con fecha, tipo y cantidad válida, sin exponer datos privados del donante en la vista pública.
- Solo el propietario modifica registros de su albergue. No incluye cobros, transferencias ni pasarela de pago.

### US-50 - Publicación de Eventos de Adopción Presencial

- El propietario crea o edita eventos con título, descripción, fecha, horario, ubicación y contacto público; se rechazan campos incompletos o fechas inválidas.
- Los adoptantes ven próximos eventos y su detalle. Cancelar conserva el registro y muestra claramente el estado cancelado.
- Otro albergue no puede modificar el evento. No incluye inscripción, pagos ni recordatorios automáticos.

### US-19 - Carga de Fotos de la Vivienda del Adoptante en el formulario de adopción, desde la app Móvil

- Android permite elegir hasta tres fotos JPEG/PNG de máximo 5 MB cada una y adjuntarlas a una solicitud del titular; valida formato y tamaño en el servidor.
- Las imágenes quedan en almacenamiento privado, vinculadas a la solicitud, y se recuperan al volver al formulario; una subida fallida permite reintentar sin duplicados.
- Solo el titular y el albergue destinatario con autorización vigente acceden a ellas. Revocar bloquea la consulta del albergue; no se sirven como imágenes públicas. Depende de US-06 y US-18.

### US-14 - Guardar Mascotas Favoritas desde el perfil del Adoptante con el Icono de Favoritos.

- El icono permite agregar y quitar favoritos; React y Android recuperan la misma lista persistida tras volver a entrar.
- Una pareja adoptante-mascota aparece una sola vez. La lista conserva la mascota e indica su disponibilidad actual.
- Solo el titular consulta o modifica sus favoritos; se muestran estados claros para lista vacía y errores de conexión.

### US-18 - Verificación de Datos e Historial de Usuarios por Albergue

- Solo el albergue con autorización vigente consulta vivienda, rutina y convivencia; revocar deniega el acceso desde interfaz y API.
- El historial muestra exclusivamente solicitudes previas del adoptante dirigidas a ese mismo albergue, con estado y fecha; si no hay registros muestra “Sin solicitudes previas”.
- No incluye antecedentes externos, documentos de identidad ni datos de otros albergues. Las 5 h cubren añadir historial interno al consentimiento existente; depende de US-06.

### US-15 - Carga de Foto de Presentación de la Mascota

- Solo el propietario carga o reemplaza una imagen JPEG/PNG válida de máximo 5 MB; el servidor rechaza archivos inválidos.
- La referencia persiste y la misma foto se muestra en detalle y catálogo React/Android sin deformaciones.
- Un fallo conserva la foto anterior. La estimación es de verificación del flujo ya existente, no una galería de múltiples imágenes.

### US-22 - Verificación KYC de Documentos (DNI del Adoptante verificando con datos de Reniec según el DNI)

- React/Android validan DNI de ocho dígitos y datos requeridos; las imágenes del documento se almacenan de forma privada.
- El backend consulta un servicio autorizado disponible y registra resultado, fecha y referencia. Una coincidencia valida los campos acordados; una discrepancia queda pendiente de revisión.
- Un fallo del servicio no marca la identidad como verificada. No se muestran documentos a otros usuarios. Las 28 h suponen contrato/API y entorno de prueba disponibles; sin ellos queda bloqueada, y una revisión manual no acredita la consulta RENIEC.

### US-23 - Gestión Administradora de Verificación KYC desde el Panel de Admin en React

- Solo ADMIN accede a la lista de verificaciones y abre documentos privados de casos pendientes, con información suficiente para decidir.
- Aprobar o rechazar registra motivo, fecha y revisor; el adoptante consulta su resultado sin acceder a documentos ajenos.
- Se impiden autoaprobaciones y cambios sin permiso, y se conserva historial de decisiones. Depende de US-22; no se presentan revisiones manuales como validaciones automáticas de RENIEC.

### US-20 - Actualización de Disponibilidad y Horarios del Adoptante

- React/Android permiten agregar, editar y quitar franjas semanales; cada inicio es anterior al fin y las franjas del mismo día no se solapan.
- Los horarios persisten y se recuperan al volver al perfil; se identifica la zona horaria America/Lima y se distinguen de las horas fuera de casa.
- Solo el titular modifica los horarios; el albergue los consulta únicamente con permiso vigente. Un formulario inválido no reemplaza la disponibilidad guardada.

## Criterios comunes y dependencias

La estimación técnica previa de las 22 historias se conserva en 198 h. El total de 126 h corresponde al trabajo planificado para estas dos semanas: 114 h en historias y 12 h de reserva. Las 5 historias aplazadas suman 84 h estimadas. Se conservan descripciones y criterios; una historia solo se cierra con integración, pruebas y revisión.

Tiempo Estimado (h) expresa el esfuerzo asignado al Sprint 2, sumado entre los tres integrantes. La reserva de 12 h cubre integración/regresión (6 h), correcciones (4 h) y evidencia/demostración (2 h). US-09, US-30, US-49, US-22 y US-23 permanecen aplazadas. US-29 exige disponer del proveedor IA antes de iniciar.

US-01 precede a US-02, US-03, US-04 y US-05. US-06 precede a US-07, US-08, US-10, fotos US-19 e historial de solicitudes US-18. US-09 parte de solicitud aprobada; US-23 comparte estados con US-22. La carga existente de US-15 se verifica sin reconstruirla.
