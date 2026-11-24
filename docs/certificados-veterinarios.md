# Certificados veterinarios de Petly (US-59)

La demostración académica usa el perfil `DEMO_AUTOFIRMADA`. El firmante de prueba es una identidad ficticia y los PDFs generados indican «Muestra de prueba · sin validez veterinaria». El despliegue queda deshabilitado por defecto y cada entorno debe configurar explícitamente el certificado público que autoriza.

## Configurar la demostración

Desde `backend-admin`, instala las dependencias y ejecuta el generador en PowerShell:

```powershell
.venv\Scripts\python.exe -m pip install -r requirements.txt
$identidad = Join-Path $env:LOCALAPPDATA 'Petly\us59-demo'
.venv\Scripts\python.exe manage.py generar_certificados_demo --identidad $identidad --salida '../output/pdf/us59-demo'
```

El comando pide la contraseña de la clave o la lee de `PETLY_CERTIFICADOS_CLAVE_PASSWORD`, si el proceso la recibió de un almacén seguro. Crea `clave-demo.pem` cifrada en PKCS#8 y `certificado-demo.pem` si no hay una identidad existente. Reutiliza esos archivos con la misma contraseña. Para volver a generar las muestras, elige otra carpeta de salida. La identidad queda fuera de Git. Si se generó localmente con `contrasena.dpapi.xml`, la cuenta de Windows que la creó puede recuperarla para ese proceso mediante `Import-Clixml` y `ConvertFrom-SecureString -AsPlainText`, sin imprimirla.

Configura estas variables en el proceso de Django y reinícialo:

```powershell
$env:PETLY_CERTIFICADOS_PERFIL = 'DEMO_AUTOFIRMADA'
$env:PETLY_CERTIFICADOS_CERT_PUBLICO = Join-Path $identidad 'certificado-demo.pem'
```

Django usa solo el certificado público. Desde la ficha veterinaria de una mascota de prueba, adjunta la muestra firmada al tipo correspondiente, comprueba «Firma verificada» y abre o descarga el PDF. El propio generador verifica las muestras al firmarlas. Para reproducir la verificación con la CLI de pyHanko, pasa el certificado público en `--trust` y usa `--trust-replace` para limitar el conjunto de confianza.

## Reglas del perfil académico

- Una única firma de aprobación PAdES, con el certificado autofirmado exacto que se configuró en Django y dentro de su periodo de vigencia al verificar.
- Se exige integridad criptográfica y cobertura del documento. Se aceptan actualizaciones incrementales que pyHanko clasifica como datos de validación sin cambios al contenido, siempre que respete las restricciones del documento.
- La validación se realiza sin acceso a la red. Para esta identidad autofirmada de demostración, retirar o sustituir el certificado público configurado deshabilita su aceptación. El perfil académico carece de servicio de revocación y de sello de tiempo de una autoridad.
- El albergue propietario puede cargar, reemplazar y consultar. Solo se guarda un PDF verificado. Un rechazo o un error durante el guardado conserva el certificado anterior.

## Almacenamiento y API

`CertificadoVeterinario` reserva un registro por mascota y tipo (`VACUNACION` o `ESTERILIZACION`). Guarda el archivo, su SHA-256 y el estado de validación. El estado inicial es `PENDIENTE`. Los archivos se ubican bajo `PRIVATE_FILES_ROOT`, en `mascotas/certificados/<mascota>/<tipo>/`, con nombres generados y sin URL pública.

- `GET /api/mascotas/{id}/certificados-veterinarios/` devuelve al albergue propietario los tipos guardados, el estado de validación y la disponibilidad de carga.
- `PUT /api/mascotas/{id}/certificados-veterinarios/{tipo}/` recibe el archivo multipart `archivo` de hasta 10 MB. Devuelve `VALID` al guardar, o HTTP 400 con `UNSIGNED`, `INVALID` o `UNTRUSTED`. Si falta la configuración de confianza, responde HTTP 503 para un PDF con firma aparente.
- `GET /api/mascotas/{id}/certificados-veterinarios/{tipo}/` sirve el PDF al albergue propietario cuando el estado es `VERIFICADO` y el SHA-256 coincide. Los registros pendientes no se abren en el visor.
