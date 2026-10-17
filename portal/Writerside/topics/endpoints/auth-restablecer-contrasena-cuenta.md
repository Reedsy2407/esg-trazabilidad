# Restablecer la contraseña de una cuenta

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Un ADMIN fija una contraseña nueva a otra cuenta, por ejemplo cuando la persona perdió el acceso a su correo.</tldr>

`POST /auth/staff-users/{id}/password`

**Quién puede llamarlo.** Solo ADMIN. La autoridad se comprueba en la base de datos en cada llamada, no en el token (AUTH-006 si la cuenta no es ADMIN).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/staff-users/{id}/password" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/staff-users/01a0d8cb-f219-72a2-b006-5150f0e0d685/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "newPassword": "otra frase larga y distinta"
}
```

Respuesta `204`, sin cuerpo.

## Reglas de negocio

- La cuenta queda con `mustChangePassword: true`: la persona elegirá su propia contraseña al entrar.
- Un ADMIN no puede restablecer su propia cuenta (`AUTH-009`). Sí puede restablecer la de otro ADMIN: un administrador es de plena confianza y la acción queda en el registro de auditoría con ambos ids.
- Se envía el aviso de cambio de contraseña por correo a la cuenta afectada.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-003` | 404 | El id no corresponde a ninguna cuenta, o la cuenta de la sesión ya no está activa. |
| `AUTH-006` | 403 | La operación es de ADMIN y la cuenta de la sesión no lo es (se comprueba en la base de datos, no en el token). |
| `AUTH-007` | 400 | La contraseña tiene menos de 15 o más de 64 caracteres, está en la lista de contraseñas comunes, incluye el correo de la cuenta o repite la actual. |
| `AUTH-009` | 409 | Un ADMIN intentó desactivar o restablecer su propia cuenta. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
