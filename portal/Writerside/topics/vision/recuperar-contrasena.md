# Recuperar la contraseña en dos pasos

<primary-label ref="auth"/>

«¿Olvidaste tu contraseña?» funciona como la de GitHub: la persona pide un código al correo, **demuestra que lo tiene antes** de que se le pida nada más, y solo entonces elige la contraseña nueva. Son tres endpoints públicos (sin token):

| Paso | Endpoint | Respuesta |
|---|---|---|
| 1. Pedir el código | [`POST /auth/password-reset/request`](auth-recuperar-pedir-codigo.md) | `202` sin cuerpo, siempre |
| 2. Verificar el código | [`POST /auth/password-reset/verify`](auth-recuperar-verificar-codigo.md) | `200 { "resetToken": "…" }` |
| 3. Elegir la contraseña nueva | [`POST /auth/password-reset/confirm`](auth-recuperar-confirmar.md) | `204` |

## Secuencia

<code-block lang="plantuml" src="../../puml/recuperar-secuencia.puml"/>

## El código

- **8 dígitos** de un generador criptográficamente seguro (los ceros a la izquierda cuentan).
- Dura **10 minutos**, admite **5 intentos** (el quinto fallo lo invalida) y solo hay **un código vivo por cuenta**: pedir otro invalida el anterior.
- Se guarda **solo como HMAC-SHA256** con un *pepper* (una clave que no está en la base de datos), atado a la cuenta y a su propio registro, y se compara en **tiempo constante**. Un hash simple de 10^8 valores se invertiría en segundos si se filtrara la tabla; con el pepper no.
- La cuenta **nunca se bloquea** por intentos fallidos: bloquearla permitiría a cualquiera dejar fuera a cualquiera.

## El token

Un código correcto no cambia la contraseña: se gasta y se canjea por un **token de un solo uso**.

- 32 bytes aleatorios, guardados **solo como HMAC** con el pepper y atados a la cuenta. Dura **10 minutos** y se usa **una vez**; hay un token vivo por cuenta.
- Vive **solo en la memoria del navegador**: no va en la URL, ni en el historial, ni en el almacenamiento. Recargar la página lo pierde y se pide un código nuevo.
- Una contraseña rechazada por la política (`AUTH-007`) **no gasta el token**: se puede escribir otra dentro de los 10 minutos.

<code-block lang="plantuml" src="../../puml/recuperar-token.puml"/>

## La solicitud siempre responde igual

`request` responde **202 sin cuerpo** exista o no la cuenta, esté activa o inactiva, o se haya llegado a un límite, y **hace los mismos pasos** en todos los casos (purga, límites, búsqueda, HMAC, una escritura); el correo sale en otro hilo. Así no se puede averiguar qué correos tienen cuenta ni por el estado ni por el tiempo. `verify` hace lo mismo: un código malo, vencido, usado, mal formado o de una cuenta que no existe da el **mismo `400` `AUTH-011`**, con el mismo trabajo en el servidor.

## Límites guardados en la base de datos

No están en memoria porque los servicios gratuitos se duermen y se reinician.

| Límite | Valor |
|---|---|
| Solicitudes por correo | 3 por hora y 60 s entre ellas |
| Solicitudes por dirección | 10 por hora |
| Verificaciones por dirección | 20 por hora |
| Confirmaciones por dirección | 20 por hora |
| Tope global | **80 correos al día** (la cuota de Google es de unos 100) |

Solo el límite de la dirección de quien pregunta responde un error (`429` `AUTH-012`, con `Retry-After`): no dice nada de ninguna cuenta. Pasado un límite por correo o el tope diario, la respuesta sigue siendo la misma `202` y el servicio lo registra. Las tablas guardan HMAC del correo y de la dirección, nunca los valores.

## El aviso de contraseña cambiada

Después de **cualquier** cambio de contraseña (recuperación, cambio propio o restablecimiento por un ADMIN) se envía un correo que dice cuándo se cambió, sin la contraseña ni ningún código. Es el aviso para quien tiene un buzón comprometido o no hizo el cambio. Es de mejor esfuerzo: **nunca deshace ni hace fallar el cambio**; si el correo no sale, la contraseña ya cambió. Respeta el mismo interruptor y el mismo tope diario.

## Lo que no hace

- El correo es a la vez el usuario y el canal de recuperación: **quien controle el buzón de una persona puede restablecer su cuenta**. El aviso y el registro de auditoría son la mitigación; el correo no se verifica al crear la cuenta.
- No inicia sesión: la persona va al login con la contraseña nueva.
- Los códigos de recuperación de respaldo (para cuentas sin buzón real) no se construyeron: solo queda el restablecimiento por un ADMIN.

Decisiones: [Recuperar la contraseña con un código por correo](2026-10-15-recuperar-contrasena-por-codigo.md) y [Validar el código antes de pedir la contraseña](2026-10-17-recuperacion-en-dos-pasos.md). Cómo sale el correo: [Correo por HTTPS](correo-apps-script.md).
