# Estados de reporting-service

<primary-label ref="reporting"/>

## Empresa: dos estados independientes

Una empresa tiene **dos** estados que cambian por separado: si está activa y si está verificada con SUNAT.

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `status: ACTIVE` | Activa | Puede recibir certificados. |
| `status: INACTIVE` | Inactiva | No puede recibir certificados (`RPT-015`). Se alterna con [Desactivar](reporting-desactivar-empresa.md) y [Reactivar](reporting-activar-empresa.md) con motivo. |
| `verification: VERIFIED` | Verificada | Su nombre legal viene de SUNAT. |
| `verification: UNVERIFIED` | No verificada | Se registró cuando SUNAT no respondía, con un nombre escrito a mano. No puede recibir certificados (`RPT-016`) hasta [verificarla con SUNAT](reporting-verificar-empresa-sunat.md). |

Las cuatro combinaciones y lo que permite cada una están en [Estados de empresa independientes](empresas-estados-independientes.md). Una empresa se elimina solo sin certificados ([Eliminar una empresa: con o sin certificados](eliminar-empresa.md)); con certificados solo se desactiva.

## Certificado

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `ISSUED` | Emitido | Vigente: cuenta para los resúmenes y ocupa su período. |
| `ANNULLED` | Anulado | Se anuló con motivo, quién y cuándo. Sigue en la lista con sus cifras intactas, libera su período y no cuenta en los resúmenes. **Es final**: no se vuelve a `ISSUED`; para corregir se emite uno nuevo. |

Un certificado nunca se edita ni se borra. En el PDF y el CSV, `ISSUED` se rotula «VIGENTE» y `ANNULLED` «ANULADO».

## Origen del dato SIGERSOL

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `DECLARED_BY_OPERATOR` | Declarado por el operador | Cifra que declara el operador de la asociación y que el personal ingresa a mano. |
| `REFERENCED_PROOF` | Con comprobante referenciado | Igual, pero con una referencia escrita a un comprobante (código de envío, PDF «Informe», facturas). Es solo texto: **no está verificado por SIGERSOL**. |

En ambos casos el cumplimiento de jerarquía es una cifra **declarada**; ninguna etiqueta dice que SIGERSOL la haya validado.

## Cobertura de SIGERSOL de un período

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `RECORDED` | Registrado | Un único registro declarado cubre todo el período: se puede emitir. |
| `UNVERIFIED` | No verificado | Ningún registro cubre el período completo: emitir responde `RPT-005`. |

## Contexto distrital de una línea del certificado

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `AVAILABLE` | Disponible | Hay conteos públicos de SIGERSOL Municipal para el distrito (asociaciones y recicladores, con su año). |
| `UNAVAILABLE` | No disponible | SIGERSOL Municipal no tiene datos o no respondió y no hay un último dato guardado. Nada se bloquea por eso. |
| `UNKNOWN_DISTRICT` | Distrito desconocido | Kilos de recojos que no registraron distrito. |

El contexto describe al **distrito**, no a la empresa ni a sus proveedores, y queda congelado en el certificado al emitirlo.
