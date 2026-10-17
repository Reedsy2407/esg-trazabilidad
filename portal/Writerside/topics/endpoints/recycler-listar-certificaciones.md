# Listar las certificaciones de una asociación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve las certificaciones de la asociación (tipo, fecha de emisión y de vencimiento), paginadas.</tldr>

`GET /associations/{associationId}/certifications`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/certifications" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/certifications?page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
      "certificationType": "Certificación de formalización",
      "expirationDate": "2027-03-10",
      "expired": false,
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "issuedDate": "2025-03-10"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- `expired` es `true` cuando la certificación ya venció. La pantalla marca además «por vencer» cuando faltan menos de 30 días: es una regla de la pantalla, no un estado del servicio.

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
