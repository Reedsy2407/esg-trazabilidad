# Ver un reciclador

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los datos de un reciclador de la asociación.</tldr>

`GET /associations/{associationId}/recyclers/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/recyclers/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/recyclers/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "dni": "40123456",
  "fullName": "Ana Paredes",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "phone": "987654321",
  "status": "ACTIVE"
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `REC-001` | 404 | El reciclador no existe o no es de esa asociación. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
