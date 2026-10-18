# La consola del personal

La consola es la aplicación web con la que el personal de una operadora y su administrador usan los cuatro servicios. Esta página dice qué pantalla usa cada endpoint, qué reglas viven **solo** en la consola (el servicio no las hace cumplir, la pantalla sí) y qué límites tiene.

![Lista de empresas, con su estado](01-empresas.jpg)

![Una empresa: resumen del período y kilos por período](02-empresa.jpg)

![Un certificado, con sus descargas en PDF y CSV](03-certificado.jpg)

![Registrar un recojo](04-registrar-recojo.jpg)

![Registrar el dato declarado de SIGERSOL](05-sigersol-registro.jpg)

Los datos de las capturas son ficticios o entidades públicas usadas como ejemplo, y la cuenta de personal aparece enmascarada.

## Qué pantalla usa cada endpoint

32 de los 49 endpoints de negocio tienen pantalla. Los demás no la necesitan o no se usan a propósito.

| Servicio | Endpoint | Pantalla |
|---|---|---|
| auth | `POST /auth/login` | Iniciar sesión |
| auth | `GET /auth/me` | Mi sesión |
| auth | `POST /auth/password-reset/request`, `/verify`, `/confirm` | Tres pantallas públicas: «¿Olvidaste tu contraseña?», «Revisa tu correo» y «Elige una contraseña nueva» |
| auth | `POST /auth/me/password` | Mi sesión → Cambiar mi contraseña |
| auth | `GET` y `POST /auth/staff-users`, desactivar, reactivar y restablecer | Mi sesión → Cuentas del personal (solo ADMIN) |
| recycler | `GET /associations` | Asociaciones (con filtro de estado); selectores de Registrar recojo, Registrar empresa y SIGERSOL |
| recycler | `GET /associations/{id}` y sus certificaciones | Asociación; Emitir certificado |
| recycler | `POST /associations` | Registrar asociación (el RUC se revisa en la pantalla igual que en el servicio) |
| recycler | suspender y activar | Asociación (pide confirmación; recarga el estado vigente si cambió) |
| recycler | registrar y renovar certificación | Asociación (Registrar certificación; Renovar pide la nueva fecha de vencimiento) |
| recycler | detalle de una certificación; recicladores de una asociación | Sin pantalla |
| collection | `GET /neighbors` | Vecinos (filtros por estado y por distrito, elegido de la lista del INEI); selector de Registrar recojo |
| collection | `POST /neighbors` | Registrar vecino (distrito de la lista del INEI) |
| collection | `GET` y `PATCH /neighbors/{id}` | Vecino; Vecino → Editar |
| collection | desactivar, activar y eliminar un vecino; bitácora de estados | Vecino (motivo obligatorio; la bitácora de cambios de estado) |
| collection | cronogramas de un vecino: ver, crear, pausar, cancelar y reactivar | Vecino (cancelar pide confirmación: es definitivo) |
| collection | `POST` y `GET /neighbors/{id}/collection-records` | Registrar recojo; Vecino (por rango de fechas) |
| collection | `/companies` (crear, listar, ver) | Sin pantalla, a propósito (ver «Dos clases de empresa») |
| reporting | `GET /tracked-companies` y `/{id}` | Empresas, Empresa |
| reporting | `POST /tracked-companies` | Registrar empresa |
| reporting | certificados: listar, ver, PDF y CSV | Empresa, Certificado; Emitir certificado (comprueba solapes) |
| reporting | `GET …/certificate-summary` | Empresa (resumen del período), Emitir certificado (borrador) |
| reporting | `POST …/certificates` y `…/annul` | Emitir certificado; Certificado → Anular certificado |
| reporting | `GET` y `POST /sigersol-syncs` | Registros SIGERSOL, Registrar dato SIGERSOL |

## Reglas que viven solo en la consola

El servicio no las exige; las pantallas sí, y lo dicen.

