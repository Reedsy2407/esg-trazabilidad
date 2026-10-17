# Reactivar un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Devuelve a un vecino `INACTIVE` al estado `ACTIVE`.</tldr>

`PATCH /neighbors/{id}/activate`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{id}/activate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d685/activate
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
```

Respuesta `200`:

```json
{
  "address": "Av. Los Olivos 123",
  "district": "Comas",
  "districtUbigeo": "150110",
  "fullName": "Ana Paredes",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "phone": "987654321",
  "status": "ACTIVE"
}
```

## Reglas de negocio

- El motivo es obligatorio (hasta 500 caracteres).
- Los cronogramas pausados al desactivarlo no se reanudan solos: se reactivan uno por uno.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-012` | 409 | Se desactivó un vecino inactivo o se activó uno activo. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
