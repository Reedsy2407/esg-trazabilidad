# Reactivar un cronograma

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Reanuda un cronograma pausado: `PAUSED` → `ACTIVE`.</tldr>

`PATCH /neighbors/{neighborId}/schedules/{id}/reactivate`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/schedules/{id}/reactivate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/schedules/01a0d8cb-f219-72a2-b006-5150f0e0d685/reactivate
Authorization: Bearer <token>
```

Respuesta `200`:

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

- Solo se reactiva uno `PAUSED`; desde otro estado responde `COL-008`.
- Si mientras estaba pausado se creó otro cronograma `ACTIVE` ese mismo día, responde `COL-002`.
- Para un vecino `INACTIVE` responde `COL-013`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-002` | 409 | El vecino ya tiene un cronograma ACTIVE ese día de la semana. |
| `COL-006` | 404 | El cronograma no existe o no es de ese vecino (también responde un `scheduleId` de otro vecino en un recojo). |
| `COL-008` | 409 | El cambio de estado del cronograma no está permitido desde el estado actual (por ejemplo, reactivar uno cancelado). |
| `COL-013` | 409 | Se registró un recojo, se creó un cronograma o se reactivó uno pausado para un vecino INACTIVE. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