- **Servicio caído o despertando.** Un solo texto en todas partes, sin códigos de estado y con el siguiente paso: «el servicio de recojos no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo». Cubre los tres estados de pasarela que Render devuelve mientras despierta un servicio gratuito (502, 503 y 504) y el `503` propio del servicio. Sin respuesta alguna dice «Comprueba tu conexión»; cualquier otro 5xx dice «tuvo un problema inesperado». Una escritura sin respuesta o con un 5xx **nunca dice que falló**: dice que no se sabe el resultado y cómo comprobarlo, porque pudo haberse registrado.
- **RUC de una asociación nueva.** La pantalla aplica la misma revisión local que el servicio (11 dígitos, prefijo 10, 15, 16, 17 o 20 y dígito verificador módulo 11) para frenar un error de tipeo antes de enviar. No consulta a SUNAT; si difiere, el servicio responde `ASO-004`.
- **«Por vencer».** Una certificación vigente que vence en **menos de 30 días**, contados con el calendario de Lima. No es un estado del servicio (este solo dice «vencida»): es una regla de la consola y la pantalla lo aclara. El verde, el ámbar y el rojo se usan únicamente en estas tres etiquetas.
- **Emitir un certificado** (un certificado nunca se borra; la única vuelta atrás es anularlo):
  - Una empresa **inactiva** no recibe certificados (decisión del dueño del producto, 2026-10-10). La pantalla lo dice en lugar de mostrar el formulario, y el servicio también lo rechaza (`RPT-015`).
  - Un período que no terminó (fin hoy o después, en Lima) se rechaza e indica desde qué fecha se puede emitir.
  - Un período con 0 kg se rechaza.
  - Un período sin un registro SIGERSOL que lo cubra entero se rechaza antes de preguntar (el servicio diría `RPT-005`), con un enlace para registrarlo.
  - Un período que se solapa con un certificado **Emitido** se rechaza antes de preguntar (`RPT-004`). Uno **Anulado** libera su período y no cuenta.
  - Un fin anterior al inicio se rechaza antes de preguntar (`RPT-009`).
  - La confirmación es una casilla y un botón que nombra el período.
- **Anular un certificado.** Mientras está Emitido, su página ofrece «Anular certificado». Pide un motivo (500 caracteres como máximo) y la misma confirmación de casilla y botón con nombre; quién y cuándo nunca se piden, el servicio los toma del token. Un certificado anulado no se borra: su página muestra el sello «ANULADO» con la fecha, el correo de quien lo anuló y el motivo, con sus cifras congeladas, y el PDF y el CSV llevan las mismas marcas. Sigue en la lista de la empresa con la etiqueta «Anulado» y no alimenta el resumen ni el gráfico. `RPT-019` (ya anulado, por ejemplo por otra persona un momento antes) lo dice y sugiere recargar. Para corregir datos erróneos: anular el certificado, desactivar la empresa, registrar la correcta y emitir de nuevo.
- **Desactivar, reactivar y eliminar un vecino** piden un motivo (500 caracteres) y la confirmación de casilla y botón con nombre; quién lo hace lo toma el servicio del token. Reactivar ofrece los cronogramas que la desactivación pausó, para reanudarlos uno a uno; eliminar cancela de una vez los cronogramas `ACTIVE` y `PAUSED` restantes. Una eliminación rechazada con `COL-011` (el vecino tiene recojos) sugiere «Desactivar».
- **Registro y ciclo de vida de una empresa.**
  - El nombre legal nunca se escribe: el servicio lo pide a SUNAT por el RUC. Los errores hablan claro: `RPT-010` RUC inválido, `RPT-011` SUNAT no respondió (un 503 definitivo: no se guardó nada), `RPT-012` SUNAT no conoce el RUC, `RPT-013` baja definitiva. Un contribuyente «NO HABIDO» se registra y la página de la empresa muestra el aviso de SUNAT.
  - Tras `RPT-011` el formulario ofrece «Registrar como no verificada» con un nombre escrito. La empresa queda **No verificada** y no puede recibir certificados (`RPT-016`) hasta que «Verificar con SUNAT» tenga éxito. Si SUNAT responde después, `RPT-014` retira la oferta.
  - La página de la empresa edita solo el **contacto** (el RUC y la asociación solo se cambian por la API), desactiva, reactiva y elimina con motivo obligatorio. `RPT-018`: ya está en ese estado. `RPT-017`: tiene certificados (los anulados cuentan), así que solo se puede desactivar. Una empresa eliminada desaparece y su RUC queda libre.
