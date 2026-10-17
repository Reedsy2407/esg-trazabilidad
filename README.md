# Trazabilidad ESG para recicladores de Lima

Plataforma web que lleva la cuenta de cada kilo de residuo que recogen los recicladores formalizados de Lima Metropolitana y la convierte en un **certificado de trazabilidad ESG** que una empresa puede mostrar. Proyecto académico y de portafolio (CIBERTEC): los certificados llevan el pie «Proyecto académico, sin validez legal».

- **Sitio:** <https://esg-frontend-egcf.onrender.com> (los servicios están en el plan gratuito de Render: la primera visita puede tardar hasta 2 minutos mientras despiertan; la pantalla «Preparando el sistema» lo explica).
- **Portal de documentación:** <https://reedsy2407.github.io/esg-trazabilidad/> (cada endpoint en lenguaje de negocio, glosario de estados, catálogo de errores, decisiones de diseño y diagramas).

## El problema y para quién es

Una empresa que quiere decir «reciclamos» necesita poder probarlo con datos: cuántos kilos, de qué asociación de recicladores y en qué período. Los recicladores trabajan casa por casa y casi nunca dejan un rastro que se pueda auditar.

Esta plataforma es la consola del **personal de una operadora** (rol STAFF) y de su **administrador** (rol ADMIN):

1. Registra a los **vecinos** que entregan residuos y su **cronograma** de recojo.
2. Registra cada **recojo** (kilos, fecha, asociación). Los recojos no se editan ni se borran: son un libro de solo agregar.
3. Registra las **empresas** (verificadas contra SUNAT por su RUC), cada una vinculada a una asociación, y el **cumplimiento de jerarquía** que declara el operador de la asociación (SIGERSOL).
4. **Emite el certificado** de una empresa para un período: kilos trazados (los recojos de su asociación en ese período), cumplimiento declarado y contexto del distrito, en pantalla, PDF y CSV. Un certificado nunca se borra: si hay un error se **anula** con motivo y se emite el correcto.

> El cumplimiento de jerarquía es una cifra **declarada** por el operador; esta aplicación no se conecta con SIGERSOL ni la verifica (una integración real exigiría un convenio con el MINAM), y no es una cifra oficial. Los límites completos están en [docs/known-limitations.md](docs/known-limitations.md).

## Capturas

| | |
|---|---|
| ![Lista de empresas](docs/img/01-empresas.jpg) | ![Empresa con resumen del período](docs/img/02-empresa.jpg) |
| Empresas, con su estado | Una empresa: resumen del período y kilos por período |
| ![Certificado de trazabilidad](docs/img/03-certificado.jpg) | ![Registrar recojo](docs/img/04-registrar-recojo.jpg) |
| Certificado con PDF y CSV | Registrar un recojo |
| ![Registrar dato SIGERSOL](docs/img/05-sigersol-registro.jpg) | |
| Registrar el dato declarado de SIGERSOL | |

Datos de demostración: las empresas y vecinos de las capturas son ficticios o entidades públicas usadas como ejemplo, y la cuenta de personal aparece enmascarada.

## Arquitectura

```mermaid
flowchart LR
    U[Personal y administrador] --> F[Frontend Angular 21]
    F -->|JWT| A[auth-service]
    F -->|JWT| R[recycler-service]
    F -->|JWT| C[collection-service]
    F -->|JWT| P[reporting-service]
    A --- DB[(Neon PostgreSQL)]
    R --- DB
    C --- DB
    P --- DB
    C -- CollectionRegistered --> MQ{{RabbitMQ}}
    MQ --> R
    MQ --> P
    R -- CertificationExpired / Renewed --> MQ
    MQ --> C
    P -. consulta del RUC .-> S[SUNAT vía proveedor]
    A -. correo por HTTPS .-> G[Google Apps Script]
```

| Servicio | Qué guarda y qué hace |
|---|---|
| `auth-service` | Cuentas del personal, roles, inicio de sesión, emisión del JWT y recuperación de contraseña por correo. |
| `recycler-service` | Asociaciones de recicladores, recicladores y sus certificaciones (con vencimiento). |
| `collection-service` | Vecinos, cronogramas y recojos (libro append-only). |
| `reporting-service` | Empresas, certificados ESG (PDF y CSV), registros SIGERSOL declarados y contexto distrital. |
| `shared-kernel` | Errores tipados, identificadores UUID v7, outbox/inbox, seguridad JWT común. |
| `frontend-demo` | Consola Angular (componentes standalone, señales, rutas perezosas, CSS propio). |

**Decisiones de diseño que importan**

