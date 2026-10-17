# Verificar con SUNAT

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Vuelve a consultar a SUNAT por el RUC de una empresa; si responde, la empresa pasa a `VERIFIED` con el nombre legal de SUNAT.</tldr>

`POST /tracked-companies/{id}/sunat-refresh`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{id}/sunat-refresh" method="POST"/>

## Ejemplo

Petición:

```http
POST /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d685/sunat-refresh
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

## Reglas de negocio

- Es el paso que habilita los certificados de una empresa registrada como no verificada.
- Si SUNAT no responde, `RPT-011` y la empresa no cambia.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |
| `RPT-011` | 503 | SUNAT (a través del proveedor) no respondió. No se guardó nada. |
| `RPT-012` | 422 | SUNAT respondió que el RUC no existe. |
| `RPT-013` | 422 | El contribuyente está dado de baja de forma definitiva. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
