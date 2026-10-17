# Ver una empresa (collection-service)

<primary-label ref="collection"/>
<secondary-label ref="interno"/>

<tldr>Devuelve una empresa de collection-service.</tldr>

`GET /companies/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/companies/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /companies/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "address": "Av. Los Olivos 123",
  "contactEmail": "contacto@empresa.pe",
  "contactPhone": "987654321",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789",
  "status": "ACTIVE"
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-004` | 404 | La empresa de collection-service (`/companies`) no existe. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
