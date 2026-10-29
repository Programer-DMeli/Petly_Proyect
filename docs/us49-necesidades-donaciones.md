# US-49 — Necesidades y donaciones del albergue

## Flujo implementado

- El albergue propietario publica y edita necesidades con título, descripción, cantidad objetivo, unidad y estado abierta/cubierta.
- Los usuarios consultan por API solo las necesidades abiertas de albergues activos. El panel del albergue ve también las cubiertas.
- El propietario registra aportes manuales asociados opcionalmente a una necesidad: tipo, descripción, cantidad, unidad y fecha.
- El historial de aportes solo está disponible para el rol ALBERGUE y se filtra al albergue autenticado. El formulario no solicita ni almacena datos personales del donante y el endpoint público no expone donaciones.
- Cantidades deben ser mayores que cero. Cuando un aporte se vincula a una necesidad, se comprueba que sea del mismo albergue, esté abierta y use la misma unidad.
- Marcar una necesidad cubierta es una acción explícita del albergue; registrar un aporte no realiza pagos ni transacciones financieras.

## Rutas

| Método y ruta | Acceso | Uso |
| --- | --- | --- |
| `GET /api/necesidades/` | Público | Lista necesidades abiertas de albergues activos. |
| `POST /api/necesidades/` | ALBERGUE | Publica una necesidad en el albergue del token. |
| `GET /api/necesidades/{id}/` | Público | Consulta una necesidad abierta. |
| `PATCH /api/necesidades/{id}/` | Propietario ALBERGUE | Edita campos o cambia estado a `ABIERTA`/`CUBIERTA`. |
| `GET /api/donaciones/` | ALBERGUE | Lista aportes del albergue autenticado. |
| `POST /api/donaciones/` | ALBERGUE | Registra un aporte manual para ese albergue. |

La migración `albergues.0003_necesidadalbergue_donacionalbergue_and_more` crea las tablas y restricciones de cantidad positiva. Se aplicó a la base Petly local el 27 de octubre de 2026 después de crear un respaldo SQL y de fotografías fuera del repositorio.
