# Despliegue paso a paso

Cómo poner los cuatro servicios (`recycler-service`, `collection-service`, `reporting-service` y `auth-service`) y el sitio en una URL pública desde cero, usando solo planes gratuitos:

- **Render** ejecuta los servicios: cuatro servicios web gratuitos, cada uno construido desde su `Dockerfile`, y un sitio estático para la consola.
- **Neon** es la base de datos: un PostgreSQL gratuito compartido por los cuatro servicios.
- **CloudAMQP** es el broker de mensajes: un RabbitMQ gratuito «Little Lemur».

Todo lo de abajo usa `<marcadores>`. **Nunca se escribe un host, usuario, contraseña, token o cadena de conexión reales en el código, en la configuración de despliegue ni en ningún documento.** Los valores reales van solo en el panel de Render.

Una vez montado, desplegar es automático: cada cambio que llega a la rama principal con el CI en verde lo despliega Render (`autoDeployTrigger: checksPass`), y un servicio solo se reconstruye cuando cambia su módulo, `shared-kernel`, el `pom.xml` raíz o el `.dockerignore`. El orden para empezar es: región, base de datos, broker, grupo de variables y, por último, aplicar el Blueprint (el paso a paso del código está en el README).

## 0. Elegir una sola región para todo

La región gratuita de Render más cercana a Lima está en Estados Unidos de todos modos. El Blueprint usa **`oregon`**. Neon y CloudAMQP se crean en la **misma región de AWS de EE. UU.**, para que cada ida y vuelta a la base o al broker quede dentro de una región: para Oregón, es AWS `us-west-2`.

Los tres proveedores nombran las regiones distinto. Al crear cada cuenta, **confirma en la lista de regiones de cada proveedor** que la elegida es de verdad la misma región de AWS. Para Oregón, Neon la lista como «AWS US West 2 (Oregon)» y CloudAMQP como «Amazon Web Services US-West-2 (Oregon)». Si cambia la región de Render, se cambia `region:` en los cuatro servicios del Blueprint y se eligen Neon y CloudAMQP a juego.

## 1. PostgreSQL en Neon

1. Crea un proyecto de Neon (plan gratuito) en la región del paso 0.
2. Usa la base que Neon crea por defecto, `neondb`; no hace falta crear otra. Anota su rol (usuario) y su contraseña.
3. Copia el host de conexión **directa**, **no** el agrupado (el que contiene `-pooler`). En el panel **Connect** de Neon, el interruptor **Connection pooling** viene **activado**: apágalo para ver el host directo. El bloqueo de Liquibase y el uso de JDBC por sesión de los servicios no deben ir detrás de un agrupador en modo transacción.
4. Arma la URL JDBC. Neon exige TLS:

   ```
   jdbc:postgresql://<host-directo-de-neon>/<base>?sslmode=require
   ```

   `<base>` es `neondb`, salvo que hayas creado otra.

No hace falta ningún SQL manual: el changelog de Liquibase de cada servicio crea sus propias tablas en el primer arranque, incluido el `CREATE EXTENSION IF NOT EXISTS btree_gist` de `reporting-service`.

## 2. RabbitMQ en CloudAMQP

1. Una cuenta nueva de CloudAMQP debe crear primero un **Team**: un nombre, aceptar los términos del servicio y responder una pregunta de GDPR. Solo entonces puede crear una instancia. Crea una en el plan gratuito **Little Lemur**, en la región del paso 0.
2. En los detalles de la instancia anota el host, el usuario, la contraseña y el **vhost**. En los planes compartidos el vhost suele ser igual al usuario.
3. Los servicios se conectan por TLS (`amqps`) al puerto **5671**.

Solo `recycler-service`, `collection-service` y `reporting-service` usan el broker; `auth-service` no.

## 3. El grupo de variables `esg-shared`

1. Crea una cuenta de Render y conéctala al proveedor donde está alojado el código.
2. En el panel, crea un **Environment Group** llamado exactamente **`esg-shared`**. Créalo **a mano, no en el Blueprint**: Render ignora `sync: false` dentro de un grupo declarado en un Blueprint, así que los secretos quedarían expuestos a las sincronizaciones del Blueprint. Los cuatro servicios lo referencian con `fromGroup: esg-shared`, de modo que cada valor se escribe una sola vez. Eso también significa que `JWT_SECRET` es un único valor, idéntico en todos los servicios.

