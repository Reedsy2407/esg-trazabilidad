# Listar los recojos de un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los recojos del vecino, paginados, con un rango opcional de fechas.</tldr>

`GET /neighbors/{neighborId}/collection-records`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/collection-records" method="GET"/>

## Ejemplo

Petición:

```http
GET /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/collection-records?from=2026-09-01&to=2026-09-30&page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
      "collectionDate": "2026-09-17",
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "neighborId": "01a0d8cb-f219-72a2-b006-5150f0e0d602",
      "scheduleId": "01a0d8cb-f219-72a2-b006-5150f0e0d603",
      "weightKg": 12.5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- `from` y `to` (formato `AAAA-MM-DD`) acotan por fecha de recojo, ambas incluidas.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
