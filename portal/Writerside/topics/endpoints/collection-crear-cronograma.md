# Crear un cronograma

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Define un día de la semana y una hora en que se recoge en casa del vecino.</tldr>

`POST /neighbors/{neighborId}/schedules`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/schedules" method="POST"/>

## Ejemplo

Petición:

```http
POST /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/schedules
Authorization: Bearer <token>
Content-Type: application/json

{
  "dayOfWeek": "MONDAY",
  "time": "07:30"
}
```

Respuesta `201`:

```json
{
  "dayOfWeek": "MONDAY",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "neighborId": "01a0d8cb-f219-72a2-b006-5150f0e0d602",
  "status": "ACTIVE",
  "time": "07:30:00"
}
```

## Reglas de negocio

- Un vecino tiene **un solo cronograma `ACTIVE` por día de la semana**: crear otro el mismo día responde `COL-002`.
- No se puede crear un cronograma activo para un vecino `INACTIVE` (`COL-013`).
- El cronograma nace `ACTIVE`. Ver [el ciclo del cronograma](estados-collection.md).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-002` | 409 | El vecino ya tiene un cronograma ACTIVE ese día de la semana. |
| `COL-013` | 409 | Se registró un recojo, se creó un cronograma o se reactivó uno pausado para un vecino INACTIVE. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
