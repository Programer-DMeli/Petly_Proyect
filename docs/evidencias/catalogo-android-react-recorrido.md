# Recorrido real: fotos y compatibilidad Android / React

Fecha local: 29 de octubre de 2026 (America/Lima).

Resultado: recorrido completo aprobado con Spring, Django y MariaDB locales, sin simular respuestas de API. Se usó una cuenta nueva identificada de prueba y una mascota de prueba con tamaño, energía y temperamento conocidos. No se modificaron perfiles de usuarios existentes.

## Fallos corregidos

- Android conservaba enlaces de fotos con `127.0.0.1` cuando su API estaba en `10.0.2.2`. Ahora las rutas locales de `/media/` usan la dirección accesible configurada para Django, incluido su puerto. Los enlaces externos se conservan.
- El selector móvil de horas guardaba etiquetas como «Más de 6h» en un campo numérico. Se reemplazó por la entrada de horas reales (0 a 24), con teclado decimal y error de validación visible. No se deducen horas arbitrarias de un intervalo.

## Comprobaciones realizadas

1. El recorrido HTTP creó mascotas identificadas de prueba, cargó una fotografía real y comprobó catálogo, desglose, orden y persistencia. Un guardado inválido no reemplazó el perfil ni los resultados.
2. Android creó una cuenta de prueba y guardó un perfil finalizado: casa, 3 horas fuera, presupuesto 300, sin niños ni otras mascotas. Descargó la foto desde Django, comprobó que fuera decodificable y mostró **100 % compatible** en la tarjeta.
3. Desde el cuestionario de la aplicación se cambió a departamento y 10 horas fuera. Se pulsó Finalizar cuestionario, se confirmó el perfil persistido desde la API y se volvió al catálogo: la tarjeta mostró **65 % compatible** automáticamente.
4. React inició sesión con esa misma cuenta, encontró la misma mascota y mostró **65 % compatible**. La imagen terminó de cargar y su SHA-256 coincidió con el archivo descargado por Android.
5. Desde el formulario React se volvió a casa y 3 horas: **100 %**. Se repitió departamento y 10 horas: **65 %**. La cuenta de prueba quedó con este último perfil.

Los porcentajes corresponden a esta mascota y a la configuración vigente de pesos. No son valores fijos para todas las mascotas. Un perfil sin finalizar o una mascota con datos necesarios desconocidos sigue mostrando información insuficiente.

## Evidencia

- `android-catalogo-antes.png`: tarjeta Android con fotografía y 100 %.
- `android-catalogo-despues.png`: misma tarjeta con fotografía y 65 % tras guardar desde el cuestionario.
- `react-catalogo-paridad-android.png`: mismo caso en React.
- `catalogo-paridad-real.json`: identificador de mascota, porcentajes y SHA-256; sin credenciales.
- `compatibilidad-sprint2-real.json`: recorrido de APIs reales, incluida conservación de datos ante guardado inválido.

Pruebas aprobadas:

- `CatalogoRealJourneyTest`: prueba instrumental real en Medium_Phone (Android 14). Completada mediante Gradle y mediante AndroidJUnitRunner; esta última ejecución conserva la instalación para recuperar las capturas.
- `catalogo-android-paridad-real.spec.js`: una prueba Playwright real aprobada.

Para repetir, iniciar los servicios locales y preparar los registros con `backend-admin/.venv/bin/python scripts/verificar-compatibilidad-sprint2.py`. Ejecutar la clase instrumental `com.petly_app.CatalogoRealJourneyTest` con la variante `petlyIntegrationInstall=true`. La prueba guarda un archivo privado de cuenta en el directorio interno de la app; recuperar ese archivo fuera del repositorio y pasar su ruta como `PETLY_CATALOGO_FIXTURE` al ejecutar Playwright. No publicar sus credenciales ni copiarlo a las evidencias versionadas.
