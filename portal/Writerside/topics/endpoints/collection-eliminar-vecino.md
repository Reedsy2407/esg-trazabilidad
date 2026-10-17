# Eliminar un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Elimina a un vecino que se registró por error y no tiene historial.</tldr>

`DELETE /neighbors/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{id}" method="DELETE"/>

## Ejemplo

Petición:

```http
DELETE /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
```

Respuesta `204`, sin cuerpo.

## Reglas de negocio

- Solo se puede eliminar un vecino **sin recojos**: con al menos uno responde `COL-011` y hay que desactivarlo. Los recojos son un [libro inmutable](recojos-inmutables.md).
- El motivo es obligatorio (hasta 500 caracteres). Al eliminar, sus cronogramas `ACTIVE` o `PAUSED` se cancelan.
- Responde 204 sin cuerpo.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-011` | 409 | Se intentó eliminar un vecino con al menos un recojo. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
