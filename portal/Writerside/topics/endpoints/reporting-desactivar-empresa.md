# Desactivar una empresa

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Pasa una empresa a `INACTIVE` ("Inactiva"): no se le pueden emitir certificados.</tldr>

`PATCH /tracked-companies/{id}/deactivate`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{id}/deactivate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d685/deactivate
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
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

## Reglas de negocio

- El motivo es obligatorio (hasta 500 caracteres).
- Los certificados ya emitidos no cambian.
- Es la salida para una empresa con certificados, que no se puede eliminar (`RPT-017`).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |
| `RPT-018` | 409 | Se desactivó una empresa inactiva o se activó una activa. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
