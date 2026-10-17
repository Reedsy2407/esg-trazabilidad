# Portal de documentación — plan

Portal estático (JetBrains Writerside) con las decisiones de negocio y los endpoints
de esg-trazabilidad explicados en lenguaje de negocio. Este archivo vive fuera de
`Writerside/` a propósito: es el plan de trabajo, no se publica.

## Decisiones ya tomadas

| Tema | Decisión |
|---|---|
| Hosting | GitHub Pages, desplegado desde `.github/workflows/portal.yml`. `render.yaml` no cambia. Paso manual: Settings → Pages → fuente "GitHub Actions". |
| Disparo de `portal.yml` | Solo cambios en `portal/**`, una vez por semana (cron) y `workflow_dispatch`. No se dispara con cambios en `*-service/src` ni `shared-kernel`. |
| Jobs de desfase | `openapi-drift` y `catalog-drift` **informan, no bloquean** (`continue-on-error: true`). Como el workflow no corre en commits de servicios, tampoco entra en el `checksPass` de Render. |
| Deploy | `deploy` depende solo de `build` y del checker de Writerside, nunca del desfase. |
| Catálogos | Un archivo por servicio para errores y para estados, así varias terminales agregan sin conflicto. |
| ADR | Un archivo por decisión; el árbol, el índice y la etiqueta de estado se generan en el build (`scripts/gen-adr-toc.mjs` sobre la copia `portal/.build/`). Estados: Aceptada, pendiente de implementación · Implementada · Descartada · Reemplazada. Quien implementa una decisión la pasa a "Implementada" en el mismo commit que el código. |
| Valores de la API | El glosario y los ADR escriben siempre el valor de la API y su etiqueta en español. La etiqueta es **por entidad**, no global: `UNVERIFIED` es "No verificada" para una empresa y "No verificado" para la cobertura. Ejemplos de v3 (todavía no existen en el código de `main`): `ANNULLED` = "Anulado", `ISSUED` = "Emitido", `DECLARED_BY_OPERATOR` = "Declarado por el operador". |

### Pendiente de endurecer (deuda explícita)

1. Hacer bloqueantes los jobs de desfase (quitar `continue-on-error`).
2. Disparar `portal.yml` también con cambios en `*-service/src/main/**` y `shared-kernel/src/main/**`,
   para que el desfase se vea en el mismo PR y no en el cron semanal.

Ambos cambios juntos hacen que un PR de servicio con docs desfasadas falle su check, y
con `autoDeployTrigger: checksPass` eso frena el deploy en Render. Hay que decidirlo de
forma consciente cuando el portal esté estable.

## Estructura

```
portal/
├── PLAN.md                    este archivo
├── README.md                  cómo construir en local, convenciones (ADR, catálogos)
├── plantillas/adr.md          plantilla de ADR (fuera de Writerside: no se publica)
├── scripts/                   build local, export de OpenAPI, chequeos de desfase, TOC de ADR
└── Writerside/
    ├── writerside.cfg
    ├── esg.tree               árbol de navegación (instancia "esg")
    ├── labels.list            servicio · estado de ADR
    ├── v.list                 variables
    ├── cfg/buildprofiles.xml
    ├── specs/                 OpenAPI de los 4 servicios (generado, no se edita a mano)
    └── topics/
        ├── inicio.md
        ├── vision/
        ├── glosario/          estados-<servicio>.md
        ├── endpoints/         una página por recurso
        ├── errores/           errores-<servicio>.md
        └── decisiones/        decisiones.md (índice) + AAAA-MM-DD-slug.md, uno por ADR
```

Writerside exige nombres de topic **únicos en todo el proyecto**, sin importar la
carpeta. Por eso se usa `errores-auth.md` y `estados-auth.md`, no `errores/auth.md`.

## Builder y limitaciones (verificado en la task 1)

- Imagen `registry.jetbrains.team/p/writerside/builder/writerside-builder:2026.09.0357`,
  la misma versión fija en `scripts/build-local.sh` y en `portal.yml`. JetBrains
  publica una versión por mes, así que el builder sigue activo.
- La imagen ocupa **8,78 GB** descomprimida (es la misma que usa la action oficial). El
  build en sí tarda ~12 s; en CI casi todo el tiempo del job `build` es el pull. El
  runner `ubuntu-latest` tenía 86 GB libres en el primer run, así que no hace falta
  liberar espacio antes.
- `offline-docs=true`: el sitio (~7 MB) trae su propio JS, CSS, fuentes y favicons.
  Sin eso, cargaba `app.js`/`app.css` desde `resources.jetbrains.com`.
- **Idioma de la interfaz:** sin opciones extra, el HTML sale con `lang="en-US"`. El
  builder acepta `-lang es-ES` en su CLI y con eso sale `lang="es-ES"`; el `app.js`
  del sitio elige su traducción con ese atributo, y el bundle trae `es-ES-translation.js`.
  La action oficial `JetBrains/writerside-github-action@v4` **no expone `-lang`** (su
  `dist/index.js` arma el comando solo con `-product`/`-g`, `-pdf` y `-output-dir`), así
  que el job `build` de `portal.yml` no usa la action: ejecuta
  `scripts/build-local.sh`, y así la versión y los argumentos del builder están en un
  solo lugar. Falta confirmar en el navegador, con el sitio publicado,
  que la interfaz se ve en español.
- Limitación que queda: aun con `-lang es-ES`, los metadatos `og:locale` (`en_US`) e
  `inLanguage` (`en-US`) declaran el sitio en inglés a buscadores y redes. No se
  encontró cómo cambiarlos. La fecha de última modificación de cada página también
  sale en inglés ("11 October 2026").
