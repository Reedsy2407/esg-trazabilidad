# Portal de documentación

Sitio estático generado con [JetBrains Writerside](https://www.jetbrains.com/writerside/)
a partir de `portal/Writerside/`. Explica las decisiones de negocio y los endpoints de
esg-trazabilidad en lenguaje de negocio. El plan y las decisiones de diseño del portal
están en [PLAN.md](PLAN.md).

## Construir en local

Con Rancher Desktop en marcha (`docker info` debe responder):

```bash
bash portal/scripts/build-local.sh
```

El sitio queda en `portal/artifacts/webHelpESG2-all.zip`, y los errores del builder en
`portal/artifacts/report.json`. El job `build` de `.github/workflows/portal.yml` usa la
misma imagen, con la misma versión fija y los mismos argumentos; si se cambia en un
lado, se cambia en el otro.

También se puede previsualizar con el plugin de Writerside en IntelliJ IDEA, abriendo
la carpeta `portal/`.

## Reglas para editar

- **Los nombres de topic son únicos en todo el proyecto**, aunque estén en carpetas
  distintas. Usa un prefijo: `errores-auth.md`, `estados-auth.md`.
- Todo topic nuevo debe estar en `Writerside/esg.tree`; si no, el build lo reporta.
