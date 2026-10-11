# ESG Trazabilidad

Este portal explica en lenguaje de negocio qué hace la plataforma %product%, qué
decisiones la sostienen y cómo se usa cada endpoint.

La plataforma registra el trabajo de los recicladores formalizados de Lima
Metropolitana y Callao: quién recoge, a qué vecino o empresa, cuándo y cuánto. Con
esos registros emite certificados ESG que una empresa puede presentar como evidencia
de su gestión de residuos.

## Cómo leer este portal

Visión del producto
: Para qué existe la plataforma y quién la usa.

Glosario de estados
: Qué significa cada estado (activo, suspendido, pausado…) y qué permite o bloquea.

Endpoints
: Qué hace cada operación, quién la usa, qué reglas aplica y qué errores devuelve.
  El contrato técnico (parámetros y esquemas) sale del OpenAPI de cada servicio.

Catálogo de errores
: Cada código (`AUTH-005`, `RPT-009`…) con su causa y qué hacer.

Decisiones
: El registro de decisiones (ADR): qué se decidió, por qué y si ya está en el código.

## Fuente de verdad

Cuando este portal y el código no coinciden, manda el código. El desfase se revisa
de forma automática: ver [el repositorio](%repo%).