| Clave | Valor |
|---|---|
| `JWT_SECRET` | Un secreto aleatorio nuevo de **al menos 32 bytes**, por ejemplo la salida de `openssl rand -base64 48`. auth-service firma los tokens con él y los otros tres los validan con él. Nunca reutilices el valor local. |
| `DB_URL` | `jdbc:postgresql://<host-directo-de-neon>/<base>?sslmode=require` |
| `DB_USERNAME` | `<rol-de-neon>` |
| `DB_PASSWORD` | `<contraseña-de-neon>` |
| `RABBITMQ_HOST` | `<host-de-cloudamqp>` |
| `RABBITMQ_PORT` | `5671` |
| `RABBITMQ_USER` | `<usuario-de-cloudamqp>` |
| `RABBITMQ_PASSWORD` | `<contraseña-de-cloudamqp>` |
| `RABBITMQ_VHOST` | `<vhost-de-cloudamqp>` |
| `RABBITMQ_SSL_ENABLED` | `true` |

Consejo: la pantalla de creación del grupo tiene un botón **Add from .env** que acepta las diez claves pegadas de una vez como líneas `CLAVE=VALOR`, más rápido que escribirlas una a una. Pega directo desde un gestor de contraseñas y no guardes ese texto en un archivo del proyecto.

`CORS_ALLOWED_ORIGINS` es opcional y puede añadirse después. Lista los orígenes de navegador que pueden llamar a las APIs, separados por comas, por ejemplo el `https://<host-del-sitio>` de la consola. Sin definir, no se permite ningún origen. `*` hace que todos los servicios fallen al arrancar, a propósito.

**Solo reporting-service:** pide a SUNAT, a través de Decolecta, la razón social de cada empresa. Hay variables propias, que se fijan en ese servicio en el panel (no están en `esg-shared`) y no tienen valor por defecto: sin ellas, reporting-service no arranca.

| Clave | Valor |
|---|---|
| `RUC_LOOKUP_PROVIDER` | `decolecta`. El otro valor aceptado, `none` (toda consulta «no responde»), es solo para desarrollo y pruebas: el servicio se niega a arrancar con él cuando `RENDER=true`, que Render fija por sí mismo. |
| `SIGERSOL_MUNICIPAL_PROVIDER` | `sigersol` en Render (declarado en el Blueprint); `none` hace que el contexto SIGERSOL de cada distrito diga «no disponible». No tiene valor por defecto. Nada depende de SIGERSOL: una caída solo muestra «no disponible». |
| `SIGERSOL_MUNICIPAL_BASE_URL` | Obligatoria cuando el proveedor es `sigersol`: `https://sistemas.minam.gob.pe/srv-sigersol/rest/` (pública, sin credenciales). |
| `RUC_LOOKUP_TOKEN` | El token de la API de Decolecta (también se lee `DECOLECTA_TOKEN`). Plan gratuito: 1.000 consultas al mes; las respuestas se guardan 24 horas en la tabla `ruc_lookup_cache`. Nunca lo pegues en un chat, un commit ni un registro. |

**Solo auth-service (recuperación de contraseña):** «¿Olvidaste tu contraseña?» envía un código de 8 dígitos por correo mediante una aplicación web de Google Apps Script que envía desde la cuenta de Gmail del proyecto (el SMTP de Gmail está cerrado en el plan gratuito de Render y el script sale por HTTPS; ver [Correo por HTTPS con Google Apps Script](correo-apps-script.md)). Son cinco variables, fijadas en ese servicio. Ninguna tiene valor por defecto, así que **sin ellas auth-service no arranca**: defínelas en el panel **antes** del despliegue.

| Clave | Valor |
|---|---|
| `EMAIL_PROVIDER` | `appsscript` (ya en el Blueprint). `fake` y `none` son solo para desarrollo y pruebas: el servicio los rechaza con `RENDER=true` mientras `PASSWORD_RESET_EMAIL_ENABLED=true`. |
| `PASSWORD_RESET_EMAIL_ENABLED` | `true` (ya en el Blueprint). El interruptor de emergencia: `false` no envía ningún correo (ni códigos ni avisos) y el servicio sigue funcionando. |
| `EMAIL_WEBHOOK_URL` | La URL de la aplicación web del script, terminada en `/exec`. Debe empezar con `https://script.google.com/macros/s/`, o el servicio no arranca. Se escribe una vez en el panel (`sync: false`). |
| `EMAIL_WEBHOOK_SECRET` | El secreto compartido con el script, de **al menos 32 caracteres**; el mismo valor que la propiedad `WEBHOOK_SECRET` del script. Para rotarlo se cambian ambos a la vez. Nunca en un archivo, un chat ni un registro. |
| `PASSWORD_RESET_PEPPER` | La clave del HMAC que protege los códigos guardados en la base, de **al menos 32 bytes** de texto aleatorio (`openssl rand -base64 48`). Un valor distinto de todo otro secreto. Cambiarla invalida los códigos vivos en ese momento (duran 10 minutos). |

