# Recuperar contraseña: elegir la contraseña nueva

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Tercer paso: cambia la contraseña usando el token que entregó `verify`.</tldr>

`POST /auth/password-reset/confirm`

**Quién puede llamarlo.** Público: no lleva token.

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/password-reset/confirm" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/password-reset/confirm
Authorization: Bearer <token>
Content-Type: application/json

{
  "newPassword": "otra frase larga y distinta",
  "resetToken": "01a0d8cb-f219-72a2-b006-5150f0e0d610.<43 caracteres aleatorios>"
}
```

Respuesta `204`, sin cuerpo.

## Reglas de negocio

- Un error de política de contraseña (`AUTH-007`) **no gasta el token**: la persona puede escribir otra contraseña dentro de los 10 minutos.
- Al cambiar la contraseña se gastan el token y cualquier código que siga vivo, todo en una transacción: si dos peticiones llegan a la vez, gana una sola.
- No inicia sesión: la persona va al login con la contraseña nueva. La cuenta queda sin `mustChangePassword`.
- Después se envía el aviso de cambio de contraseña por correo; si falla, el cambio no se deshace.
- Un token desconocido, vencido, usado o alterado responde `AUTH-011`. Límite por dirección: 20 confirmaciones por hora.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-007` | 400 | La contraseña tiene menos de 15 o más de 64 caracteres, está en la lista de contraseñas comunes, incluye el correo de la cuenta o repite la actual. |
| `AUTH-011` | 400 | En `verify`: código incorrecto, vencido, ya usado, mal formado o correo sin cuenta activa. En `confirm`: token desconocido, vencido, usado o alterado. La respuesta es idéntica en todos los casos. |
| `AUTH-012` | 429 | La dirección superó el límite de la recuperación (10 solicitudes, 20 verificaciones o 20 confirmaciones por hora). Trae `Retry-After`. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
