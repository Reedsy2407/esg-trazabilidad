# Aviso orientativo de kilos frente al total distrital

- **Estado:** Propuesta, condicionada (2026-10-10). Último task de M5 y recortable.
- **Servicio:** reporting-service

## Contexto

El brief pide avisar cuando los kilos declarados sean desproporcionados frente al total del distrito en SIGERSOL Municipal. Hay dos límites:

- Los recojos no registran el material, solo el peso total.
- La API de SIGERSOL no declara la unidad. En el spike S1 las magnitudes se contradicen: La Molina 2024 sugiere toneladas y la provincia de Lima sugiere kilos.

## Decisión (si se implementa)

- Se compara el total de kilos trazados del distrito en el período, anualizado, contra el total de inorgánicos valorizados del distrito ese año.
- Es un **aviso orientativo, nunca un bloqueo ni una verificación**, y la pantalla lo dice así.
- El umbral no se fija hasta confirmar la unidad. Si no se confirma, o si SIGERSOL no responde desde Render (spike S2), este aviso se descarta y este ADR pasa a "Descartada".

## Alternativas descartadas

- **Comparar por material:** el sistema no registra material, y agregarlo queda fuera de v3.
- **Bloquear la emisión:** los datos de SIGERSOL son agregados municipales con problemas de calidad visibles; no pueden decidir sobre un certificado.

## Consecuencias

- El aviso puede fallar en ambos sentidos. Es una heurística y se documenta como tal.
