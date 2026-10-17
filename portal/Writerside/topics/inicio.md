# ESG Trazabilidad

Este portal explica en lenguaje de negocio qué hace la plataforma %product%, qué
decisiones la sostienen y cómo se usa cada endpoint.

La plataforma registra el trabajo de los recicladores formalizados de Lima
Metropolitana y Callao: quién recoge, a qué vecino, cuándo y cuánto. Con esos
registros emite certificados ESG que una empresa puede presentar como evidencia de
su gestión de residuos. Es un proyecto académico y de portafolio: los certificados
no tienen validez legal.

## Cómo leer este portal

Visión del producto
: Para qué existe la plataforma, los cuatro servicios, el camino de un kilo hasta el
  certificado y las reglas de negocio (empresas, cronogramas, recojos, SIGERSOL,
  cuentas, contraseñas, sesión, recuperación y servicio caído).
  Empieza por [Visión, servicios y flujo de punta a punta](vision-y-servicios.md).

Glosario de estados
: Qué significa cada estado (activo, suspendido, pausado, emitido, anulado…) y qué
  permite o bloquea. Ver el [glosario](glosario.md).

Endpoints
: Una página por operación: para qué sirve, quién puede llamarla, un ejemplo de
  petición y respuesta, sus reglas y sus errores. El contrato técnico sale del
  OpenAPI de cada servicio. Ver [Endpoints](endpoints.md).

Catálogo de errores
: Cada código (`AUTH-011`, `RPT-005`, `SERVICE_UNAVAILABLE`…) con su causa y qué hacer.
  Ver el [catálogo](errores.md).

Decisiones
: El registro de decisiones (ADR): qué se decidió, por qué y si ya está en el código.
  Ver [Decisiones](decisiones.md).

## Fuente de verdad

Cuando este portal y el código no coinciden, manda el código. El desfase se revisa
de forma automática: las especificaciones OpenAPI y el catálogo de errores se
comparan con el código en cada ejecución del flujo del portal (solo informan). Ver
[el repositorio](%repo%).
