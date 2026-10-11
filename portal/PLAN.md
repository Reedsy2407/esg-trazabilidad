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
| ADR | Un archivo por decisión; el árbol y el índice se generan en el build. |

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
        └── decisiones/        AAAA-MM-DD-slug.md, uno por ADR
```

Writerside exige nombres de topic **únicos en todo el proyecto**, sin importar la
carpeta. Por eso se usa `errores-auth.md` y `estados-auth.md`, no `errores/auth.md`.

## Builder y limitaciones (verificado en la task 1)

- Imagen `registry.jetbrains.team/p/writerside/builder/writerside-builder:2026.09.0357`,
  la misma versión fija en `scripts/build-local.sh` y en `portal.yml`. JetBrains
  publica una versión por mes, así que el builder sigue activo.
- El build local tarda ~12 s una vez descargada la imagen (la descarga sí es grande).
- `offline-docs=true`: el sitio (~7 MB) trae su propio JS, CSS, fuentes y favicons.
  Sin eso, cargaba `app.js`/`app.css` desde `resources.jetbrains.com`.
- **Idioma de la interfaz:** sin opciones extra, el HTML sale con `lang="en-US"`. El
  builder acepta `-lang es-ES` en su CLI y con eso sale `lang="es-ES"`; el `app.js`
  del sitio elige su traducción con ese atributo, y el bundle trae `es-ES-translation.js`.
  La action oficial `JetBrains/writerside-github-action@v4` **no expone `-lang`** (su
  `dist/index.js` arma el comando solo con `-product`/`-g`, `-pdf` y `-output-dir`), así
  que `portal.yml` corre la imagen con `docker run` y los mismos argumentos que
  `scripts/build-local.sh`. Falta confirmar en el navegador, con el sitio publicado,
  que la interfaz se ve en español.
- Limitación que queda: aun con `-lang es-ES`, los metadatos `og:locale` (`en_US`) e
  `inLanguage` (`en-US`) declaran el sitio en inglés a buscadores y redes. No se
  encontró cómo cambiarlos. La fecha de última modificación de cada página también
  sale en inglés ("11 October 2026").
- Costo: $0 (Actions gratis en repo público, builder gratis, Pages gratis).

## Tasks

1. Esqueleto: cfg, árbol, labels, `inicio.md`, build local con Docker.
2. `portal.yml`: build, checker y deploy a Pages.
3. Specs OpenAPI y `openapi-drift` (antes, comparar alternativas: ver abajo).
4. Glosario de estados y catálogo de errores por servicio, más `catalog-drift`.
5. Plantilla, generador de TOC de ADR, los 6 ADR del brief y 4 ADR ya implementados.
6. Visión del producto y páginas de endpoints, un servicio por vez.

## Generación de los specs OpenAPI

_Se completa en la task 3._
