# Registrar un reciclador

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Da de alta a un reciclador dentro de una asociación.</tldr>

`POST /associations/{associationId}/recyclers`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/recyclers" method="POST"/>

## Ejemplo

Petición:

```http
POST /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/recyclers
Authorization: Bearer <token>
Content-Type: application/json

{
  "dni": "40123456",
  "fullName": "Ana Paredes",
  "phone": "987654321"
}
```

Respuesta `201`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "dni": "40123456",
  "fullName": "Ana Paredes",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "phone": "987654321",
  "status": "ACTIVE"
}
```

## Reglas de negocio

- El DNI tiene 8 dígitos y no puede repetirse entre recicladores.
- El reciclador nace `ACTIVE`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `REC-002` | 409 | Se registró un reciclador con un DNI ya usado. |
| `REC-003` | 404 | Se registró un reciclador en una asociación inexistente. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
