# Registrar una empresa

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Registra una empresa consultando a SUNAT por su RUC: el nombre legal **no se escribe**, lo trae SUNAT.</tldr>

`POST /tracked-companies`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies" method="POST"/>

## Ejemplo

Petición:

```http
POST /tracked-companies
Authorization: Bearer <token>
Content-Type: application/json

{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "contactEmail": "contacto@empresa.pe",
  "contactName": "Rosa Medina",
  "contactPhone": "987654321",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789"
}
```

Respuesta `201`:

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

- La empresa queda vinculada a **una asociación** (`associationId`): los kilos de sus certificados son los recojos de esa asociación.
- Si SUNAT responde, la empresa nace `ACTIVE` y `VERIFIED` ("Verificada"). Un contribuyente `NO HABIDO` se registra, con una advertencia en `warning`.
- Si SUNAT no responde se devuelve `RPT-011` y **no se guarda nada**; la persona puede entonces [registrarla como no verificada](reporting-registrar-empresa-no-verificada.md).
- No puede haber dos empresas con el mismo RUC (el RUC de una empresa eliminada queda libre).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-002` | 409 | Se registró una empresa con un RUC ya usado (el RUC de una empresa eliminada queda libre). |
| `RPT-010` | 400 | El RUC falla el formato o el dígito verificador. |
| `RPT-011` | 503 | SUNAT (a través del proveedor) no respondió. No se guardó nada. |
| `RPT-012` | 422 | SUNAT respondió que el RUC no existe. |
| `RPT-013` | 422 | El contribuyente está dado de baja de forma definitiva. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
