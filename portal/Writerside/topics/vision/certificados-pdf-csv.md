# Certificados en PDF y CSV

<primary-label ref="reporting"/>

Un certificado emitido se descarga como PDF ([PDF](reporting-descargar-pdf.md)) y como CSV ([CSV](reporting-descargar-csv.md)). Ambos salen de las cifras **congeladas** en el certificado: reexportar nunca vuelve a consultar SIGERSOL ni recalcula kilos.

<code-block lang="plantuml" src="../../puml/ciclo-certificado.puml"/>

## Qué dicen los dos archivos

| Elemento | PDF | CSV |
|---|---|---|
| Estado | Marca **VIGENTE** (`ISSUED`) o **ANULADO** (`ANNULLED`) | Columna «Estado»: Vigente o Anulado |
| Si está anulado | Cuándo, quién (correo) y por qué, y una marca de agua «ANULADO» en cada página | «Anulado en» (hora de Lima), «Anulado por» y «Motivo de anulación» |
| Fechas | `dd/MM/aaaa`, horas de Lima | `dd/MM/aaaa`, horas de Lima |
| Cumplimiento de jerarquía | Rotulado «(declarado)», con la nota de que lo declara el operador | Igual |
| Contexto distrital | Una fila por distrito con los conteos de SIGERSOL Municipal, su año y su fuente, o «no disponible» / «Distrito desconocido» | Igual, con «Disponible / No disponible» |
| Pie | «Proyecto académico, sin validez legal» y «Página i de n» | — |
| Pesadas | — | Las pesadas que respaldan los kilos |

El PDF está compuesto como el ticket de la consola: título, sello VIGENTE o ANULADO, la empresa ajustada a los márgenes, los kilos como cifra principal y una tabla rayada.

## Formato del CSV

- **UTF-8 con BOM** (para que Excel reconozca las tildes).
- **Separador coma**, saltos de línea **CRLF** y comillas según **RFC 4180**: un campo con comas, comillas o saltos de línea va entre comillas.
- Los números llevan **punto decimal** y no llevan separador de miles.
- Los nombres de campo usan un solo estilo (Estado, Anulado en, Anulado por, Motivo de anulación, Disponible / No disponible).

> **Límite conocido.** En un Windows con coma decimal (por ejemplo, es-ES) Excel puede abrir el CSV en una sola columna. Se usa Datos → Desde texto/CSV y se elige la coma como separador. Con la configuración de Perú, México o en-US abre en columnas.

## Un certificado anulado

No se borra: sigue en la lista de la empresa como «Anulado», con sus cifras intactas, y su PDF y su CSV llevan la marca. Libera su período, y no cuenta en los resúmenes ni en el gráfico de kilos por período. Anular uno ya anulado responde `RPT-019`. Ver [Anular un certificado](reporting-anular-certificado.md).
