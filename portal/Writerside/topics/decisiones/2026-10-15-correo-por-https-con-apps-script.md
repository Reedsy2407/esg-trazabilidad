# El correo sale por HTTPS con un Google Apps Script, no por SMTP

- **Estado:** Implementada (M9: puerto `EmailSender`, adaptadores `appsscript`, `fake` y no disponible; plantilla HTML con respaldo en texto plano).
- **Fecha:** 2026-10-15
- **Dónde se aplica:** auth-service, envío de correos; [Correo por HTTPS con Google Apps Script](correo-apps-script.md), `docs/deployment.md`
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

La recuperación de contraseña necesita enviar correo, y el proyecto no paga dominio ni servicio de correo. El plan gratuito de Render **bloquea los puertos de SMTP**, así que enviar por SMTP desde un servicio no conecta. Una cuenta de Gmail dedicada y un Google Apps Script publicado como aplicación web permiten enviar por HTTPS, que sí está abierto.

## Decisión

- auth-service define un **puerto de salida** `EmailSender` con tres adaptadores, elegidos con `EMAIL_PROVIDER`: `appsscript` (producción), `fake` (desarrollo y pruebas) y uno **no disponible** que siempre falla. `fake` y no disponible están prohibidos en Render con la recuperación encendida.
- El adaptador `appsscript` hace un `POST` JSON con un **secreto compartido en el cuerpo** y **sigue a mano** la redirección `302` con un `GET` (repetir el POST duplicaría el correo), solo hacia `script.google.com` o `script.googleusercontent.com`. Sin reintentos.
- El script envía **dos plantillas fijas** (nunca texto libre) con una **plantilla HTML y respaldo en texto plano**, con el remitente visible «Trazabilidad ESG», y solo usa el permiso de envío (`MailApp`).
- Cuota: Google permite unos 100 destinatarios al día a una cuenta de consumo; el servicio se detiene en **80 al día** y responde igual.
- Los registros solo llevan el evento, la duración y un código fijo: nunca el destinatario, el código, el secreto ni la URL.

## Consecuencias

- El remitente es una dirección `@gmail.com`; la entregabilidad fuera de Gmail no está medida.
- Si Google suspendiera la cuenta, la recuperación por correo dejaría de funcionar y quedaría el restablecimiento por un ADMIN.
- Cambiar de proveedor no toca el resto del código: basta otro adaptador del puerto.
- Quien edite el script puede leer los códigos: la cuenta de Gmail y el script son tan sensibles como la clave de firma.

## Alternativas descartadas

- **SMTP:** los puertos están cerrados en el plan gratuito.
- **Un proveedor de correo transaccional:** exige un dominio propio verificado y un plan.
- **Un script con más permisos (leer el buzón):** un permiso más ancho de lo necesario.
