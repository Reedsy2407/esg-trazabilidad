# Correo por HTTPS con Google Apps Script

<primary-label ref="auth"/>

auth-service envía dos correos: el **código de recuperación** y el **aviso de contraseña cambiada**. Salen desde una cuenta de Gmail dedicada, con el nombre visible **«Trazabilidad ESG»**, mediante un **Google Apps Script** publicado como aplicación web.

## Por qué no SMTP

El plan gratuito de Render **bloquea los puertos de SMTP**: un servidor que intente enviar correo por SMTP no conecta. El script recibe la orden por **HTTPS** (puerto 443, que sí está abierto), así que no depende de esos puertos. Además cumple el gasto cero: no se paga dominio ni servicio de correo.

## Cómo funciona

1. auth-service hace un `POST` JSON al script con `{ secret, type, to, code | when }`.
2. El script responde con una redirección `302`. El adaptador **no repite el POST** (duplicaría el correo): hace un `GET` a la URL de la redirección, que devuelve el resultado ya calculado. Solo sigue una redirección y solo hacia `script.google.com` o `script.googleusercontent.com`.
3. El resultado llega en el cuerpo (`{ "ok": true }` o `{ "ok": false, "error": … }`); las aplicaciones web de Apps Script responden siempre HTTP 200.
4. **Sin reintentos**: no hay clave de idempotencia y un reintento podría duplicar el correo. La persona puede pedir otro código tras 60 s.

El secreto compartido va **en el cuerpo** (el script no lee cabeceras) y vive en las propiedades del script y en una variable de Render; nunca en el código.

## Qué manda y qué valida el script

El script solo envía **dos plantillas fijas**; la petición nunca trae asunto ni texto libre. Valida el secreto (comparación sin salida temprana), el formato del destinatario, que el código tenga 8 dígitos y que la fecha del aviso use solo caracteres permitidos. Una fuga del secreto sirve, como mucho, para gastar la cuota o mandar el aviso con un código inventado; no para hacer phishing con contenido a medida. El script no registra destinatarios ni códigos.

Cada correo lleva una **plantilla HTML** (tablas y estilos en línea, ancho máximo de 600 px, legible a 360 px, modo oscuro) con la marca de %product%, y un **respaldo en texto plano**. El código se muestra grande y en monoespaciado, con su vencimiento de 10 minutos y el aviso «si no lo pediste tú, ignóralo».

## Puerto de salida y adaptadores

En auth-service el envío es un **puerto** (`EmailSender`) con tres adaptadores, que se eligen con `EMAIL_PROVIDER`:

| Adaptador | Para qué |
|---|---|
| `appsscript` | Producción: el script de Google descrito arriba. |
| `fake` | Desarrollo y pruebas: guarda los correos en memoria. |
| No disponible (`none`) | Un proveedor que siempre falla; para probar «el correo no sale». |

`fake` y `none` están **prohibidos en Render** mientras la recuperación esté encendida, y ningún secreto tiene valor por defecto: sin las variables, el servicio no arranca. Cambiar de proveedor no toca el resto del código.

## Cuota y tope propio

Google limita una cuenta de consumo a unos **100 destinatarios al día**, sin garantía. El servicio se detiene en **80 correos al día** (códigos y avisos juntos) y sigue respondiendo igual. Si Google suspendiera la cuenta, la recuperación por correo dejaría de funcionar y quedaría el restablecimiento por un ADMIN.

## Límites conocidos

- El remitente es una dirección `@gmail.com` y no una del proyecto.
- La entregabilidad a otros proveedores no se midió: un correo automático de una cuenta de consumo puede caer en spam, por eso la pantalla avisa de revisar esa carpeta.
- La primera prueba real (a un Gmail y a otro proveedor) la hace una persona tras el despliegue.

Decisión: [Enviar el correo por HTTPS con un Google Apps Script](2026-10-15-correo-por-https-con-apps-script.md).
