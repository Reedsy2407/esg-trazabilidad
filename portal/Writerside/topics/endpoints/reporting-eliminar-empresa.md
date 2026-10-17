# Eliminar una empresa

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Elimina una empresa registrada por error. Solo si no tiene certificados.</tldr>

`DELETE /tracked-companies/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{id}" method="DELETE"/>

## Ejemplo

Petición:

```http
DELETE /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
```

Respuesta `204`, sin cuerpo.

## Reglas de negocio

- **Con certificados, vigentes o anulados, responde 409 `RPT-017`** y la salida es desactivar la empresa.
- Sin certificados se elimina con un motivo obligatorio (hasta 500 caracteres) y su **RUC queda libre** para registrarse de nuevo.
- Responde 204 sin cuerpo.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |
| `RPT-017` | 409 | La empresa tiene al menos un certificado (vigente o anulado). |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
