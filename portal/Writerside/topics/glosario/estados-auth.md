# Estados de auth-service

<primary-label ref="auth"/>

## Cuenta del personal

| Valor de la API | Etiqueta | Qué permite | Qué bloquea |
|---|---|---|---|
| `active: true` | Activa | Iniciar sesión, recuperar la contraseña y recibir avisos. | — |
| `active: false` | Desactivada | Nada: es una baja con motivo, quién y cuándo. | No inicia sesión, no recibe códigos de recuperación y su respuesta de login es la misma que con una contraseña incorrecta. |

Se pasa de uno a otro con [Desactivar](auth-desactivar-cuenta.md) y [Reactivar](auth-reactivar-cuenta.md), siempre con motivo. Un ADMIN no puede desactivar su propia cuenta, así el sistema nunca se queda sin su último ADMIN activo.

## Rol

| Valor de la API | Etiqueta | Qué permite |
|---|---|---|
| `ADMIN` | Administrador | Todo lo del personal y, además, listar, crear, desactivar, reactivar y restablecer cuentas. Se comprueba en la base de datos en cada llamada. |
| `STAFF` | Personal | Registrar datos, emitir y anular certificados, cambiar su propia contraseña. No ve ni administra cuentas. |

El rol viaja en el token solo para que la pantalla muestre u oculte opciones; los otros tres servicios lo ignoran.

## Contraseña pendiente de elegir

`mustChangePassword: true` (en el token, `pwdChange: true`) significa que la persona todavía no eligió su propia contraseña: la cuenta es nueva o un ADMIN la restableció. La pantalla la lleva a «Cambiar mi contraseña» antes de cualquier otra cosa. Es una guía de la pantalla, no una regla que el servicio imponga a cada llamada.

## Código y token de la recuperación

Ver el [diagrama de estados](recuperar-contrasena.md): un código nace **vivo**, y termina **gastado** (se usó, lo reemplazó otro o se agotaron los 5 intentos) o **vencido** (10 minutos). Al verificarlo nace un token con el mismo ciclo (vivo, gastado, vencido), también de 10 minutos y un solo uso.
