# Ver una certificación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve una certificación de la asociación.</tldr>

`GET /associations/{associationId}/certifications/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/certifications/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/certifications/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "certificationType": "Certificación de formalización",
  "expirationDate": "2027-03-10",
  "expired": false,
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "issuedDate": "2025-03-10"
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `CER-001` | 404 | La certificación no existe o no es de esa asociación. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
