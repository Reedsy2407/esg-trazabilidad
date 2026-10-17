# Iniciar sesión

<primary-label ref="auth"/>
<secondary-label ref="estable"/>

<tldr>Cambia el correo y la contraseña de una cuenta del personal por un token (JWT) con el que se llama a los demás endpoints.</tldr>

`POST /auth/login`

**Quién puede llamarlo.** Público: no lleva token.

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/login" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/login
Authorization: Bearer <token>
Content-Type: application/json

{
  "email": "ana.paredes@asociacion.pe",
  "password": "una frase larga que solo yo uso"
}
```

Respuesta `200`:

```json
{
  "accessToken": "<token JWT>"
}
```

## Reglas de negocio

- El correo se compara en minúsculas y la contraseña se normaliza (NFKC) antes de compararla.
- Una cuenta desactivada no inicia sesión, y la respuesta es la misma que con una contraseña incorrecta: no se revela cuál fue el motivo.
- El token está firmado con HS256, dura **1 hora** y no se renueva. Lleva el id de la cuenta, el correo y el rol; si la persona todavía debe elegir su propia contraseña (cuenta nueva o restablecida por un ADMIN) lleva además `pwdChange: true`.
- Los otros tres servicios validan el token por firma y vencimiento, sin llamar a auth-service.
- Hay un límite de **5 intentos por minuto por dirección**; cuenta todos los intentos, también los exitosos, y el cupo se repone poco a poco (uno cada 12 s aproximadamente). Al superarlo responde 429 con `Retry-After`. El límite está en la memoria de la instancia: se reinicia cuando el servicio duerme o se reinicia.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-001` | 401 | El correo o la contraseña no coinciden, o la cuenta está desactivada. La respuesta es la misma en los tres casos. |
| `AUTH-004` | 429 | Se superó el límite de intentos de `POST /auth/login` (o de cambio de contraseña propia) desde la misma dirección. Trae `Retry-After`. |

Como cualquier endpoint de auth-service: con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
