# Renovar una certificación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Cambia la fecha de vencimiento de una certificación por una nueva.</tldr>

`PATCH /associations/{associationId}/certifications/{id}/renew`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/certifications/{id}/renew" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/certifications/01a0d8cb-f219-72a2-b006-5150f0e0d685/renew
Authorization: Bearer <token>
Content-Type: application/json

{
  "newExpirationDate": "2028-03-10"
}
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

## Reglas de negocio

- La nueva fecha debe ser posterior a la de emisión.
- Al renovar, recycler-service publica `CertificationRenewedEvent` y collection-service **desbloquea** la asociación para registrar recojos.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `CER-001` | 404 | La certificación no existe o no es de esa asociación. |
| `CER-003` | 400 | Las fechas de la certificación (o la nueva fecha de renovación) quedan en orden inverso. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