El script (solo envía dos plantillas fijas, cada una como HTML con estilo y alternativa en texto plano y un logo en línea, usando únicamente `MailApp`) vive en el Apps Script de la cuenta de Gmail, publicado como aplicación web «Ejecutar como: yo», «Quién tiene acceso: cualquiera»; el secreto compartido se guarda en las propiedades del script, no en su código. Tras cambiar el código del script, crea una **nueva versión del mismo despliegue** para que la URL no cambie. La cuota de Google para una cuenta de consumo es de unos 100 destinatarios al día: el servicio deja de enviar a los **80 al día** (códigos y avisos juntos) y sigue respondiendo igual.

**Comprobación manual tras el primer despliegue (una vez, por una persona):** desde el sitio en vivo pide un código a tu propio correo, a una bandeja de Gmail y a una de otro proveedor. Verifica que el correo llega (y no a spam), que se ve la versión con estilo (logo y código grande en monoespaciado; también en el modo oscuro de Gmail y en un teléfono), que el remitente dice «Trazabilidad ESG», que llega **un** solo correo por solicitud y que `MailApp.getRemainingDailyQuota()` baja. El servicio registra `event=email_sent provider=appsscript durationMs=N` o `event=email_failed ... reason=<código>`, nunca el destinatario, el código ni la URL del script.

`PORT` **no** se define: Render lo inyecta y cada servicio lee `server.port: ${PORT:808x}`.

Un `JWT_SECRET` o un `DB_PASSWORD` ausentes hacen que cualquier servicio falle al arrancar, a propósito, porque ninguno tiene valor por defecto en ningún sitio. Un `RABBITMQ_PASSWORD` ausente hace lo mismo con los tres servicios que usan AMQP; auth-service no lo lee.

## 4. Primer despliegue

Al aplicar el Blueprint (ver el README), Render previsualiza cuatro servicios web: `esg-recycler-service`, `esg-collection-service`, `esg-reporting-service` y `esg-auth-service`. La vista previa es también la validación final del Blueprint. Cuando pide **`ADMIN_BOOTSTRAP_EMAIL`** (solo auth-service, `sync: false`), se escribe el correo de la primera cuenta del personal: se escribe una vez aquí y las sincronizaciones posteriores no lo tocan. Cada servicio construye su imagen desde la raíz del código (`dockerContext: .`) y arranca; Render lo da por vivo cuando `GET /actuator/health/liveness` responde 200.

**En el primer despliegue puede fallar un servicio en su paso de Liquibase.** Los cuatro servicios comparten el esquema `public` de Neon y, con él, las tablas `databasechangelog` y `databasechangeloglock` de Liquibase. La tabla de bloqueo (y su única fila) tiene que crearse **antes** de que cualquier servicio pueda tomar el bloqueo. Cuando varios servicios arrancan a la vez contra un esquema vacío, pueden competir por crearla y el perdedor no arranca. La solución es un **Manual Deploy** de ese servicio: las tablas ya existen, así que espera el bloqueo como los demás. Solo puede pasar con el esquema vacío, así que reaparece únicamente con una base de Neon nueva o reiniciada.

Por qué sonda de vida y no `/actuator/health`: cuando RabbitMQ o PostgreSQL están caídos, la salud agregada devuelve 503 y un reinicio no arregla una caída del proveedor. La sonda de vida solo pregunta si la JVM está sana. El `/actuator/health` agregado sigue público para diagnóstico.

## 5. La contraseña inicial

En su primer arranque con la tabla `staff_user` vacía, auth-service crea la cuenta de `ADMIN_BOOTSTRAP_EMAIL` con una **contraseña aleatoria que registra una sola vez en nivel WARN**, en una línea con la forma `email=<correo> password=<contraseña>`. Léela en **esg-auth-service → Logs** justo después del primer despliegue y guárdala **de inmediato** en un gestor de contraseñas. La ventana es estrecha: en la puesta en marcha real, la línea ya no estaba en los registros tras unos reinicios y redespliegues y hubo que restablecer la cuenta (abajo).

