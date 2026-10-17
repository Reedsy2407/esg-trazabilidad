# auth-service: acceso del personal

<primary-label ref="auth"/>

Cuentas del personal, roles (ADMIN y STAFF), inicio de sesión, emisión del token (JWT) y recuperación de contraseña por correo.

## Cómo se usa

- **Autenticación:** todos los endpoints, salvo los públicos de auth-service, piden `Authorization: Bearer <token>` con el token que entrega [Iniciar sesión](auth-iniciar-sesion.md).
- **Errores:** siempre un cuerpo `application/problem+json` con `status`, `detail` y un `code` estable (`AUTH-011`, `RPT-005`…). La pantalla y los clientes deben decidir por el `code`, nunca por el texto. Ver el [catálogo de errores](errores-auth.md).
- **Servicio caído:** si la base de datos no responde, el servicio contesta `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`; ver [Servicio caído o despertando](servicio-caido.md).
- El token se firma aquí (HS256, 1 hora, sin renovación) y los otros tres servicios lo validan sin llamar a este.
- Las acciones de ADMIN se deciden con la base de datos en cada llamada, no con el rol del token.
- La política de contraseñas y la recuperación están explicadas en [Política de contraseñas](politica-contrasenas.md) y [Recuperar la contraseña](recuperar-contrasena.md).

## Endpoints

| Operación | Método y ruta | Quién | Estabilidad |
|---|---|---|---|
| [Iniciar sesión](auth-iniciar-sesion.md) | `POST /auth/login` | Público | Estable |
| [Ver mi cuenta](auth-mi-cuenta.md) | `GET /auth/me` | Personal con sesión | Estable |
| [Cambiar mi contraseña](auth-cambiar-mi-contrasena.md) | `POST /auth/me/password` | Personal con sesión | Reciente |
| [Recuperar contraseña: pedir el código](auth-recuperar-pedir-codigo.md) | `POST /auth/password-reset/request` | Público | Reciente |
| [Recuperar contraseña: verificar el código](auth-recuperar-verificar-codigo.md) | `POST /auth/password-reset/verify` | Público | Reciente |
| [Recuperar contraseña: elegir la contraseña nueva](auth-recuperar-confirmar.md) | `POST /auth/password-reset/confirm` | Público | Reciente |
| [Listar las cuentas del personal](auth-listar-cuentas.md) | `GET /auth/staff-users` | Solo ADMIN | Reciente |
| [Crear una cuenta del personal](auth-crear-cuenta.md) | `POST /auth/staff-users` | Solo ADMIN | Reciente |
| [Desactivar una cuenta](auth-desactivar-cuenta.md) | `PATCH /auth/staff-users/{id}/deactivate` | Solo ADMIN | Reciente |
| [Reactivar una cuenta](auth-reactivar-cuenta.md) | `PATCH /auth/staff-users/{id}/reactivate` | Solo ADMIN | Reciente |
| [Restablecer la contraseña de una cuenta](auth-restablecer-contrasena-cuenta.md) | `POST /auth/staff-users/{id}/password` | Solo ADMIN | Reciente |

## Etiquetas de estabilidad

**Estable:** en uso por la consola, cubierto por pruebas de integración y sin cambios previstos. **Reciente:** añadido o cambiado entre M7 y M10 (cuentas y roles, recuperación de contraseña); puede ajustarse. **Interno:** existe pero la consola no lo usa.
