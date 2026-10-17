# Cambiar mi contraseña

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Permite a la persona con sesión iniciada reemplazar su contraseña, comprobando antes la actual.</tldr>

`POST /auth/me/password`

**Quién puede llamarlo.** Cualquier persona del personal con sesión iniciada (ADMIN o STAFF).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/me/password" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/me/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "currentPassword": "una frase larga que solo yo uso",
  "newPassword": "otra frase larga y distinta"
}
```

Respuesta `204`, sin cuerpo.

## Reglas de negocio

- La contraseña nueva sigue la [política de contraseñas](politica-contrasenas.md): 15 a 64 caracteres, sin reglas de composición, fuera de la lista de comunes, sin incluir el correo y distinta de la actual.
- Tiene el mismo límite de intentos que el inicio de sesión (cupo aparte), porque quien tenga un token podría usarlo para adivinar la contraseña actual.
- Tras el cambio se envía un aviso por correo («tu contraseña fue cambiada»). Si el aviso falla, el cambio no se deshace.
- Los tokens ya emitidos siguen valiendo hasta que vencen (hasta 1 hora): el JWT no se revoca.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-004` | 429 | Se superó el límite de intentos de `POST /auth/login` (o de cambio de contraseña propia) desde la misma dirección. Trae `Retry-After`. |
| `AUTH-007` | 400 | La contraseña tiene menos de 15 o más de 64 caracteres, está en la lista de contraseñas comunes, incluye el correo de la cuenta o repite la actual. |
| `AUTH-008` | 400 | En «Cambiar mi contraseña», la contraseña actual no coincide. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
