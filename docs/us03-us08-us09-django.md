# US-03, US-08 y US-09 — integración Django

## US-03 — datos vigentes e invalidación de compatibilidad

`GET/PATCH /api/acceso/mi-perfil/` permite al adoptante actualizar su perfil y devuelve `compatibilidad_revision`. Django aumenta esa revisión cuando cambian vivienda, patio, convivencia, rutina o preferencias de adopción. El consumidor debe usarla como parte de la llave de caché junto con la versión del catálogo.

El servicio interno `GET /api/mascotas/datos-compatibilidad/` entrega únicamente mascotas disponibles de albergues activos, ordenadas por ID. Incluye los atributos usados por el cálculo y `actualizado_en`; responde `ETag`/`Last-Modified` y acepta `If-None-Match` (`304` cuando no hubo cambios). Se autentica con `X-Service-Token`, comparado en tiempo constante. Configurar `PET_COMPATIBILITY_SERVICE_TOKEN` en Django y el mismo secreto en el consumidor Spring; no exponerlo en clientes web o Android.

La versión para invalidación es la combinación `(compatibilidad_revision, catalogVersion)`. Así se invalida por cambios de perfil y por altas, ediciones, bajas lógicas o disponibilidad de mascotas. Django no persiste una puntuación por adoptante: entrega datos actuales y el cálculo se vuelve a hacer con el perfil actualizado.

## US-08 — línea de tiempo

Las solicitudes guardan el adoptante propietario y tienen eventos cronológicos inmutables en `SolicitudEvento`. La creación y cada transición del servicio `cambiar_estado_solicitud` escriben el evento dentro de la misma transacción que actualiza la solicitud. El historial existente se migra como `REGISTRO_HISTORICO`, sin inventar transiciones pasadas; se vinculan cuentas existentes por documento cuando hay coincidencia.

`GET /api/acceso/solicitudes/{id}/historial-seguimiento/` devuelve `estadoActual`, `eventos` y `fechasClave`. Solo puede consultarlo el adoptante titular o el albergue de la solicitud. La misma línea registra generación, envío y firma del contrato.

## US-09 — generación y firma

`POST /api/contratos/solicitudes/{id}/generar/` está restringido al albergue propietario y solo acepta solicitudes aprobadas. Crea una copia HTML versionada con hash SHA-256 y datos de adoptante, albergue y mascota; luego envía al adoptante un código aleatorio de 8 dígitos, válido por 15 minutos. El adoptante revisa el contrato recibido por correo y comparte el código con el albergue. `POST /api/contratos/solicitudes/{id}/aprobar-token/` solo acepta ese código una vez, limita los intentos y registra qué cuenta autenticada del albergue lo presentó. `GET /api/contratos/` y `GET /api/contratos/{uuid}/` solo muestran contratos al adoptante titular, al albergue propietario o al administrador técnico.

Como no hay proveedor elegido en el proyecto, el adaptador implementa un protocolo REST reemplazable. El proveedor recibe `reference`, `title`, `document` (`format: html`, `sha256`, `content`), `signers` y `webhookUrl`; responde con `id` o `documentId`, y opcionalmente `signingUrl`/`url`. Se envía `Idempotency-Key` estable. El adaptador puede reemplazarse por una implementación específica del proveedor sin cambiar modelos ni endpoints.

El código de correo es una aprobación registrada en Petly; no constituye una firma digital certificada ni verifica por sí solo la identidad del adoptante. La constancia queda descargable por adoptante y albergue. Para desarrollo, los mensajes se guardan en `backend-admin/emails`; para enviar a bandejas reales configura `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USE_TLS`, `EMAIL_HOST_USER` y `EMAIL_HOST_PASSWORD`. `ADOPTION_CONTRACT_TERMS`, su versión y responsable deben contener datos aprobados por el equipo; sin ellos, Django conserva el borrador pero responde `409`. Se puede elegir el adaptador REST en vez del flujo por correo mediante `CONTRACT_SIGNATURE_PROVIDER=generic-rest` y configurar sus credenciales/webhook. No se incluyen cláusulas legales inventadas.

El webhook es `POST /api/contratos/webhooks/firma/`. Requiere `X-Provider-Timestamp` (epoch Unix) y `X-Provider-Signature: sha256=<hex>`, calculada como HMAC-SHA256 del secreto sobre `timestamp + "." + cuerpo HTTP crudo`. Se limita a cinco minutos de diferencia y deduplica por proveedor/ID de evento. Se aceptan estados `sent`, `pending`, `viewed`, `in_progress`, `signed`, `completed`, `declined`, `rejected` y `expired`. No se persiste el cuerpo completo del webhook; solo su hash y los identificadores necesarios para auditoría.

Errores del proveedor no revierten la aprobación ni el borrador: el contrato queda `ERROR` para reintentar y la API responde `502` o `503`. HTTP no se mantiene dentro de una transacción de base de datos. La clave de idempotencia ayuda ante reintentos; el adaptador requiere que el proveedor la respete.
