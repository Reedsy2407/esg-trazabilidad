# Activar un reciclador

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Pasa un reciclador de `INACTIVE` ("Inactivo") a `ACTIVE` ("Activo").</tldr>

`PATCH /associations/{associationId}/recyclers/{id}/activate`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/recyclers/{id}/activate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/recyclers/01a0d8cb-f219-72a2-b006-5150f0e0d685/activate
Authorization: Bearer <token>
```

Respuesta `200`:

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

- Activar un reciclador que ya está activo responde `REC-004`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `REC-001` | 404 | El reciclador no existe o no es de esa asociación. |
| `REC-004` | 409 | Se desactivó un reciclador inactivo o se activó uno activo. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
