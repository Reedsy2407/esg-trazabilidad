# Listar las cuentas del personal

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Devuelve todas las cuentas del personal, de la más antigua a la más reciente, con quién cambió su estado por última vez.</tldr>

`GET /auth/staff-users`

**Quién puede llamarlo.** Solo ADMIN. La autoridad se comprueba en la base de datos en cada llamada, no en el token (AUTH-006 si la cuenta no es ADMIN).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/staff-users" method="GET"/>

## Ejemplo

Petición:

```http
GET /auth/staff-users
Authorization: Bearer <token>
```

Respuesta `200`:

```json
[
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
]
```

## Reglas de negocio

- Es la pantalla «Cuentas del personal». Cada cuenta trae su rol, si está activa, cuándo se cambió su contraseña y, si fue desactivada, el motivo, quién y cuándo.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-006` | 403 | La operación es de ADMIN y la cuenta de la sesión no lo es (se comprueba en la base de datos, no en el token). |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
