# Ver un recojo

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Devuelve un recojo del vecino.</tldr>

`GET /neighbors/{neighborId}/collection-records/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/collection-records/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/collection-records/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "collectionDate": "2026-09-17",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "neighborId": "01a0d8cb-f219-72a2-b006-5150f0e0d602",
  "scheduleId": "01a0d8cb-f219-72a2-b006-5150f0e0d603",
  "weightKg": 12.5
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-007` | 404 | El recojo no existe o no es de ese vecino. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
