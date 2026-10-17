# Crear una cuenta del personal

<primary-label ref="auth"/>
<secondary-label ref="reciente"/>

<tldr>Da de alta a una persona del personal con una contraseña inicial que define quien la crea.</tldr>

`POST /auth/staff-users`

**Quién puede llamarlo.** Solo ADMIN. La autoridad se comprueba en la base de datos en cada llamada, no en el token (AUTH-006 si la cuenta no es ADMIN).

## Contrato

<api-endpoint openapi-path="../../specs/auth-service.json" endpoint="/auth/staff-users" method="POST"/>

## Ejemplo

Petición:

```http
POST /auth/staff-users
Authorization: Bearer <token>
Content-Type: application/json

{
  "email": "ana.paredes@asociacion.pe",
  "fullName": "Ana Paredes",
  "password": "una frase larga que solo yo uso",
  "role": "ADMIN"
}
```

Respuesta `201`:

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

- La cuenta nace activa y con `mustChangePassword: true`: la persona debe elegir su propia contraseña en su primer ingreso.
- El rol es opcional y por defecto es `STAFF`. El correo se guarda en minúsculas y necesita un punto en el dominio.
- La contraseña inicial sigue la política; la pantalla la genera en el navegador y no se vuelve a mostrar.
- No es idempotente: repetirla con el mismo correo responde `AUTH-002`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `AUTH-002` | 409 | Se intentó crear una cuenta con un correo ya registrado (se compara en minúsculas). |
| `AUTH-005` | 400 | El correo no tiene la forma `nombre@dominio.tld` (falta un punto en el dominio). |
| `AUTH-006` | 403 | La operación es de ADMIN y la cuenta de la sesión no lo es (se comprueba en la base de datos, no en el token). |
| `AUTH-007` | 400 | La contraseña tiene menos de 15 o más de 64 caracteres, está en la lista de contraseñas comunes, incluye el correo de la cuenta o repite la actual. |

Como cualquier endpoint de auth-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
