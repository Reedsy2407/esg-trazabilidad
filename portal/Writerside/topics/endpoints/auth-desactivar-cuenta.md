# Desactivar una cuenta

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Impide que una cuenta inicie sesión, dejando el motivo registrado.</tldr>

`PATCH /auth/staff-users/{id}/deactivate`

**Quién puede llamarlo.** Solo ADMIN. La autoridad se comprueba en la base de datos en cada llamada, no en el token (AUTH-006 si la cuenta no es ADMIN).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/staff-users/{id}/deactivate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /auth/staff-users/01a0d8cb-f219-72a2-b006-5150f0e0d685/deactivate
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
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

- El motivo es obligatorio (hasta 500 caracteres) y queda guardado junto con quién y cuándo.
- Un ADMIN no puede desactivar su propia cuenta (`AUTH-009`): así el sistema nunca se queda sin su último ADMIN activo.
- Un token ya emitido a esa cuenta sigue valiendo hasta que vence (máximo 1 hora). Las acciones de ADMIN son la excepción: se comprueban en la base de datos, así que un ADMIN desactivado pierde su poder al instante.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-003` | 404 | El id no corresponde a ninguna cuenta, o la cuenta de la sesión ya no está activa. |
| `AUTH-006` | 403 | La operación es de ADMIN y la cuenta de la sesión no lo es (se comprueba en la base de datos, no en el token). |
| `AUTH-009` | 409 | Un ADMIN intentó desactivar o restablecer su propia cuenta. |
| `AUTH-010` | 409 | Se desactivó una cuenta inactiva o se reactivó una activa. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
