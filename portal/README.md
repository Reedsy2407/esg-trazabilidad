# Portal de documentación

Sitio estático generado con [JetBrains Writerside](https://www.jetbrains.com/writerside/)
a partir de `portal/Writerside/`. Explica las decisiones de negocio y los endpoints de
esg-trazabilidad en lenguaje de negocio. Publicado en
<https://reedsy2407.github.io/esg-trazabilidad/>. El plan y las decisiones de diseño del
portal están en [PLAN.md](PLAN.md).

## Construir en local

Con Rancher Desktop en marcha (`docker info` debe responder) y Node instalado:

```bash
bash portal/scripts/build-local.sh
```

El script copia `Writerside/` a `portal/.build/`, genera ahí el árbol y el índice de
decisiones (ver más abajo) y construye esa copia. El sitio queda en
`portal/artifacts/webHelpESG2-all.zip`, y los errores del builder en
`portal/artifacts/report.json`. El job `build` de `.github/workflows/portal.yml` corre
este mismo script, así que la versión del builder y sus argumentos están en un solo
lugar.

La vista previa del plugin de Writerside en IntelliJ IDEA lee las fuentes sin generar
nada: ahí la sección Decisiones aparece vacía. Para verla completa, usa el script.

## Reglas para editar

- **Los nombres de topic son únicos en todo el proyecto**, aunque estén en carpetas
  distintas. Usa un prefijo: `errores-auth.md`, `estados-auth.md`.
- Todo topic nuevo (salvo los ADR) debe estar en `Writerside/esg.tree`; si no, el build
  lo reporta.
- Estados y valores de la API: se escribe siempre el valor de la API, tal cual, y su
  etiqueta en español: `ANNULLED` = "Anulado", `UNVERIFIED` = "No verificada".

## Agregar una decisión (ADR)

Un archivo por decisión, sin tocar ningún archivo compartido, así varias terminales
agregan decisiones a la vez sin conflictos de git.

1. Copia [plantillas/adr.md](plantillas/adr.md) a
   `Writerside/topics/decisiones/AAAA-MM-DD-slug.md` (fecha de la decisión, slug en
   minúsculas con guiones) y llénala. La plantilla explica cada campo.
2. **No edites `esg.tree` ni `decisiones.md`.** Tienen marcadores (`ADR-TOC`,
   `ADR-INDEX`) que `scripts/gen-adr-toc.mjs` reemplaza al construir: la entrada en el
   árbol, la fila del índice y la etiqueta de estado salen del propio archivo.
3. Valida antes de commitear:

   ```bash
   node portal/scripts/gen-adr-toc.mjs --check
   ```

   El build hace la misma validación y se detiene si un ADR está mal formado.

### Estados

La línea `- **Estado:**` empieza con uno de estos valores, tal cual; después puede ir
un detalle (`Implementada (v2)`, `Aceptada, pendiente de implementación. Se implementa en M5.`).

| Estado | Cuándo |
|---|---|
| Aceptada, pendiente de implementación | Decidida, pero el código todavía no la refleja. |
| Implementada | Ya está en el código. |
| Descartada | Se evaluó y no se adopta. |
| Reemplazada | Ya no aplica; `Reemplazada por` enlaza la que la reemplaza. |

**Quien implementa una decisión cambia su estado a "Implementada" en el mismo commit
que el código**, y agrega en "Dónde se aplica" un enlace al código o a `docs/`.

## Specs OpenAPI (`Writerside/specs/`)

Un JSON por servicio, generado siempre desde el código real (nunca a mano): cada
servicio expone su propio `/v3/api-docs` vía springdoc, y su test
`OpenApiExportIT` (en auth-service, el mismo `OpenApiDocsIT` que ya prueba sus
endpoints) lo exporta en forma determinista (claves ordenadas, indentación fija,
sin `servers`) a `<servicio>/target/openapi/<servicio>.json`.

**Quien cambia un endpoint corre esto en su mismo PR**, para que el spec
commiteado nunca quede desfasado del código que lo generó:

```bash
mvn -pl <servicio> -am verify \
  -Dit.test=OpenApiExportIT,OpenApiDocsIT -DfailIfNoSpecifiedTests=false
node portal/scripts/export-openapi.mjs
```

(`-Dit.test` solo elige qué IT corre; también corren todos los unit tests del
servicio, porque surefire y failsafe comparten la propiedad `skipTests` y no hay
una forma estándar de saltar solo los primeros.)

y commitea el `.json` resultante bajo `Writerside/specs/`.

El job `openapi-drift` de `.github/workflows/portal.yml` corre lo mismo para los
4 servicios y compara contra lo commiteado, pero **solo informa**
(`continue-on-error: true`): un desfase no bloquea el deploy. No es un sustituto
de este paso manual, es su red de seguridad.
