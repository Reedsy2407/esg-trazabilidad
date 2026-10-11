# Aviso orientativo de kilos frente al total distrital

- **Estado:** Descartada por calidad de datos (2026-10-10, decisión de Angel).
- **Servicio:** reporting-service

## Contexto

El brief v3 pedía avisar, sin bloquear, cuando los kilos declarados de un material fueran desproporcionados frente al total del distrito en SIGERSOL Municipal (`reporteValorizacion/listaRsInorganicos`).

El spike S1 encontró dos problemas:

- **La API no declara la unidad, y sus cifras se contradicen.** La Molina 2024 suma 1 389,29 con 31 recicladores, lo que solo es creíble en toneladas. La provincia de Lima suma 2 428 847,58, lo que solo es creíble en kilos: 2,4 millones de toneladas de inorgánicos valorizados no es una cifra posible.
- **Los recojos no registran el material**, solo el peso total.

## Decisión

No hay aviso de plausibilidad. v3 no muestra ni compara toneladas de SIGERSOL: el contexto distrital usa solo los conteos de asociaciones y recicladores.

Las toneladas por material se reconsideran solo si algún día se confirma la unidad de esas cifras.

## Alternativas descartadas

- **Asumir una unidad y fijar un umbral:** con cifras que se contradicen, el aviso fallaría en ambos sentidos y daría una falsa sensación de control.
- **Bloquear la emisión:** datos agregados municipales con problemas de calidad visibles no pueden decidir sobre un certificado.

## Consecuencias

- Los kilos de un certificado no se contrastan con ninguna cifra pública. El certificado y el portal no dicen ni sugieren que el sistema valide contra SIGERSOL.
