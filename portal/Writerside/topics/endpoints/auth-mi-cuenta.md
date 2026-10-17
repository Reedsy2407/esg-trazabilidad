# Ver mi cuenta

<primary-label ref="auth"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los datos de la cuenta a la que pertenece el token, entre ellos cuándo se cambió la contraseña por última vez.</tldr>

`GET /auth/me`

**Quién puede llamarlo.** Cualquier persona del personal con sesión iniciada (ADMIN o STAFF).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/me" method="GET"/>

## Ejemplo

Petición:

```http
GET /auth/me
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "active": true,
  "createdAt": "2026-10-16T15:20:00Z",
  "email": "ana.paredes@asociacion.pe",
  "fullName": "Ana Paredes",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "mustChangePassword": false,
  "passwordChangedAt": "2026-10-16T15:20:00Z",
  "role": "ADMIN",
  "statusChangedAt": null,
  "statusChangedBy": null,
  "statusReason": "Cambio de domicilio"
}
```

## Reglas de negocio

- Lee la cuenta de la base de datos, no del token: muestra el rol y el estado actuales.
- `passwordChangedAt` es `null` mientras la persona no haya cambiado nunca su contraseña; la pantalla «Mi sesión» lo muestra.
- `mustChangePassword` es `true` para una cuenta recién creada o restablecida por un ADMIN: la pantalla lleva a la persona a cambiarla antes de seguir (es una guía de la pantalla, no una regla del servicio).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-003` | 404 | El id no corresponde a ninguna cuenta, o la cuenta de la sesión ya no está activa. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
