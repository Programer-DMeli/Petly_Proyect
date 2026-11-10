# Asesoría técnica y veterinaria gratuita desde Android

En **Mi perfil → Seguimiento y asesoría veterinaria gratuita**, el adoptante
selecciona un seguimiento y describe el problema de adaptación (1–2000 caracteres).
El formulario conserva el motivo si falla el envío, permite reintentar y muestra
la confirmación solo después del registro. Al actualizar, muestra el estado de
las solicitudes guardadas.

## API y persistencia

- `GET /api/seguimientos/adoptante?page=1`: consulta paginada desde Spring Boot.
- `POST /api/seguimientos/adoptante/asesorias`: requiere JWT de adoptante y JSON
  `{"seguimiento": 12, "problema": "No se adapta al quedarse sola."}`.
- Spring reenvía la identidad autenticada a Django. Django valida que el
  seguimiento pertenezca al adoptante, corresponda a una adopción entregada y no
  esté cancelado. El albergue se obtiene de esa adopción; el cliente no elige
  destinatario ni estado inicial.
- La respuesta `201` contiene `id`, `seguimiento`, `problema`, `estado` y
  `creado_en`. El motivo se guarda en el campo existente `problema`.
- Se reutiliza `SolicitudAsesoriaVeterinaria`, propiedad de Django en la MariaDB
  compartida, sin duplicar los datos en una segunda tabla de Spring. El panel
  de seguimientos del albergue ya incluye sus solicitudes recibidas.

## Estado y actualización del entorno

La migración aditiva `contratos.0008_solicitudasesoriaveterinaria_estado`
añade `estado` y asigna `PENDIENTE` a los registros existentes. Después de
respaldar la base, ejecutar `backend-admin/.venv/bin/python backend-admin/manage.py migrate`.
No modifica el motivo ni el seguimiento guardados. Reiniciar Spring y Django e
instalar la nueva versión Android para consumir los endpoints Spring.

Solo el albergue responsable puede llamar a
`POST /api/contratos/asesorias-veterinarias/{id}/cambiar-estado/` con
`{"estado": "EN_ATENCION"}` o `{"estado": "ATENDIDA"}`. Las transiciones son
`PENDIENTE → EN_ATENCION → ATENDIDA`; repetir el estado actual es idempotente y
los retrocesos se rechazan con `409`. El adoptante consulta el estado, sin poder
modificarlo. El detalle del motivo y los cambios de estado no se exponen a otros
adoptantes ni albergues.
