# Lista de seguridad: los 20 puntos

Una revisión ligera de seguridad de los cuatro servicios y de la consola web, siguiendo la lista de 20 puntos del proyecto en su orden. La arquitectura no cambió por ella. Cada punto tiene un estado: **Cumple**, **Parcial**, **No aplica** u **Omitido a propósito**. Los controles que quedan fuera de la lista están en [Controles adicionales](#controles-adicionales), y las decisiones tomadas a propósito, después.

## La lista

| # | Punto | Estado | Por qué |
|---|-------|--------|---------|
| 1 | Ocultar claves de API | Cumple | La consola no guarda ninguna clave ni secreto: su construcción solo conoce las URL públicas de los cuatro servicios. Los secretos del servidor (`JWT_SECRET`, las contraseñas de la base y del broker, `PASSWORD_RESET_PEPPER` y `EMAIL_WEBHOOK_SECRET`) son variables de entorno: en el grupo de variables de Render en producción y en un archivo local fuera del control de versiones en desarrollo. |
| 2 | Sacar los secretos del historial | Cumple | Un análisis de secretos sobre todo el historial (309 commits) encontró 4 coincidencias, todas falsos positivos: un UUID usado como sujeto de un token de prueba en tres ayudas de pruebas de integración y la constante del alfabeto de contraseñas de la consola. Cada una se permite por commit, archivo y línea exacta, nunca por regla, y el CI repite el análisis sobre todo el historial en cada cambio. Los archivos de variables locales no se versionan y el archivo de ejemplo trae toda clave secreta vacía. |
| 3 | Clave pública para la base de datos | No aplica | El navegador nunca habla con la base de datos, así que no hay una clave pública (anónima) que restringir. Solo los servicios se conectan, con credenciales que nunca salen del servidor. |
| 4 | Activar seguridad por fila | Omitido a propósito | Ver [Sin seguridad por fila](#sin-seguridad-por-fila). |
| 5 | Cifrar datos sensibles | Parcial | En tránsito: HTTPS hasta el navegador y `sslmode=require` hacia la base. Contraseñas: hashes BCrypt. En reposo: sin cifrado por columna, ver [Sin cifrado por columna](#sin-cifrado-por-columna). |
| 6 | Exigir autenticación en el servidor | Cumple | Cada servicio valida el JWT por sí mismo (firma HS256 y vencimiento, sin conexión, contra el secreto compartido). Todo endpoint de negocio exige un token válido; solo son públicos el inicio de sesión, la sonda de vida, `/actuator/info` y la documentación de la API. Las guardas de ruta de la consola son solo comodidad. |
| 7 | Restringir el acceso a los registros | Parcial | Un llamador anónimo no llega a ningún registro. Cualquier persona del personal con sesión llega a todos los registros de negocio; solo la administración de cuentas se limita a un ADMIN (ver [Roles y cuentas](roles-y-cuentas.md)). Los recursos anidados se comprueban contra su padre en la ruta: un certificado bajo otra empresa, un cronograma bajo otro vecino o un reciclador bajo otra asociación responden 404, igual que uno que no existe. |
| 8 | Bloquear la manipulación de campos | Cumple | Ningún endpoint de escritura enlaza una entidad de persistencia. Revisado en el código: las 20 operaciones de escritura reciben uno de 13 `*Request` dedicados o solo identificadores de ruta (7 cambios de estado como suspender o cancelar). Ningún paquete de entrada web referencia una entidad JPA, un repositorio ni un tipo de `jakarta.persistence`. Ninguno de los 13 `*Request` tiene campo para el identificador, el estado, las marcas de tiempo, `active` ni valores calculados como `kilosTrazados`: los fija el servidor. |
| 9 | Proteger las cookies de sesión | No aplica | No hay cookies de sesión: el token viaja en el encabezado `Authorization` y se guarda en `sessionStorage`. Ver [JWT en sessionStorage, con CSP](#jwt-en-sessionstorage-con-csp). |
| 10 | Aplicar hash a las contraseñas | Cumple | BCrypt. Toda contraseña que el servidor acepta sigue la [política de contraseñas](politica-contrasenas.md) (NIST SP 800-63B). Las contraseñas iniciales y de restablecimiento se generan en el navegador (16 símbolos, `crypto.getRandomValues`, unos 92 bits) y la consola nunca las guarda ni las registra; la persona debe elegir la suya en el primer inicio de sesión. La contraseña del primer administrador es aleatoria. |
| 11 | Limitar los intentos de inicio de sesión | Cumple | `POST /auth/login` y `POST /auth/me/password` (que pide la contraseña actual): 5 intentos por minuto por IP de cliente, con un cubo aparte para cada uno, y responden `AUTH-004` (429). Está en memoria, así que es por instancia y se reinicia con ella; basta contra la fuerza bruta sostenida en una sola instancia. Un correo equivocado y una contraseña equivocada reciben el mismo `AUTH-001`. |
| 12 | Protección contra bots | Omitido a propósito | Ver [Sin CAPTCHA](#sin-captcha). |
| 13 | Parametrizar las consultas | Cumple | Consultas derivadas de Spring Data JPA y JPQL con parámetros enlazados. Ninguna consulta se arma concatenando texto y no hay consultas nativas. |
| 14 | Validar las entradas | Cumple | `jakarta.validation` en cada registro de solicitud, más las invariantes del dominio, respondidas como 4xx con el código propio del servicio. Dos entradas que antes terminaban en 500 ya no lo hacen: un correo sin punto en el dominio (`AUTH-005`, 400) y un período de certificado cuyo fin es anterior al inicio (`RPT-009`, 400, tanto al emitir como en el resumen borrador). |
| 15 | Sanear el contenido del usuario | Cumple | Pantallas: Angular escapa toda interpolación y el código de la consola no tiene `innerHTML`, `bypassSecurityTrust*`, `eval` ni `document.write` (el único `innerHTML` es una prueba que lee el DOM); la CSP permite scripts solo del propio sitio. Exportaciones: el CSV del certificado escribe todo texto tipeado (nombre de empresa, RUC) con una comilla simple al inicio si empieza con `=`, `+`, `-`, `@`, un tabulador o un retorno de carro, para que la hoja de cálculo lo abra como texto y no como fórmula. Los números (kilos, porcentajes) nunca llevan prefijo. El PDF lo dibuja PDFBox como texto, que no ejecuta nada. |
| 16 | Restringir los archivos subidos | No aplica | No hay subidas de archivos. Ver [Sin subidas](#sin-subidas). |
| 17 | Devolver solo los datos necesarios | Cumple | Todo endpoint responde un `*Response` dedicado construido por un mapeador, nunca una entidad. El inicio de sesión devuelve solo el token de acceso y la respuesta de una cuenta del personal nunca incluye el hash de la contraseña. Los cuerpos de error llevan un código y un mensaje fijo. Los campos personales (DNI, teléfono, dirección) se devuelven solo al personal con sesión, como parte del registro al que pertenecen. |
| 18 | Añadir encabezados de seguridad | Cumple | APIs (valores por defecto de Spring Security, comprobados en el entorno local y en producción): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache`, `Expires: 0`, `X-XSS-Protection: 0` y HSTS en producción. No envían CSP: solo devuelven JSON, PDF y CSV. El sitio: una CSP estricta (`default-src 'none'`, `script-src 'self'`, `connect-src` limitado a las cuatro APIs, `frame-ancestors 'none'`), `X-Content-Type-Options`, `Referrer-Policy: no-referrer` y una `Permissions-Policy` que apaga cámara, micrófono, geolocalización y pagos. |
| 19 | Forzar HTTPS | Cumple | Render termina el TLS: el HTTP plano responde `301` a HTTPS y las cuatro APIs envían `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` en producción. La URL de la base usa `sslmode=require`. |
| 20 | Analizar las dependencias | Cumple | Un bot abre cada semana solicitudes de actualización para Maven, las acciones del CI, las imágenes base de Docker y los paquetes npm de la consola, con las actualizaciones menores y de parche agrupadas. El trabajo de la consola en el CI falla ante un aviso conocido de severidad alta o crítica en las dependencias de ejecución (`npm audit --omit=dev --audit-level=high`). |

## Controles adicionales

- **Ningún secreto con valor por defecto.** `JWT_SECRET`, `DB_PASSWORD` y `RABBITMQ_PASSWORD` se leen sin valor de respaldo (el JWT exige la propiedad). Un servicio sin ellos falla al arrancar en vez de correr con un valor predecible.
- **CORS.** Una lista explícita de orígenes (`CORS_ALLOWED_ORIGINS`). Vacía, no permite ninguno; `*` se rechaza al arrancar.
- **CSRF.** No aplica: el token viaja en el encabezado `Authorization`, nunca en una cookie, así que una petición de otro sitio no puede llevarlo. Las APIs no tienen estado y desactivan la protección CSRF por eso. La CSP del sitio también fija `form-action 'none'`.
- **Manejo de errores.** Los errores son cuerpos `ProblemDetail` con un código y un mensaje fijo; ninguna traza ni texto de excepción llega al cliente. Cuando no se puede llegar a la base (caída, sin conexiones, tiempo agotado, o una conexión que se cerró justo antes de usarla) el manejador común responde `503` con el código `SERVICE_UNAVAILABLE`, un mensaje fijo y `Retry-After`, nunca un 500 crudo ni el mensaje del controlador, el servidor o el SQL; la línea de registro lleva solo la clase de la excepción. La salud agregada pasa a DOWN (503) para un monitor mientras la sonda de vida sigue en UP, para que la plataforma no reinicie una JVM sana por una caída del proveedor. Ver [Servicio caído o despertando](servicio-caido.md).
- **Registro de datos sensibles.** Ninguna contraseña, token ni secreto se registra, con una excepción deliberada: la contraseña aleatoria del primer administrador se registra una vez en WARN cuando la tabla de personal está vacía, porque no hay otro canal para entregarla. La consola no registra nada.
- **Almacenamiento y vida del token.** El JWT vive en el `sessionStorage` de la pestaña, vence a la hora y nunca se renueva. Ver [La sesión](sesion-jwt.md).

## Decisiones tomadas a propósito

Los dos roles, la política de contraseñas, la recuperación por código y la sesión sin revocación tienen su propia página: [Roles y cuentas](roles-y-cuentas.md), [Política de contraseñas](politica-contrasenas.md), [Recuperar la contraseña en dos pasos](recuperar-contrasena.md) y [La sesión](sesion-jwt.md). Lo que acepta el proyecto a propósito está reunido en [Limitaciones conocidas](limitaciones-conocidas.md).

### JWT en sessionStorage, con CSP

La consola guarda el token en `sessionStorage`: vive solo en esa pestaña y desaparece al cerrarla. El JavaScript puede leerlo, así que un XSS lo expondría. La alternativa, una cookie HttpOnly, exigiría las cuatro APIs en un mismo sitio (o cookies `SameSite=None`) más protección CSRF: un diseño distinto para un sitio estático que llama a cuatro orígenes. En cambio se reduce el riesgo con:

- una CSP que permite scripts solo del propio sitio y ninguno en línea;
- ningún punto de inyección de HTML en el código (comprobado en esta revisión);
- tokens que vencen a la hora y nunca se renuevan.

### Sin seguridad por fila

La seguridad por fila importa cuando el navegador habla directo con la base de datos, como con las bibliotecas cliente de Supabase. Aquí el navegador solo habla con los servicios, cada servicio llega a la base con sus propias credenciales y toda regla de acceso se aplica en el servicio. Hay un solo inquilino, así que no hay filas que ocultar de un usuario a otro.

### Sin cifrado por columna

Lo más sensible que se guarda son datos de contacto personales: nombres, DNI, teléfonos, direcciones de vecinos y correos del personal. Las contraseñas se guardan como hashes BCrypt, nunca en claro. El resto se protege con control de acceso y con TLS hacia la base, no con cifrado por columna. Eso exigiría una gestión de claves (dónde vive la clave, cómo rota) que una demostración en un plan gratuito no puede alojar bien, y una clave guardada junto a los datos aporta poco.

### Sin CAPTCHA

No hay registro público ni formularios públicos. Los endpoints públicos de negocio son el inicio de sesión del personal y las tres rutas de recuperación de contraseña. El inicio de sesión tiene límite por IP, responde igual a un correo equivocado y a una contraseña equivocada (`AUTH-001`) y se apoya en BCrypt; la recuperación tiene sus propios límites en la base de datos (por correo, por dirección y un tope diario, ver [Recuperar la contraseña en dos pasos](recuperar-contrasena.md)). Un CAPTCHA añadiría un script de terceros y su dominio a la CSP.

### Sin subidas

Ningún endpoint acepta un archivo y ninguna pantalla lo ofrece. Las cifras SIGERSOL se escriben a mano y los certificados los genera el servicio (solo descargas de PDF y CSV). No hay un tipo de archivo que comprobar, un tamaño que limitar ni un almacenamiento que proteger.
