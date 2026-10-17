# Recuperar contraseña: pedir el código

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Primer paso de «¿Olvidaste tu contraseña?»: envía un código de 8 dígitos al correo de la cuenta, si existe y está activa.</tldr>

`POST /auth/password-reset/request`

**Quién puede llamarlo.** Público: no lleva token.

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/password-reset/request" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/password-reset/request
Content-Type: application/json

{
  "email": "ana.paredes@asociacion.pe"
}
```

Respuesta `202`, sin cuerpo.

## Reglas de negocio

- **Siempre responde 202 sin cuerpo**, exista o no la cuenta, esté activa o no, o se haya llegado a un límite: el servicio hace los mismos pasos en todos los casos y el correo sale en otro hilo, así que no se puede averiguar qué correos tienen cuenta.
- El código tiene 8 dígitos, dura 10 minutos, admite 5 intentos y solo hay un código vivo por cuenta: uno nuevo invalida el anterior. Se guarda como HMAC con un pepper, nunca en claro.
- Límites guardados en la base de datos: 3 solicitudes por hora y 60 s entre ellas por correo, 10 por hora por dirección y un tope global de **80 correos al día** (bajo la cuota de Google).
- Solo el límite de la dirección de quien pide responde error (429, `AUTH-012`), porque no dice nada de ninguna cuenta.
- El correo sale por HTTPS con un Google Apps Script; ver [Correo por HTTPS](correo-apps-script.md).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-012` | 429 | La dirección superó el límite de la recuperación (10 solicitudes, 20 verificaciones o 20 confirmaciones por hora). Trae `Retry-After`. |

Como cualquier endpoint de auth-service: con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
