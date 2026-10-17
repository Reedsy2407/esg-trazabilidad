# Limitaciones conocidas

Lo que esta plataforma no hace, o hace con un límite aceptado a propósito. Es un proyecto académico, sin validez legal (los PDF lo dicen en el pie de cada página).

## SIGERSOL

- **El dato de jerarquía es declarado.** El cumplimiento de jerarquía y las cifras SIGERSOL las declara el operador de la asociación y el personal las ingresa a mano. Esta aplicación no se conecta con SIGERSOL ni las verifica; pantallas, PDF y CSV lo dicen («Cumplimiento de jerarquía (declarado)»).
- **El código de envío no se puede verificar públicamente.** El comprobante es solo una referencia en texto (código de envío, PDF «Informe» o número y fecha de facturas), sin subir archivo y sin comprobación contra SIGERSOL.
- **El dato por empresa solo existe tras la cuenta de cada empresa** en SIGERSOL; sin ella, no hay forma pública de obtenerlo.
- **Una integración real exigiría un convenio con el MINAM.** No hay una API pública para terceros. Lo único que se lee es el recuento público por distrito de SIGERSOL Municipal (asociaciones y recicladores), que describe el distrito y no a la empresa.

## Sesiones y cuentas

- **JWT sin revocación (decisión de Angel, 2026-10-15).** Dura 1 hora (`EXPIRY_HOURS` en `JwtIssuer`) y nunca se renueva. Los servicios lo validan por firma y vencimiento, sin consultar a auth-service (diseño sin estado, necesario porque los servicios gratuitos se duermen). Tras cambiar o restablecer una contraseña, o desactivar una cuenta, un token ya emitido sigue valiendo hasta que vence. Las acciones de ADMIN son la excepción: auth-service lee la base de datos.
- **Un ADMIN puede restablecer la contraseña de otro ADMIN** (y entrar como esa persona): un administrador es de plena confianza y la acción queda registrada con ambos ids.
- **El backend no obliga el cambio de contraseña pendiente.** `must_change_password` (claim `pwdChange`) solo guía a las pantallas; quien tiene una contraseña recién restablecida puede llamar a la API directamente hasta cambiarla.
- **La bandeja es parte de la seguridad de la cuenta (M9).** «¿Olvidaste tu contraseña?» manda un código de 8 dígitos al correo de la cuenta; ese correo es a la vez el usuario y el canal de recuperación, así que quien controle la bandeja de una persona puede restablecer su cuenta. El aviso «tu contraseña fue cambiada» y el registro de auditoría son la mitigación; el correo no se verifica al crear la cuenta.
- **La recuperación es en dos pasos (M9c): primero el código, luego la contraseña.** El código se valida antes de pedir la contraseña nueva y se canjea por un token de un solo uso (10 minutos) que vive solo en la memoria del navegador. Consecuencias aceptadas: recargar la página o cerrar la pestaña entre los dos pasos pierde el token y hay que pedir un código nuevo (el código ya se gastó al verificarlo); y quien tenga el token (solo existe en esa pestaña) puede cambiar la contraseña durante esos 10 minutos.
- **Un tercero que conozca el correo puede gastar el código vigente de otra persona** con 5 intentos erróneos (la cuenta no se bloquea y el código se pide de nuevo; el límite de 20 verificaciones por hora por dirección lo acota). Los cuerpos vacíos o demasiado largos reciben un `400` de validación distinto de `AUTH-011`, igual para cualquier cuenta.
- **Dos detalles menores de la recuperación, aceptados.** (1) Los tiempos de `verify` para una cuenta real y una inexistente se igualan haciendo el mismo trabajo (la misma consulta con bloqueo, un `UPDATE` y un HMAC), pero una IT solo comprueba que las medianas no difieran en más de 250 ms; no es una garantía de tiempo constante. (2) Cambiar la contraseña por la vía propia o por un ADMIN no gasta un código de recuperación ya emitido: sigue valiendo hasta 10 minutos, y solo lo tiene quien lea el correo (la recuperación misma sí gasta los códigos vivos).
- **Tras una recuperación, la sesión anterior sigue viva hasta 1 hora** (mismo límite del JWT sin revocación).
- **Sin respaldo para cuentas sin bandeja real.** Los códigos de recuperación (corte 3 del diseño) no se construyeron: si el correo de una cuenta no existe o no se puede leer, solo un ADMIN puede restablecerla.

## Exportaciones y demo

- **CSV en Windows con coma decimal.** El CSV usa coma como separador (RFC 4180, BOM, CRLF, números con punto decimal). En un Windows con coma decimal, como es-ES, Excel lo abre en una sola columna: usar Datos > Desde texto/CSV y elegir «,». Con la configuración de Perú, México o en-US abre en columnas.
- **«Distrito desconocido» en la demo.** Los recojos de la siembra son anteriores a la función de distrito, así que el contexto distrital del certificado de demostración lo muestra así.
- **Prueba sensible a la carga.** `SigersolSyncConcurrencyApiIT` falló una vez con la máquina cargada y pasó 3 de 3 sola y en el `verify` completo; si falla en el CI, repetirla antes de buscar un defecto.

## Gasto cero

- **Sin dominio ni correo propio.** El proyecto no paga dominio, correo ni planes. Los correos de recuperación y de aviso salen desde una cuenta de Gmail dedicada mediante un Google Apps Script (`EMAIL_PROVIDER=appsscript`), con dos consecuencias: el remitente es una dirección `@gmail.com` («Trazabilidad ESG») y no una del proyecto, y Google limita la cuenta a unos **100 destinatarios al día** sin garantía (puede cambiar sin aviso). El servicio se detiene en **80 correos al día** y sigue respondiendo igual; si Google suspendiera la cuenta, la recuperación por correo dejaría de funcionar y quedaría el restablecimiento por un ADMIN. El puerto `EmailSender` permite cambiar de proveedor sin tocar el resto.
- **Entregabilidad sin medir.** Un correo automático de una cuenta de consumo puede caer en spam, sobre todo fuera de Gmail; la pantalla avisa de revisar la carpeta de spam. La primera prueba real (a un Gmail y a otro proveedor) la hace una persona tras el despliegue.
- **Servicios gratuitos que se duermen** (Render) y base de datos en el plan gratuito de Neon: la primera petición tras un rato puede tardar o responder 502/504 mientras despierta. Cuando un servicio o su base de datos no responde, la API contesta `503` con el código `SERVICE_UNAVAILABLE` y `Retry-After: 10` (M10), y las pantallas dicen «el servicio de … no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo», sin códigos HTTP. Límites aceptados: el `Retry-After` es una estimación (no se usa para reintentar solo); las escrituras que no obtienen respuesta siguen diciendo que no se sabe si se aplicaron (no se reintentan solas, para no duplicar); y solo se traducen a 503 los fallos de acceso a datos (base de datos inalcanzable, sin conexiones o con tiempo agotado), no los de RabbitMQ, que no están en el camino de una petición (el outbox escribe en la base).

## Dónde está la fuente

Esta página reproduce `docs/known-limitations.md` del repositorio ([repositorio](%repo%)). Cuando difieran, manda el repositorio.
