# Compatibilidad y decisiones de adopción

## Orden de compatibilidad (US-02)

`GET /api/mascotas/compatibilidad/` requiere un adoptante autenticado con perfil. Devuelve mascotas `DISPONIBLE` de albergues activos ordenadas por puntaje descendente; los empates se resuelven por ID ascendente. El endpoint conserva la respuesta Django, pero sus reglas y puntajes deben coincidir con la versión `petly-compat-v1` de Spring. React y Android consumen el resultado de Spring en `/api/catalogo`.

| Criterio | Peso | Regla inicial |
| --- | ---: | --- |
| Vivienda | 30% | Vivienda conocida y tamaño evaluado: 60/100 para perro grande en departamento; 100/100 para los demás pares conocidos. |
| Rutina | 35% | Se compara energía baja/media/alta con horas fuera (más de 8, hasta 8, hasta 4); misma categoría 100, adyacente 70, extremos 35. |
| Convivencia | 35% | Requiere cuestionario completado y temperamento evaluado; sociable 100, tranquilo 90, juguetón 80, activo 75, independiente 70, tímido 60. Niños con mascota tímida: 45; otras mascotas con temperamento activo: 65. |

Los pesos se normalizan con precisión decimal y se redondean al entero más cercano. No se imputan valores desconocidos. Estas reglas son una primera versión explicable y deben revisarse con datos de uso y criterio de bienestar animal; el puntaje orienta la búsqueda y no decide una adopción.

## Decisión de solicitudes (US-07)

El propietario del albergue decide por `POST /api/acceso/solicitudes/{id}/cambiar-estado/` con `estado` y `observaciones_revision`. Observar y rechazar requieren motivo. Una aprobación exige perfil finalizado y autorización vigente del adoptante para ese albergue; bloquea la solicitud y la mascota en una transacción, y deja la mascota `EN_PROCESO`. Las decisiones finales no se reabren. La transición `EN_PROCESO` a `DISPONIBLE` se rechaza mientras exista solicitud aprobada o entregada. Las rutas de actualización/eliminación genéricas y las acciones masivas de admin no pueden saltarse estas reglas.

La migración `0010_observada_horas_fuera` conserva registros existentes al convertir `EN_REVISION` a `OBSERVADA` y agrega las horas fuera de casa al perfil. Motivos y autorizaciones se validan en el servidor; no se devuelve el documento del postulante en el listado.