No se vuelve a registrar. La cuenta se crea como **ADMIN** con un cambio de contraseña pendiente (las pantallas envían a la persona a elegir la suya, de 15 a 64 caracteres), y `ADMIN_BOOTSTRAP_EMAIL` tiene un segundo comportamiento: cuando la tabla ya tiene cuentas pero **ningún ADMIN activo**, la cuenta que nombra esa variable (debe existir y estar activa) se asciende a ADMIN al arrancar y no se toca nada más. Con un ADMIN activo, un reinicio no hace nada. Un administrador puede restablecer la contraseña de cualquier cuenta desde la aplicación (incluida la de otro administrador) y cualquiera puede cambiar la propia. Quien olvidó la suya la recupera con un código enviado al correo de la cuenta («¿Olvidaste tu contraseña?» en el inicio de sesión; las variables están en la sección 3). Si se pierde la contraseña del único administrador, el último recurso es borrar todas las filas de `staff_user` en el editor SQL de Neon y reiniciar esg-auth-service, que registra una contraseña nueva una vez (esto también elimina todas las demás cuentas del personal).

## 6. Comprobación del despliegue

Contra las URL públicas, `https://<servicio>.onrender.com`. Los comandos son para **bash** (el `read -s` del punto 3 no es POSIX `sh`). Define primero esto, solo en tu propia terminal:

```bash
AUTH=https://<esg-auth-service>.onrender.com
RECYCLER=https://<esg-recycler-service>.onrender.com
COLLECTION=https://<esg-collection-service>.onrender.com
REPORTING=https://<esg-reporting-service>.onrender.com
```

La primera llamada a un servicio dormido espera su arranque en frío (ver [Servicio caído o despertando](servicio-caido.md)); eso no es una falla.

1. **Los endpoints públicos de los cuatro responden sin token.** La sonda de vida y Swagger UI deben dar 200:

   ```bash
   for u in $AUTH $RECYCLER $COLLECTION $REPORTING; do
     curl -s -o /dev/null -w "$u liveness=%{http_code} " $u/actuator/health/liveness
     curl -s -o /dev/null -w "swagger=%{http_code}\n" $u/swagger-ui/index.html
   done
   ```

2. **Los endpoints de negocio rechazan una petición sin token.** Se espera `401` con `"code":"AUTH-000"`:

   ```bash
   curl -s -w " %{http_code}\n" $RECYCLER/associations
   curl -s -w " %{http_code}\n" $COLLECTION/companies
   curl -s -w " %{http_code}\n" $REPORTING/tracked-companies
   ```

3. **Inicia sesión con la cuenta inicial.** Escribe la contraseña en el aviso, para que no quede en el historial del shell:

   ```bash
   read -rs -p "contraseña inicial: " PW; echo
   TOKEN=$(curl -s -H 'Content-Type: application/json' \
     -d "{\"email\":\"<correo-inicial>\",\"password\":\"$PW\"}" $AUTH/auth/login \
     | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'); unset PW
   ```

4. **El mismo token sirve, sin modificar, en los otros tres servicios.** Se espera 200 en cada uno:

   ```bash
   for u in $RECYCLER/associations $COLLECTION/companies $REPORTING/tracked-companies; do
     curl -s -o /dev/null -w "$u %{http_code}\n" -H "Authorization: Bearer $TOKEN" $u
   done
   ```

5. **Un evento viaja por CloudAMQP.** Un recojo registrado en collection-service aumenta los kilos de la asociación en recycler-service:

   ```bash
   H="Authorization: Bearer $TOKEN"
   ASSOC=$(curl -s -H "$H" -H 'Content-Type: application/json' \
     -d '{"name":"Asociacion Smoke","ruc":"<ruc-de-11-digitos>"}' $RECYCLER/associations \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
   NEIGHBOR=$(curl -s -H "$H" -H 'Content-Type: application/json' \
     -d '{"fullName":"Vecino Smoke","address":"<direccion>"}' $COLLECTION/neighbors \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
   curl -s -H "$H" -H 'Content-Type: application/json' \
     -d "{\"associationId\":\"$ASSOC\",\"collectionDate\":\"<aaaa-mm-dd>\",\"weightKg\":12.5}" \
     $COLLECTION/neighbors/$NEIGHBOR/collection-records
   echo "$ASSOC"
   ```

   Revisa el resultado en el **editor SQL de Neon**: el total acumulado de la asociación no forma parte de ninguna respuesta HTTP (`AssociationResponse` no lo expone). En pocos segundos (el despachador del outbox consulta cada 5 s por defecto) sube en 12,5:

   ```sql
   select total_kilos_collected from association where id = '<id-de-la-asociacion>';
   ```

