# Demostración Sprint 1

## Acceso local

Web: http://localhost:5173. Spring: 8080. Django: 8000. Medium_Phone usa http://10.0.2.2:8080. La configuración de prueba utiliza MariaDB aislada petly_integracion en puerto 13306; no modifica database_petly del equipo.

El archivo /tmp/petly-demo-cuentas.json contiene las tres cuentas privadas generadas para esta sesión. No contiene cuentas reales; no se versiona y tiene permisos 600. Consultarlo localmente para obtener correo/contraseña de cada rol. Las pruebas de recuperación actualizan la contraseña del adoptante en ese archivo. Si se pierde /tmp, generar de nuevo siguiendo instalacion.md.

## Recorrido sugerido

1. ALBERGUE: acceder, actualizar institución/ubicación/capacidad y responsable; crear/editar un área de infraestructura.
2. Registrar mascota con una fotografía propia JPEG/PNG de máximo 5 MB, edad, energía y temperamento. Consultar detalle y editarla.
3. ADOPTANTE: registrarse o acceder. Guardar borrador del cuestionario, completar convivencia y finalizar. Salir de la pantalla y recuperar respuestas.
4. Catálogo: buscar la mascota, combinar especie/tamaño/edad/temperamento, consultar detalle y fotografía. La edad del filtro es total en meses.
5. ALBERGUE: cambiar a En Proceso, Adoptado y Disponible. ADOPTANTE: volver a consultar el catálogo para ver cada cambio.
6. ADOPTANTE: autorizar expresamente a ese albergue. ALBERGUE: actualizar autorizaciones y consultar el perfil. ADOPTANTE revoca; ALBERGUE actualiza y la consulta queda denegada.
7. ADMIN: acceder y registrar una institución seleccionando una cuenta ALBERGUE libre; consultar, editar, desactivar y eliminar una institución vacía. No se eliminan cuentas. Si tiene mascotas, el borrado se rechaza y puede desactivarse. La cuenta técnica Django es distinta.
8. Recuperación: solicitar correo desde React; abrir el enlace web del último correo SMTP local (/tmp/petly-correo-prueba.eml). Cambiar contraseña, acceder con la nueva. El enlace solo sirve una vez y vence a los 15 minutos. No mostrar token/contraseña al público.
9. Android: acceder como ADOPTANTE y comprobar el mismo perfil y catálogo persistidos. Iniciar el emulador por terminal: ~/Android/Sdk/emulator/emulator -avd Medium_Phone &.

Las cuentas y datos de la base aislada son ficticios. React opera con datos persistidos, sin respuestas simuladas. Recargar el navegador o cerrar el proceso Android requiere nuevo acceso: sesiones deliberadamente en memoria. Cada albergue modifica únicamente sus recursos.

## Verificación y cierre

Resultados en entrega-sprint-01.md y evidencias/sprint-01.json. El cierre formal requiere revisión cruzada del equipo. Para usar XAMPP real, completar configuración privada e inspeccionar historial/esquema antes de migrar; no reiniciar ni borrar la base existente. El proveedor de correo externo y teléfono físico quedan como verificaciones del entorno, no se presentan como probados.

## Fotografías y CRUD de ADMIN

Las siete imágenes de img_mascotas están publicadas como Luna, Toby, Bruno, Miel, Copito, Simba y Nube en el albergue de la cuenta demo ALBERGUE. Descripciones sencillas de demostración, sin edades, sexo ni comportamiento inferidos. Buscar esos nombres en React/Android para verlas. Django almacena la imagen original y Spring expone su URL; Android carga el mismo archivo. El encuadre completo evita recortes de la mascota.

Archivo privado /tmp/petly-crud-owner.json: cuenta ALBERGUE libre para demostrar el alta institucional desde ADMIN. Las cuentas anteriores que ya tienen institución no se pueden asignar de nuevo. La cuenta libre fue creada mediante el procedimiento del equipo en la base aislada.

Para volver a publicar sin duplicar esos registros: desde web/, node scripts/publicar-mascotas.mjs, con los servicios activos y PETLY_DEMO_FILE configurado. El script sube archivos desde el formulario real del panel; verifica imagen cargada y conserva IDs en /tmp/petly-publicaciones-img.json. Los originales siguen en img_mascotas y no se incluyen en Git.

Albergue de las fotos: Refugio Verde Petly. Buscar ese nombre en el catálogo permite localizar sus publicaciones. Se verificaron las siete descargas desde Android y la igualdad de los archivos originales; el host 10.0.2.2 está admitido en Django DEBUG.

## Demostrar bloqueo y cuota de US-21

Usar una cuenta de prueba registrada y un correo completo válido. Introducir una contraseña incorrecta no vacía (por ejemplo x): primer fallo 401; segundo 423, contador de un minuto y botón deshabilitado. La contraseña correcta no evita ese bloqueo y recargar/abrir otro cliente vuelve a recibir 423 con el tiempo restante. Un correo como nombre@gmail es un error de formato, no un intento de autenticación.

Para cuota, enviar más de diez peticiones reales de autenticación en un minuto desde la misma IP; 429 indica espera de la ventana restante (hasta un minuto), distinta del bloqueo de cuenta. Las pruebas usan correos ficticios y ventanas reales para no bloquear cuentas de uso del equipo. La prueba de vencimiento de bloqueo en H2 adelanta solo el vencimiento de su fixture; no se afirma que esperó un minuto cronológicos.
