# Registrar una certificación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Registra una certificación de la asociación con su tipo y sus fechas.</tldr>

`POST /associations/{associationId}/certifications`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/certifications" method="POST"/>

## Ejemplo

Petición:

```http
POST /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/certifications
Authorization: Bearer <token>
Content-Type: application/json

{
  "certificationType": "Certificación de formalización",
  "expirationDate": "2027-03-10",
  "issuedDate": "2025-03-10"
}
```

Respuesta `201`:

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

- La fecha de emisión debe ser anterior a la de vencimiento.
- Cuando una certificación vence sin renovarse, recycler-service avisa por evento (`CertificationExpiredEvent`) y collection-service **bloquea** nuevos recojos de esa asociación (`COL-009`) hasta que se renueve.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `CER-002` | 404 | Se registró una certificación para una asociación inexistente. |
| `CER-003` | 400 | Las fechas de la certificación (o la nueva fecha de renovación) quedan en orden inverso. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
