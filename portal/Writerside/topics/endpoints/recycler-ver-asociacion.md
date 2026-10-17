# Ver una asociación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los datos de una asociación: nombre, RUC, contacto y estado.</tldr>

`GET /associations/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations/01a0d8cb-f219-72a2-b006-5150f0e0d685
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
  "registrationNumber": "RN-2024-0123",
  "ruc": "20123456789",
  "status": "ACTIVE"
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `ASO-001` | 404 | El id de la asociación no existe. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