- **Detalles de pantalla.** El eje del gráfico de kilos se ajusta a los datos (un paso «redondo», unos tres intervalos), no a un rango fijo. «Registrar recojo» tiene **un** solo control de vecino: un cuadro combinado editable (se escribe un nombre o distrito, flechas y Enter para elegir, Esc para cerrar; editar el texto retira la elección). Los selectores de distrito y de vecino abren con la lista completa al hacer clic y, si se sale sin elegir, restauran la elección anterior. Una empresa con certificados (aunque estén anulados) no muestra el botón «Eliminar empresa», solo el motivo; si apareciera uno mientras tanto, el servicio responde `RPT-017`. Los registros anteriores al origen del dato dicen «Dato anterior al registro de origen».
- **Contexto distrital.** El certificado y el borrador listan los kilos de cada distrito con los recuentos agregados de SIGERSOL Municipal (asociaciones y recicladores), su año y fuente, bajo «Contexto distrital». La nota aclara que describen el distrito, no a la empresa ni a sus proveedores, y nada se redacta como validación (el certificado nunca usa la palabra «verifica»). Un distrito sin datos dice «Contexto de SIGERSOL Municipal no disponible»; los kilos sin distrito dicen «Distrito desconocido». En un certificado las líneas quedan congeladas al emitirlo; en el borrador son vivas. Solo se muestran recuentos, no toneladas (la unidad es desconocida).
- **Origen del dato SIGERSOL.** Registrar una cifra pide «Origen del dato» (declarado por el operador, o con comprobante referenciado) y guarda el correo de quien la ingresa. La referencia es **obligatoria** exactamente con «Con comprobante referenciado» (`RPT-021`) y es «referenciado, no verificado por SIGERSOL»: el texto nunca dice que SIGERSOL lo validó. El formulario conserva la precisión de las columnas y limita la nota a 255 caracteres, porque una más larga fallaría en la base y volvería como `RPT-006` («se superpone»), lo que confundiría. Las cifras siempre dicen «ingreso manual».
- **La cifra de jerarquía es declarada.** Pantallas, PDF y CSV la rotulan «Cumplimiento de jerarquía (declarado)» y llevan la nota de que la declara el operador de la asociación, no es una cifra oficial de SIGERSOL y esta plataforma no la verifica.
- **Cuentas del personal.**
  - La contraseña inicial siempre se genera en el navegador (16 caracteres, `crypto.getRandomValues`, sin caracteres ambiguos); nunca se escribe.
  - Existe solo en la memoria de la página: nunca en el almacenamiento, la URL, el estado del enrutador, la consola ni un atributo del DOM. Se descarta con «Ya la entregué», con un «Descartar» confirmado, al salir de la página o si el intento se rechaza antes de crear la cuenta. Si la pantalla la copió, también se sobrescribe el portapapeles.
  - Tras un resultado desconocido sigue visible y un reintento la reutiliza.
  - Los correos necesitan un punto en el dominio, revisado antes de enviar: el servicio acepta `a@b` en su validación genérica y luego lo rechaza con `AUTH-005`.
