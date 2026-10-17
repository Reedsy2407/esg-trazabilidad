# Operación y seguridad

Cómo se despliega, cómo se protege la base de datos al migrar y cómo se comprueba que todo sigue en pie.
## Despliegue: Render Blueprint

Los cuatro servicios y el sitio estático se describen en un solo archivo, `render.yaml` (un *Blueprint* de Render): cinco recursos en el plan gratuito, región Oregon. Cada servicio construye su imagen desde la raíz del código fuente y Render lo da por vivo cuando `GET /actuator/health/liveness` responde 200.

- **Solo se despliega con el CI en verde** (`autoDeployTrigger: checksPass`) y solo cuando cambia algo que el servicio usa (`buildFilter`): un cambio del frontend no reconstruye los servicios.
- **Ningún secreto con valor por defecto.** `JWT_SECRET`, `DB_PASSWORD`, `RABBITMQ_PASSWORD`, `PASSWORD_RESET_PEPPER` y las variables del correo se fijan en el panel de Render (`sync: false`); si faltan, el servicio **no arranca**, a propósito.
- **Verificación tras cada despliegue.** `scripts/verify-deploy.sh` compara el commit que sirve cada servicio (`/actuator/info`, y `version.json` en el frontend) con el esperado, y `scripts/smoke-prod.sh` recorre un flujo real contra producción y mide tiempos.

## Ramas de la base de datos por etapa

La base es PostgreSQL en **Neon** y los cuatro servicios comparten el esquema `public`; las migraciones son de **Liquibase** y corren al arrancar cada servicio. Antes de desplegar una etapa que trae migraciones se crea una **rama de Neon `pre-mN`** (una copia de producción con **fecha de expiración**), de modo que un error de migración se pueda reconstruir sin tocar los datos reales. Las ramas `pre-*` caducan solas y se borran cuando la etapa queda verificada.

## Secretos: gitleaks

El CI corre **gitleaks** sobre todo el historial en cada empujón. Un falso positivo conocido se permite **solo** con una entrada que fija el commit, el archivo y el texto exacto de la línea en `.gitleaks.toml`; nada se permite por regla, así que un hallazgo nuevo en cualquier otro lugar rompe el CI. Antes de empujar se corre gitleaks en local con la misma configuración.

## Pruebas

- **Pruebas de integración con PostgreSQL real** (Testcontainers) en cada servicio: la API completa, las migraciones, las restricciones de la base, la concurrencia y hasta la caída de la base de datos.
- **`e2e-tests`:** levanta los servicios como procesos reales y comprueba que un token de auth-service sirve en los demás.
- **Playwright** con un backend simulado: flujos completos, teclado, accesibilidad con AXE y 360, 768 y 1280 px.
- Frontend: lint, pruebas unitarias y construcción de producción antes de cada merge.

## Seguridad en resumen

- JWT propio (HS256, 1 hora, sin revocación: ver [La sesión](sesion-jwt.md)); ningún encabezado de «confianza» entre servicios.
- Contraseñas según NIST ([política](politica-contrasenas.md)), guardadas con BCrypt.
- CORS con lista explícita de orígenes; CSRF no aplica porque el token viaja en un encabezado, no en una cookie.
- Errores como `application/problem+json` con código fijo; sin trazas ni textos internos.
- Recuperación de contraseña con respuesta que no revela cuentas, límites en la base y aviso de cambio ([Recuperar la contraseña](recuperar-contrasena.md)).
- Frontend con CSP y el token en `sessionStorage`.

Lo que **no** se hace a propósito está en [Limitaciones conocidas](limitaciones-conocidas.md).