6. **El inicio de sesión tiene límite de intentos.** El límite es de 5 intentos por minuto por IP de cliente y cuenta **todos** los intentos, incluido el inicio de sesión exitoso del punto 3. Las fichas vuelven poco a poco, una cada 12 s aproximadamente, no todas a la vez tras un minuto. Por eso **espera al menos un minuto tras el punto 3** y luego haz siete inicios de sesión rápidos con contraseña equivocada desde una misma máquina. Se espera `401 AUTH-001` en los cinco primeros y `429 AUTH-004` con un encabezado `Retry-After` desde el sexto aproximadamente. En una instancia fría y lenta puede reponerse una ficha a mitad del bucle, lo que mueve el primer 429 un intento.

   ```bash
   for i in 1 2 3 4 5 6 7; do
     curl -s -D /tmp/h -o /tmp/b -H 'Content-Type: application/json' \
       -d '{"email":"<correo-inicial>","password":"wrong"}' $AUTH/auth/login
     echo "$i: $(head -1 /tmp/h | tr -d '\r') $(grep -o '"code":"[^"]*"' /tmp/b) $(grep -i '^retry-after' /tmp/h | tr -d '\r')"
   done
   ```

Qué commit sirve cada servicio se lee sin credenciales: `GET /actuator/info` devuelve `{"commit": "<sha>"}` (del `RENDER_GIT_COMMIT` de Render) y el sitio estático lo hace en `GET /version.json`. Tras un despliegue, compáralo con el commit esperado: un servicio al que el último cambio no tocó no se redespliega.

**Un CI en rojo en un commit, seguido de otro commit que no toca los servicios, deja los servicios sin desplegar.** Render despliega un servicio solo cuando el CI pasa en un commit que toca sus rutas (`buildFilter`). Si el CI de ese commit falla y el arreglo toca solo la consola, los cuatro servicios siguen con su imagen anterior. La salida es un **Manual Deploy → Deploy latest commit** de cada uno. Algo parecido pasa con **un empujón de varios commits**: Render parece decidir el despliegue automático por el último commit del empujón, así que si este no toca las rutas de un servicio, puede no redesplegarse aunque commits anteriores del mismo empujón sí lo cambiaran. Para evitarlo, deja un commit que toque el servicio como el último del empujón, o empújalo solo.

## 7. La consola (sitio estático) y CORS

El Blueprint declara un quinto recurso, **`esg-frontend`**, un sitio estático construido desde la carpeta de la consola (`npm ci && npm run build`, publicado desde `dist/esg-frontend/browser`). No tiene secretos ni grupo de variables. Se despliega solo con el CI en verde (`autoDeployTrigger: checksPass`) y solo cuando cambia su carpeta. Su construcción escribe `/version.json` (`{"commit": "<sha>"}`), así que se comprueba igual que las APIs.

El navegador llama a las cuatro APIs directamente, entre orígenes distintos, así que estas deben permitir el origen del sitio:

1. **Crear el sitio.** El Blueprint lo crea en su siguiente sincronización: automática si el Auto Sync del Blueprint está activado y, si no, **Blueprints → esg-trazabilidad → Manual Sync**. La vista previa debe mostrar exactamente un recurso nuevo, `esg-frontend` (sitio estático), y ningún cambio en los cuatro servicios.
2. **Leer su URL** en **esg-frontend → Settings**. En esta instalación es `https://esg-frontend-egcf.onrender.com`: el nombre simple estaba tomado y Render añadió el sufijo `-egcf`. Si el sitio se recrea con otro sufijo, hay que actualizar el origen de abajo.
3. **Permitir ese origen.** En **Environment Groups → esg-shared**, añade `CORS_ALLOWED_ORIGINS` = `https://esg-frontend-egcf.onrender.com` (solo esquema y host: sin ruta, sin barra final y nunca `*`, que falla al arrancar a propósito). Guardar el grupo redespliega los cuatro servicios una vez.
4. **Verificar.** Guardar el grupo redespliega los servicios *en el mismo commit*, así que comparar commits no distingue las instancias viejas de las nuevas. Primero espera a que los cuatro muestren **Deploy live** para ese redespliegue y luego:
   - CORS, una verificación previa por servicio (cada una debe imprimir `access-control-allow-origin: https://esg-frontend-egcf.onrender.com`):
     ```bash
     for s in auth recycler collection reporting; do
       echo "$s: $(curl -si -X OPTIONS https://esg-$s-service.onrender.com/actuator/health \
         -H 'Origin: https://esg-frontend-egcf.onrender.com' -H 'Access-Control-Request-Method: GET' \
         -H 'Access-Control-Request-Headers: authorization' | grep -i '^access-control-allow-origin' | tr -d '\r')"
     done
     ```
   - Los encabezados del sitio en la raíz, en un enlace profundo y en un paquete (cada uno debe mostrar `content-security-policy` y `cache-control: no-cache`):
     ```bash
     F=https://esg-frontend-egcf.onrender.com
     for p in / /recojos/nuevo "/$(curl -s $F/ | grep -o 'main-[A-Z0-9]*\.js' | head -1)"; do
       echo "== $p"; curl -sI "$F$p" | grep -iE '^(HTTP|content-security-policy|cache-control|content-type)'
     done
     ```
   - En el navegador: abre el sitio, espera a que termine «Preparando el sistema», inicia sesión, abre una empresa y un certificado, descarga su PDF y registra un recojo.