- Esa fecha es la del build, no la del último commit del topic: el script monta solo
  `portal/`, y el `.git` queda fuera del contenedor.
- Costo: $0 (Actions gratis en repo público, builder gratis, Pages gratis).

## Workflow `portal.yml` (task 2)

| Job | Depende de | Qué hace |
|---|---|---|
| `build` | — | `bash portal/scripts/build-local.sh` y sube el zip y `report.json` como artifact `portal`. |
| `check` | `build` | `JetBrains/writerside-checker-action@v1`: falla si el builder reportó errores. |
| `deploy` | `build`, `check` | Solo en `main` (no en PR): descomprime el zip y publica en GitHub Pages. |

Disparadores: `portal/**` y el propio `portal.yml` (push a `main` y PR), cron los lunes
a las 11:00 UTC y `workflow_dispatch`. El propio workflow está en la lista para que un
cambio a un job se pruebe con su mismo push.

**Paso manual (una sola vez):** Settings → Pages → Build and deployment → Source:
"GitHub Actions". Sin eso, `deploy` falla al configurar Pages.

## Tasks

1. ✅ Esqueleto: cfg, árbol, labels, `inicio.md`, build local con Docker.
2. ✅ `portal.yml`: build, checker y deploy a Pages.
5a. ✅ Adelantada por pedido del usuario (la otra terminal tiene borradores esperando):
    plantilla `plantillas/adr.md`, `scripts/gen-adr-toc.mjs` (con tests) e índice de
    decisiones.
3. ✅ Specs OpenAPI y `openapi-drift` (rama `portal/openapi-export`).
4. ✅ Glosario de estados y catálogo de errores por servicio, más `catalog-drift` (informativo).
5b. ✅ ADR: los 4 anteriores más 8 (empresa con dos estados, cumplimiento declarado, contraseñas NIST, JWT sin revocación, recuperación por código, recuperación en dos pasos, correo por HTTPS con Apps Script, servicio caído 503), todos Implementada.
6. ✅ Visión del producto, modelo de dominio y una página por operación de los cuatro servicios (64 páginas), con diagramas PlantUML.

## Estado del contenido (M9 a M10)

- **Specs OpenAPI** regeneradas con `export-openapi.mjs` (auth 12 rutas, recycler 14, collection 20, reporting 19). La ruta `/test/ping` de auth-service sale de un controlador de prueba y no tiene página.
- **Endpoints:** una página por operación, 64 en total, con `<api-endpoint>` sobre la spec, rol, ejemplo de petición y respuesta, reglas y errores, y una etiqueta de estabilidad (Estable; Reciente = añadido o cambiado entre M7 y M10; Interno = `/companies`). Las páginas son fuentes: se editan a mano.
- **Páginas de negocio** en `topics/vision/` (16), glosario de estados (1 + 4) y catálogo de errores (1 + 4, con los comunes).
- **Diagramas** en `Writerside/puml/` (7 PlantUML: flujo de punta a punta, modelo de dominio, ciclo del cronograma, ciclo del certificado, secuencia y estados de la recuperación, servicio caído), incluidos con `<code-block lang="plantuml" src="…"/>`. El builder los renderiza (secuencia, estados y clases) sin Graphviz; un diagrama muy ancho se desplaza en horizontal dentro de la página. Se prefirió `code-block src` a `<img src="x.puml">`, que exige además una variante `_dark` por archivo (inspección VIS011).
- **`catalog-drift`:** `scripts/catalog-drift.mjs` compara los códigos de los `*Errors.java` con la primera columna de cada `errores-<servicio>.md`; job informativo en `portal.yml`. No cubre los códigos de shared-kernel (`AUTH-000`, `VALIDATION_ERROR`, `DATA_CONFLICT`, `SERVICE_UNAVAILABLE`).
- **Pendiente:** confirmar en el sitio publicado que la interfaz se ve en español (la barra y el buscador salen en `es-ES`; los textos fijos del componente `api-endpoint`, como «Request parameters», salen en inglés).

## Generación de los specs OpenAPI (task 3)

- Cada servicio exporta su propio `/v3/api-docs` (springdoc) con su test
  `OpenApiExportIT` — en auth-service, un método nuevo en el `OpenApiDocsIT` que ya
  existía, para no duplicar su arranque (`@Testcontainers` + `@SpringBootTest`
  + Postgres). El endpoint es público en los 4 servicios (`SecurityConfig`), así
  que el test no necesita token.
- El export es determinista: claves JSON ordenadas recursivamente, indentación fija
  de 2 espacios con `\n` (no el separador del sistema operativo), y sin el campo
  `servers` (springdoc lo llena con el puerto aleatorio del test, nunca el mismo dos
  veces). El propio test compara dos exports independientes y falla si no son
  idénticos — la prueba de que es determinista vive en el test, no solo en la
  documentación. Queda en `<servicio>/target/openapi/<servicio>.json`.
- `portal/scripts/export-openapi.mjs` copia esos 4 JSON a
  `portal/Writerside/specs/<servicio>.json`. Quien cambia un endpoint corre el test
  + el script en su propio PR y commitea el resultado (ver `portal/README.md`).
- El job `openapi-drift` de `portal.yml` corre lo mismo para los 4 servicios y
  compara contra lo commiteado con `git diff --exit-code`; **informa, no bloquea**
  (`continue-on-error: true`, consistente con la fila "Jobs de desfase" de arriba).
  Usa el mismo filtro de disparo que el resto de `portal.yml` (`portal/**`, cron
  semanal, `workflow_dispatch`): nunca corre en un push a `*-service/src`, así que
  nunca entra al `checksPass` de Render tampoco.
