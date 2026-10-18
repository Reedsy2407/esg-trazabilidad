# La decisión en una frase, en presente (ej.: Los certificados se anulan, no se borran)

- **Estado:** Aceptada, pendiente de implementación
- **Fecha:** AAAA-MM-DD
- **Dónde se aplica:** servicio y operación (ej.: reporting-service, anulación de certificados)
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Qué problema o restricción obligó a decidir. Datos concretos, no opiniones.

## Decisión

Qué se decidió, en lenguaje de negocio. Si toca un estado o un valor de la API, nómbralo
como en el glosario: el valor de la API y su etiqueta (`ANNULLED` = "Anulado").

## Consecuencias

Qué cambia para el personal, las empresas o los vecinos, y qué queda prohibido o
pendiente.

## Alternativas descartadas

- **Alternativa:** por qué no.

<!--
Cómo llenar esta plantilla (este comentario se borra):

1. Copia el archivo a portal/Writerside/topics/decisiones/AAAA-MM-DD-slug.md
   (fecha de la decisión + slug en minúsculas con guiones). No edites esg.tree ni
   decisiones.md: el árbol y el índice se generan en el build.
   Título: sin [ ] < > (rompen el enlace del índice o el XML de Writerside).
   Cada línea de metadatos (Estado, Fecha, Dónde se aplica, Reemplaza a,
   Reemplazada por) va una sola vez: para cambiarla, edita la existente.
2. Estado: empieza con uno de estos cuatro valores, tal cual. Después puede ir un
   detalle separado por punto, paréntesis o dos puntos
   (ej.: "Aceptada, pendiente de implementación. Se implementa en M5.").
     Aceptada, pendiente de implementación
     Implementada
     Descartada
     Reemplazada
3. Fecha: la misma del nombre del archivo.
4. Reemplaza a / Reemplazada por: "—", o un enlace al otro ADR,
   [título](AAAA-MM-DD-slug.md). Si el estado es Reemplazada, "Reemplazada por"
   es obligatorio.
5. Quien implementa una decisión cambia su estado a "Implementada" en el mismo
   commit que el código, y agrega en "Dónde se aplica" un enlace al código o a otro capítulo del portal.
6. Comprueba antes de commitear: node portal/scripts/gen-adr-toc.mjs --check
-->
