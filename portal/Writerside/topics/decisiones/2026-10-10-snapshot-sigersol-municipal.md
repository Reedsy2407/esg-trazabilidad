# Los datos de SIGERSOL Municipal se guardan como snapshot por año y distrito

- **Estado:** Aceptada, pendiente de implementación. Se implementa en M5.
- **Fecha:** 2026-10-10
- **Dónde se aplica:** reporting-service, contexto distrital de SIGERSOL Municipal
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

SIGERSOL Municipal "Acceso Libre" tiene endpoints REST públicos de solo lectura con
datos agregados por distrito y año: recicladores formalizados, asociaciones, residuos
inorgánicos valorizados y lugares de acopio. No están documentados, no se verificaron
sus términos de uso y pueden cambiar sin aviso. Responden desde EE. UU. (spike S2,
runner de GitHub), así que no se espera bloqueo desde Render. Los datos son anuales:
2024 es el último año con filas (spike S1).

No traen RUC ni kilos por empresa. El dato por empresa está en SIGERSOL No Municipal,
con login, y no se automatiza.

## Decisión

- Un cliente de solo lectura detrás de un puerto, sin credenciales y con timeouts
  cortos. Solo usa los **conteos de asociaciones y recicladores** por distrito. Las
  toneladas no se usan: la API no declara su unidad y sus cifras se contradicen (ver
  [Aviso orientativo de kilos frente al total distrital](2026-10-10-aviso-de-plausibilidad.md),
  descartada).
- Cada respuesta útil se guarda en `sigersol_district_snapshot` (año, ubigeo,
  asociaciones, recicladores, fecha de consulta). El snapshot hace de caché larga y de
  "último dato bueno" si SIGERSOL deja de responder.
- El mapeo de distritos se hace por código: los códigos de SIGERSOL son el sufijo del
  ubigeo INEI. El nombre (en mayúsculas y sin tildes) solo se usa como chequeo
  cruzado.
- Año por defecto: el último con filas.
- Al emitir, el contexto de cada distrito del certificado se congela en el propio
  certificado, rotulado "Fuente: SIGERSOL Municipal, año X, datos agregados".
  Reexportar nunca vuelve a consultar SIGERSOL.
- Sin datos ni snapshot, la pantalla dice "no disponible". Nada se bloquea por
  SIGERSOL.

## Consecuencias

- Las pruebas usan respuestas grabadas; CI nunca llama al servicio real.
- Si SIGERSOL cambia su API, el contexto se congela en el último snapshot y la
  pantalla indica su año.

## Alternativas descartadas

- **Consultar en vivo en cada pantalla:** frágil, lento y abusivo con un servicio
  público para datos que cambian una vez al año.
- **Caché solo en memoria:** se pierde cada vez que Render duerme el servicio (15
  min).
- **Conciliación anual o PDF por empresa:** reemplazado por el brief v3.
