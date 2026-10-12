# Cómo comprobar el bloqueo de acceso — US-21

1. Usar una cuenta de prueba registrada, con un correo completo válido. Evitar bloquear cuentas de trabajo del equipo durante una demostración.
2. Abrir React /acceso o la versión Android actualizada. Introducir el correo registrado y una contraseña incorrecta no vacía, por ejemplo x.
3. Primer envío: credenciales inválidas (HTTP 401). Esperar a que termine la petición.
4. Segundo envío consecutivo: cuenta bloqueada (HTTP 423), contador de 1 minuto y botón de acceso deshabilitado.
5. Cambiar a la contraseña correcta no evita el bloqueo. Desde otra interfaz o tras recargar, Spring devuelve el tiempo restante; no comienza otro bloqueo de un minuto.
6. Al vencer los un minuto, el botón se habilita; acceder con contraseña correcta reinicia el contador de fallos.

Un correo como nombre@gmail se rechaza como formato inválido y no llega al acceso. Un correo válido pero inexistente devuelve credenciales inválidas: no existe una cuenta que bloquear. Estos casos son diferentes de dos contraseñas incorrectas de una cuenta registrada.

La cuota por IP también es distinta: más de diez peticiones reales de autenticación en un minuto producen HTTP 429, contador de la ventana restante (hasta un minuto) y botón deshabilitado. Reiniciar una pantalla no modifica los controles del servidor. React y el emulador pueden compartir IP; ejecutar pruebas secuencialmente.

Verificación: 23 pruebas Spring, 2 pruebas React reales y 2 Android específicas aprobadas. Los temporizadores son interfaz; la seguridad y persistencia están en Spring. Las comprobaciones de vencimiento de cuenta en H2 adelantan el vencimiento de su fixture; las ventanas de cuota se esperaron realmente. No se bloquearon cuentas del equipo.

Emulador de revisión: versión debug com.petly_app.integration para conservar la app original. La versión corregida se deja abierta; otra instalación antigua puede conservar las pantallas anteriores hasta recompilarse.
