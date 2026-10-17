# Ver un cronograma

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Devuelve un cronograma del vecino.</tldr>

`GET /neighbors/{neighborId}/schedules/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/schedules/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/schedules/01a0d8cb-f219-72a2-b006-5150f0e0d685
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

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-006` | 404 | El cronograma no existe o no es de ese vecino (también responde un `scheduleId` de otro vecino en un recojo). |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