- **Hexagonal por servicio:** cada servicio separa `adapter/in/web` (controladores), `port/in` y `port/out` (puertos), `service` (casos de uso) y `adapter/out` (persistencia, correo, SUNAT). El dominio no conoce Spring ni la base de datos, por eso un proveedor (SUNAT, correo) se cambia sin tocar las reglas.
- **JWT sin estado:** `auth-service` firma (HS256) y los otros tres servicios validan **sin llamar** a auth, porque en el plan gratuito los servicios se duermen. Costo aceptado: un token ya emitido vale hasta que vence (1 hora).
- **Eventos con outbox:** el recojo y las certificaciones se comunican por RabbitMQ con patrón outbox/inbox, para no perder ni duplicar mensajes.
- **Gasto cero:** Render (gratis), Neon (gratis), CloudAMQP (gratis), GitHub Pages para el portal y Google Apps Script para el correo.

## Stack

Java 21 · Spring Boot 3.3 · Spring Security (JWT propio) · PostgreSQL 16 (Neon) · Liquibase · RabbitMQ · Angular 21 · Docker Compose (local) · Testcontainers · Playwright · GitHub Actions · Render · Writerside (portal).

## Cómo correrlo en local

Requisitos: JDK 21, Maven, Node 22, y un runtime de contenedores (Docker o Rancher Desktop en modo `dockerd`).

```bash
# 1. Variables locales (el archivo .env.local nunca se sube al repositorio)
cp .env.local.example .env.local      # llena POSTGRES_PASSWORD, DB_PASSWORD, RABBITMQ_PASSWORD,
                                      # JWT_SECRET (>= 32 bytes) y PASSWORD_RESET_PEPPER (>= 32 bytes)

# 2. PostgreSQL y RabbitMQ
docker compose --env-file .env.local up -d

# 3. Instala el módulo compartido una vez y arranca los cuatro servicios, cada uno en su terminal
mvn -DskipTests install
mvn -pl auth-service       spring-boot:run        # puerto 8084
mvn -pl recycler-service   spring-boot:run        # puerto 8081
mvn -pl collection-service spring-boot:run        # puerto 8082
mvn -pl reporting-service  spring-boot:run        # puerto 8083

# 4. El frontend (por defecto apunta a los servicios públicos; ver frontend-demo/README.md)
cd frontend-demo && npm ci && npm start           # http://localhost:5200
```

Ningún secreto tiene valor por defecto: sin las variables, el servicio **no arranca**. En el primer arranque con la tabla de cuentas vacía, `auth-service` crea la cuenta de `ADMIN_BOOTSTRAP_EMAIL` con una contraseña aleatoria que escribe una sola vez en el log.

## Cómo se prueba

- **Unitarias** en cada módulo (JUnit 5, Mockito) y en el frontend (Vitest).
- **Pruebas de integración con PostgreSQL real** (Testcontainers) para cada servicio: la API completa, migraciones, restricciones de la base, concurrencia (dos peticiones a la vez) y la caída de la base de datos.
- **`e2e-tests`:** levanta los servicios como procesos reales y comprueba que un token de `auth-service` sirve en los demás.
- **Playwright** con un backend simulado en el frontend: flujos completos, teclado, accesibilidad con AXE y 360/768/1280 px.
- **CI** (GitHub Actions): compilación y pruebas de todos los módulos, lint, pruebas y build del frontend, Playwright, `npm audit` y búsqueda de secretos con gitleaks.

```bash
mvn verify                                  # todos los módulos (unitarias + integración)
cd frontend-demo && npx ng lint && npm test -- --watch=false && npx ng build
cd frontend-demo && npx playwright test     # flujos del navegador
```

## Seguridad en 5 líneas

1. Ningún secreto en el repositorio ni con valor por defecto; gitleaks revisa todo el historial en cada push.
2. Contraseñas de 15 a 64 caracteres (NIST 800-63B), con lista de contraseñas comunes bloqueadas; guardadas con BCrypt.
3. Dos roles (ADMIN y STAFF); la autoridad de ADMIN se comprueba en la base de datos, no en el token.
4. «Olvidé mi contraseña» en dos pasos: código de 8 dígitos (10 minutos, 5 intentos) y luego un token de un solo uso; ambos guardados como HMAC, con respuesta que no revela si la cuenta existe.
5. Errores tipados sin trazas ni datos internos; CORS con lista explícita; el frontend guarda el token solo en `sessionStorage`, con CSP.

Detalle y limitaciones aceptadas (por ejemplo, un JWT no se puede revocar antes de vencer) en [docs/security.md](docs/security.md).

## Documentación

Los documentos de `docs/` están en inglés; el portal, en español.

- [docs/security.md](docs/security.md): controles y decisiones de seguridad.
- [docs/deployment.md](docs/deployment.md): despliegue en Render, variables y verificación.
- [docs/frontend.md](docs/frontend.md): qué pantalla usa cada endpoint y las reglas que viven solo en el frontend.
- [docs/known-limitations.md](docs/known-limitations.md): **límites conocidos** (SIGERSOL, sesiones, correo, CSV, servicios que se duermen).
- [docs/performance.md](docs/performance.md) y [docs/render-logs.md](docs/render-logs.md): mediciones y lectura de logs de producción.
- [portal/README.md](portal/README.md): cómo se construye el portal.
