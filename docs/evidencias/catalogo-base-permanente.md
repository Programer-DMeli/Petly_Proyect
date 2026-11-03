# Catálogo y compatibilidad con las mascotas reales

Verificación del 2 de noviembre de 2026 (America/Lima), con Spring, Django y MariaDB locales. React y Android autenticados consultan `/api/catalogo`; Spring lee `database_petly.mascotas_mascota` y Django sirve las fotografías indicadas por `foto_principal`. Android adapta las URLs locales al host del emulador. La opción de desarrollo «Ver pantallas de prueba» sigue siendo una vista ficticia; para estos datos se debe iniciar sesión.

## Comportamiento vigente

El catálogo del adoptante incluye los tres estados de mascotas de albergues activos: disponible, en proceso y adoptado. Mantiene el estado real; solicitar adopción solo está habilitado para disponibles. El cálculo de compatibilidad y el orden siguen siendo los mismos, con puntaje descendente, empate por ID y datos insuficientes al final. No se modifica el estado de adopción por tener un match alto.

Android pide 12 resultados por página, suficientes para las 10 mascotas actuales, y permite avanzar y retroceder si hay más páginas. Los filtros conservan su funcionamiento y regresan a la primera página al aplicarlos.

## Comprobaciones realizadas

- Las APIs devolvieron exactamente los IDs 1–8, 17 y 18: Luna, Toby, Bruno, Miel, Copito, Simba, Domi, Nube, Milo y Mailo; seis disponibles, tres en proceso y una adoptada.
- Las diez imágenes respondieron HTTP 200 con contenido de imagen; su SHA-256 coincidió con el archivo local correspondiente de Django.
- Una cuenta temporal sin cuestionario recibió porcentaje nulo, sin inventar compatibilidad. Después de guardar dos perfiles completos, las diez mascotas tuvieron cobertura del 100%. En cada caso, lista y `/api/v1/matches/mascota/{id}` coincidieron y la suma de aportes, redondeada HALF_UP, coincidió con el porcentaje.
- Casa, 3 horas fuera, sin niños ni otras mascotas: Copito obtuvo 91%. Departamento, 10 horas fuera, con niños y otras mascotas: 65%. Son resultados de las reglas vigentes (30% vivienda, 35% rutina, 35% convivencia), no porcentajes fijos ni predicciones de adopción.
- React se verificó con Chromium y servicios reales: diez tarjetas, diez fotos decodificadas, porcentajes iguales a la API y acceso a solicitar adopción únicamente en las seis disponibles. Desde su cuestionario, cambiar vivienda a Casa y horas a 3, manteniendo niños y otras mascotas, recalculó Copito de 65% a 88%; Android mostró el mismo 88% con la misma cuenta.
- Compilación React y Android, pruebas unitarias Android y pruebas Spring de compatibilidad y paginación correctas. Se agregó cobertura para conservar los tres estados sin alterar sus puntajes.

No se crearon mascotas ni se modificaron los diez registros validados, sus fotos, las reglas globales o los perfiles existentes. El puntaje orienta la búsqueda; el albergue decide cada adopción. Este comportamiento del catálogo reemplaza su exclusión previa de mascotas no disponibles documentada en la evidencia histórica del Sprint 2.

## Validación previa a la publicación

Tras integrar `origin/main`, pasaron las compilaciones de React y Android, las 25 pruebas unitarias Android, las 10 pruebas Spring de compatibilidad y paginación y la nueva prueba Chromium con respuestas simuladas para conservar los tres estados y solicitar adopción solo de disponibles. También pasaron las pruebas simuladas de recálculo y configuración de pesos. La prueba de borradores IA de la misma suite falla buscando el nombre antiguo «Generar borrador desde la foto»; el código publicado ya usa «Sugerir descripción con IA», en una pantalla ajena a estas correcciones. En esta revisión no se repitió el recorrido con servicios reales descrito arriba ni la navegación de páginas en el emulador. El script Python actualizado pasó la revisión de sintaxis.
