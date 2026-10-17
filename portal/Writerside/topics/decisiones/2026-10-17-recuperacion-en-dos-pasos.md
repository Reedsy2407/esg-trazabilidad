# El código se valida antes de pedir la contraseña nueva, con un token de un solo uso

- **Estado:** Implementada (M9c: `POST /auth/password-reset/verify`, `confirm` con `resetToken`, migración `v0.4.0`).
- **Fecha:** 2026-10-17
- **Dónde se aplica:** auth-service y consola, recuperación de contraseña; [Recuperar la contraseña en dos pasos](recuperar-contrasena.md), `docs/security.md`
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

En M9 el endpoint `confirm` recibía a la vez el correo, el código y la contraseña nueva. Una persona que escribía bien el código pero una contraseña rechazada por la política tenía que volver a pedir el código, y un código mal escrito se descubría después de escribir la contraseña. Servicios como GitHub piden primero el código y solo después la contraseña.

## Decisión

- `POST /auth/password-reset/verify` recibe correo y código. Si es correcto, **lo gasta** y devuelve un `resetToken`: **32 bytes aleatorios**, guardado solo como **HMAC con el pepper**, atado a la cuenta, que **vence en 10 minutos** y **se usa una vez**.
- `POST /auth/password-reset/confirm` recibe `resetToken` y `newPassword`. Un error de política (`AUTH-007`) **no gasta el token**.
- Código malo, vencido, mal formado o correo inexistente: el **mismo `400` `AUTH-011`** y el **mismo trabajo**, para no revelar cuentas. Se mantienen los 5 intentos y los límites por dirección (20 verificaciones por hora), guardados en la base.
- El cambio de contraseña, el gasto del token y el de los códigos vivos van en **una transacción**: de dos `confirm` simultáneos gana uno. El aviso por correo sigue sin poder deshacer el cambio.
- En la consola el token vive **solo en memoria**: nunca en la URL, el historial ni el almacenamiento; recargar lo pierde.

## Consecuencias

- Una contraseña rechazada ya no cuesta un código nuevo.
- Recargar entre el segundo y el tercer paso obliga a pedir otro código (el primero ya se gastó).
- Quien tenga el token (solo existe en esa pestaña) puede cambiar la contraseña durante 10 minutos.
- Los tiempos de `verify` se igualan haciendo el mismo trabajo en todos los casos, pero no se garantiza tiempo constante.

## Alternativas descartadas

- **Mantener un solo paso:** obliga a repetir el código ante cualquier error de contraseña.
- **Guardar el token en `sessionStorage` o en la URL:** lo expone a scripts y a historiales.
- **Un código de varios usos:** ampliaría la ventana de adivinarlo.