- **Roles.** El rol sale del token y solo guía a las pantallas: «Cuentas del personal» y sus acciones se muestran a un ADMIN, y las guardas de ruta alejan a un STAFF; el servicio decide desde la base de datos (`AUTH-006` para quien no es admin). Tras crear o restablecer una cuenta, se envía a la persona a «Cambiar mi contraseña» antes que a nada más (una guarda de la consola, no una regla del servicio). Desactivar y restablecer avisan de que un token ya emitido sigue valiendo hasta que venza (como máximo una hora).
- **Sesión.** «Tu sesión vence a las HH:MM (dura X y no se renueva)» toma la hora del vencimiento del token (en hora de Lima) y la duración de la diferencia entre vencimiento y emisión (una hora, sin revocación). auth-service no emite token de renovación.
- **«¿Olvidaste tu contraseña?»**, tres pantallas públicas. En la primera se escribe el correo y se pasa a la segunda **cualquiera sea la respuesta**, para que nunca diga si la cuenta existe. En la segunda («Revisa tu correo») el correo aparece como texto fijo con «¿No es tu correo? Cambiar», y se escribe el código de 8 dígitos (se acepta pegado con espacios): el código se verifica **antes** de pedir contraseña alguna. «Enviar otro código» se bloquea 60 segundos con una cuenta regresiva visible; solo el final se anuncia a lectores de pantalla. Todo código que no sirve recibe el mismo texto («El código no es válido o venció. Pide uno nuevo.») y a los cinco intentos se vuelve a la primera pantalla. En la tercera («Elige una contraseña nueva») se escribe la contraseña dos veces con las reglas de la política y la opción «Mostrar las contraseñas». El token de restablecimiento vive **solo en la memoria** del navegador: nunca en la URL, el historial, el almacenamiento ni el DOM, y una guarda devuelve a la primera pantalla a quien llegue sin él (recarga o dirección escrita). `AUTH-007` se dice bajo la contraseña y no gasta el token; cuando el token ya no sirve (`AUTH-011`, o pasaron 10 minutos) se vuelve a la segunda pantalla con «La verificación venció (dura 10 minutos y sirve una vez). Pide un código nuevo.». Un `429` dice «Demasiados intentos. Prueba de nuevo en N minutos.» y un 5xx pelado nunca afirma que el código no se envió ni que la contraseña no cambió. Al terminar, el formulario se vacía, el token se descarta y el inicio de sesión muestra «Contraseña cambiada. Inicia sesión con la nueva.» (no se abre sesión). Cada paso mueve el foco a su título, los errores usan `role="alert"` y las tres pantallas se prueban a 360, 768 y 1280 px con AXE y por teclado.
- **Resultado desconocido.** Una escritura sin respuesta, un 408 o un 5xx (Render responde 502 o 504 mientras despierta un servicio) nunca afirma que falló: dice cómo comprobarlo antes de reintentar.

## Límites de la consola

- **Dos roles.** La administración de cuentas es solo de ADMIN; cualquier persona del personal puede emitir certificados, cancelar cronogramas y registrar empresas o cifras SIGERSOL. Las pantallas piden una confirmación explícita antes de las acciones irreversibles o sensibles. Ver [Limitaciones conocidas](limitaciones-conocidas.md).
- **Los selectores cargan una página de 100** (el máximo de página de los servicios) y lo avisan cuando hay más. La búsqueda de vecinos filtra solo lo cargado.
- **El filtro de distrito** es el mismo cuadro combinado con el que se registra o edita un vecino: elige uno de los 50 distritos por su código de ubigeo, así que no falla por mayúsculas ni tildes. No admite texto libre, porque el servicio filtra por el código y no por el nombre.
- **La bitácora de la sesión** vive en el almacenamiento de la pestaña, atada a un inicio de sesión. No es un registro del servidor.
- **Recicladores:** la consola no los gestiona; ninguna pantalla cubre esos endpoints.

## Dos clases de empresa

La **empresa seguida** de reporting-service (`/tracked-companies`) es la empresa a la que se emiten certificados, y la única que usa la consola. collection-service también tiene `/companies` (nombre, RUC, datos de contacto), sin relación con ella: ningún evento las une. Mientras no se decida cuál es «la empresa», `/companies` queda sin usar.

## Pendiente en el servicio

- Editar, desactivar o eliminar registros SIGERSOL. (Los vecinos ya tienen las tres acciones, las empresas seguidas también y las cuentas del personal permiten desactivar y reactivar, sin eliminar.)
- Códigos de recuperación como respaldo para cuentas sin bandeja real: no se construyeron. Cambiar la propia contraseña, el restablecimiento por un admin y el código por correo ya existen.
- Permisos más finos que ADMIN y STAFF (quién puede emitir certificados).
- Buscar vecinos por nombre (la lista solo filtra por estado y distrito exacto).
- El nombre de la asociación en el certificado (la respuesta solo trae su identificador).
- Un endpoint JSON con los pesajes de un certificado (hoy solo viajan en el CSV).
- Decidir entre `/companies` (collection) y `/tracked-companies` (reporting).