Los encabezados de respuesta del sitio incluyen una Content-Security-Policy: scripts solo del propio sitio, llamadas a las APIs solo a los cuatro servicios y ningún marco. La incrustación de CSS crítico está apagada en la configuración de Angular porque su manejador `onload=` exigiría scripts en línea. Un host de API nuevo debe añadirse a `connect-src` antes de que la consola pueda llamarlo.

`Strict-Transport-Security` no se declara en el sitio porque Render ya lo envía en `*.onrender.com`: comprobado el 2026-10-09 (Lima) con `curl -sI https://esg-frontend-egcf.onrender.com/`, que devuelve `strict-transport-security: max-age=315360000; includeSubdomains; preload` (el valor de la plataforma, más fuerte que el de un año que fijaríamos). Declarar uno propio lo duplicaría.

## Límites de los planes gratuitos

- **Apagado y arranque en frío.** Un servicio gratuito de Render duerme tras 15 minutos sin HTTP de entrada. La siguiente petición espera un contenedor completo y el arranque de Spring Boot a 0,1 CPU. **Medido en Render (2026-09-28): de 95 a 126 s** para esa primera petición. El proxy de Render mantiene la conexión abierta todo ese tiempo y luego responde 200; no vence por tiempo. Con el servicio despierto, `/actuator/health` responde en 0,27 a 0,57 s. Class Data Sharing (CDS) o AOT podrían acortar el arranque, pero probablemente no por debajo de un minuto; queda como mejora sin prioridad.
- **Mientras un servicio duerme, sus tareas de fondo no corren:** el despachador del outbox y el análisis de vencimientos de certificaciones de recycler-service. Los eventos ya publicados esperan en las colas duraderas de CloudAMQP hasta que el consumidor despierta, y una fila de outbox escrita justo antes de dormir se despacha cuando el servicio vuelva a despertar. Es consistencia eventual, no pérdida de datos.
- **750 horas de instancia gratuitas por espacio de trabajo al mes**, compartidas por los cuatro servicios. Un servicio dormido no gasta ninguna.
- **CloudAMQP borra una cola de la que nadie consume en 28 días.** Los servicios vuelven a declarar sus colas en el siguiente arranque, pero los mensajes que estaban en una cola borrada se pierden. Aceptable para una demostración; revisar antes de llevar datos reales.
- **Una sola instancia de auth-service.** El límite de intentos de inicio de sesión está en memoria y se reinicia cuando esa instancia se reinicia o duerme.
- **Swagger UI es público a propósito.** Es la puerta de entrada de la demostración y solo documenta la API; toda llamada de negocio detrás de él sigue necesitando un token.

Más límites (SIGERSOL, sesiones, CSV, demostración, gasto cero) están en [Limitaciones conocidas](limitaciones-conocidas.md).

## Los datos de la demostración

Los datos de la demostración se reconstruyeron el 2026-10-16 con RUC públicos reales: la empresa es el MINAM (RUC 20492966658, nombre tomado de SUNAT, «MINISTERIO DEL AMBIENTE»), su asociación es «Demo Sur» (RUC 20521286769, OEFA) y el certificado emitido cubre del 17 al 30 de setiembre de 2026 con **35,30 kg** trazados y **85,5 %** de cumplimiento de jerarquía declarado. Los certificados y empresas de prueba anteriores se anularon o desactivaron, no se borraron. La tabla de distritos de ese certificado dice «Distrito desconocido» (ver [Limitaciones conocidas](limitaciones-conocidas.md)).
