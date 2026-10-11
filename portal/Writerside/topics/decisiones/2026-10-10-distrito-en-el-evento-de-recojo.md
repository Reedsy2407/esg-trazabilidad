# El evento de recojo lleva el ubigeo del vecino

- **Estado:** Aceptada, pendiente de implementación. Se implementa en M5.
- **Fecha:** 2026-10-10
- **Dónde se aplica:** collection-service (publica), reporting-service (consume), recycler-service (consume y lo ignora)
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Para mostrar el contexto distrital de SIGERSOL en un certificado, reporting-service
necesita saber de qué distrito vienen los kilos. Hoy `CollectionRegisteredEvent` no lo
dice, y reporting-service no puede llamar a collection-service (solo eventos).

## Decisión

- `CollectionRegisteredEvent` gana `districtUbigeo`: opcional, 6 dígitos, el ubigeo
  del vecino al momento del recojo.
- Es un cambio aditivo y compatible: mismo exchange, misma routing key, mismas colas.
- reporting-service lo guarda en su ledger. Las filas y eventos viejos sin el campo
  quedan como "distrito desconocido".
- recycler-service no lo usa. Una prueba demuestra que su consumidor acepta el campo
  nuevo y sigue sumando una sola vez.

## Consecuencias

- El ubigeo queda fijo al momento del recojo: si después el vecino cambia de
  distrito, sus recojos anteriores no cambian.

## Alternativas descartadas

- **Distrito de la empresa (domicilio fiscal de SUNAT):** no es donde se recicla.
- **Llamada síncrona a collection-service:** rompe la dirección de dependencias
  aprobada.
- **Proyección local de vecinos en reporting-service:** otro evento más solo para
  copiar un dato que cabe en el evento existente.
