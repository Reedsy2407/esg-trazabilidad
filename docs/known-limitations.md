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
- **Sin recuperación sin un ADMIN.** «Olvidé mi contraseña» está diseñado (`DISENO-OLVIDE-CONTRASENA.md`, en la carpeta de documentos) y no construido.

## Exportaciones y demo

- **CSV en Windows con coma decimal.** El CSV usa coma como separador (RFC 4180, BOM, CRLF, números con punto decimal). En un Windows con coma decimal, como es-ES, Excel lo abre en una sola columna: usar Datos > Desde texto/CSV y elegir «,». Con la configuración de Perú, México o en-US abre en columnas.
- **«Distrito desconocido» en la demo.** Los recojos de la siembra son anteriores a la función de distrito, así que el contexto distrital del certificado de demostración lo muestra así.
- **Prueba sensible a la carga.** `SigersolSyncConcurrencyApiIT` falló una vez con la máquina cargada y pasó 3 de 3 sola y en el `verify` completo; si falla en el CI, repetirla antes de buscar un defecto.

## Gasto cero

- **Sin dominio ni correo propio.** El proyecto no paga dominio, correo ni planes. La recuperación de contraseña por correo irá por una cuenta de Gmail con Google Apps Script como remitente; el diseño está en `DISENO-OLVIDE-CONTRASENA.md`.
- **Servicios gratuitos que se duermen** (Render) y base de datos en el plan gratuito de Neon: la primera petición tras un rato puede tardar o responder 502/504 mientras despierta.
