# Recuperar contraseña: verificar el código

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Segundo paso: valida el código **antes** de pedir la contraseña nueva y entrega un token de un solo uso.</tldr>

`POST /auth/password-reset/verify`

**Quién puede llamarlo.** Público: no lleva token.

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/password-reset/verify" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/password-reset/verify
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "04271983",
  "email": "ana.paredes@asociacion.pe"
}
```

Respuesta `200`:

```json
{
  "resetToken": "01a0d8cb-f219-72a2-b006-5150f0e0d610.<43 caracteres aleatorios>"
}
```

## Reglas de negocio

- Si el código es correcto, lo gasta y responde `{ "resetToken": "..." }`. El token son 32 bytes aleatorios (más el id de su registro), se guarda solo como HMAC con el pepper, atado a la cuenta, y vale **10 minutos** y **un solo uso**. Un token nuevo reemplaza al anterior.
- Con un código incorrecto, vencido, ya usado o mal formado, o con un correo sin cuenta activa, responde el **mismo 400 `AUTH-011`** y hace el mismo trabajo: no revela si la cuenta existe.
- Cada código admite 5 intentos; el quinto fallo lo invalida. La cuenta nunca se bloquea.
- Límite por dirección: 20 verificaciones por hora (`AUTH-012`).
- El código puede traer espacios («1234 5678»): solo cuentan los dígitos.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-011` | 400 | En `verify`: código incorrecto, vencido, ya usado, mal formado o correo sin cuenta activa. En `confirm`: token desconocido, vencido, usado o alterado. La respuesta es idéntica en todos los casos. |
| `AUTH-012` | 429 | La dirección superó el límite de la recuperación (10 solicitudes, 20 verificaciones o 20 confirmaciones por hora). Trae `Retry-After`. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
