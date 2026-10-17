# La contraseña se recupera con un código de 8 dígitos enviado al correo

- **Estado:** Implementada (M9: `request`, código con HMAC y pepper, límites en la base, respuesta uniforme, aviso de cambio).
- **Fecha:** 2026-10-15
- **Dónde se aplica:** auth-service, recuperación de contraseña; [Recuperar la contraseña en dos pasos](recuperar-contrasena.md), [Operación y seguridad](operacion-y-seguridad.md)
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Antes de M9, una persona que olvidaba su contraseña dependía de que un ADMIN se la restableciera. Una recuperación por correo abre una superficie nueva: un secreto corto (8 dígitos son 10^8 valores) enviado por un canal que no controlamos, y una ruta pública que puede revelar qué correos tienen cuenta.

## Decisión

- La persona pide un código al correo de su cuenta. El código tiene **8 dígitos**, dura **10 minutos**, admite **5 intentos** y solo hay **uno vivo por cuenta**.
- Se guarda **solo como HMAC-SHA256 con un pepper** (variable `PASSWORD_RESET_PEPPER`, de al menos 32 bytes, sin valor por defecto), atado a la cuenta y a su registro, y se compara en tiempo constante.
- La solicitud **siempre responde 202** y hace el mismo trabajo exista o no la cuenta, para no revelar cuentas.
- Los **límites viven en la base de datos**, no en memoria (Render duerme y reinicia): por correo, por dirección y un tope global de 80 correos al día.
- Tras **cualquier** cambio de contraseña se envía un aviso por correo que **nunca deshace ni hace fallar** el cambio.
- La cuenta **no se bloquea** por intentos fallidos.

## Consecuencias

- El correo es a la vez el usuario y el canal de recuperación: quien controle el buzón puede restablecer la cuenta. El aviso y la auditoría son la mitigación.
- Sin cuenta de correo real no hay recuperación: solo un ADMIN puede restablecer. Los códigos de respaldo (corte 3 del diseño) quedan fuera.
- Cómo sale el correo está en [Enviar el correo por HTTPS con un Google Apps Script](2026-10-15-correo-por-https-con-apps-script.md).

## Alternativas descartadas

- **Enlace en vez de código:** exige conocer la URL pública del sitio en el servicio y pone un secreto en una URL que queda en historiales.
- **Bloquear la cuenta tras varios fallos:** permitiría a cualquiera dejar fuera a cualquiera.
- **Limitar en memoria:** se pierde cuando el servicio duerme o se reinicia.
