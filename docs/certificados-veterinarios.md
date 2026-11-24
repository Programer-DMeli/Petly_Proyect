# Certificados veterinarios de Petly (US-59)

La planificación requiere acordar el perfil de firma y contar con un PDF de prueba. El repositorio no contiene ese acuerdo. La API informa `carga_habilitada: false` y el panel bloquea la carga y el reemplazo. La inspección preliminar de un PDF solo comprueba el formato y que exista un campo de firma incorporado con contenido. Una vez hecha esa inspección, la API devuelve `503` mientras el perfil siga pendiente. La petición no escribe archivos ni crea o reemplaza certificados.

## Decisiones necesarias para habilitar la carga

- Definir el formato y nivel de firma PDF admitido, los formatos aceptados de firma y sello de tiempo, y las revisiones del PDF que pueden quedar fuera de la firma.
- Determinar quién puede firmar y cómo se verifica su autorización. Proporcionar las autoridades raíz e intermedias de confianza y los criterios de vigencia.
- Elegir la fuente y el momento de comprobación de revocación, y decidir qué hacer si esa información es inaccesible o inconcluyente.
- Proporcionar un PDF que cumpla el perfil y muestras negativas de firma ausente, contenido alterado, cadena no confiable y las demás condiciones que el equipo decida rechazar.

Con el perfil definido, conectar el verificador y habilitar el guardado únicamente después de una validación completa. El reemplazo debe conservar el archivo vigente hasta que el nuevo quede validado y su asociación se confirme en la base de datos.

## Almacenamiento y API

`CertificadoVeterinario` reserva un registro por mascota y tipo (`VACUNACION` o `ESTERILIZACION`). Guarda el archivo, su SHA-256 y el estado de validación. El estado inicial es `PENDIENTE`. Los archivos se ubican bajo `PRIVATE_FILES_ROOT`, en `mascotas/certificados/<mascota>/<tipo>/`, con nombres generados y sin URL pública.

- `GET /api/mascotas/{id}/certificados-veterinarios/` devuelve al albergue propietario los tipos guardados, el estado de validación y la disponibilidad de carga.
- `PUT /api/mascotas/{id}/certificados-veterinarios/{tipo}/` recibe el archivo multipart `archivo`. Rechaza un PDF ilegible o sin firma incorporada y mantiene suspendido el guardado hasta definir el perfil.
- `GET /api/mascotas/{id}/certificados-veterinarios/{tipo}/` sirve el PDF al albergue propietario cuando el estado es `VERIFICADO` y el SHA-256 coincide. Los registros pendientes no se abren en el visor.
