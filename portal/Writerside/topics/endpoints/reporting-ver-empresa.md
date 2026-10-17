# Ver una empresa

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los datos de una empresa: SUNAT (estado, condición, fecha de consulta), contacto, estado y verificación.</tldr>

`GET /tracked-companies/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "contactEmail": "contacto@empresa.pe",
  "contactName": "Rosa Medina",
  "contactPhone": "987654321",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789",
  "status": "ACTIVE",
  "sunatCheckedAt": "2026-10-16T15:20:00Z",
  "sunatCondicion": "HABIDO",
  "sunatEstado": "ACTIVO",
  "verification": "VERIFIED",
  "warning": null
}
```

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
