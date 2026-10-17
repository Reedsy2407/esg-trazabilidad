# La empresa tiene dos estados independientes: activa o inactiva, y verificada o no verificada

- **Estado:** Implementada (M4: `status` y `verification`; `RPT-015` y `RPT-016`).
- **Fecha:** 2026-10-12
- **Dónde se aplica:** reporting-service, ciclo de vida de la empresa y emisión de certificados; [Estados de empresa independientes](empresas-estados-independientes.md)
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Desde que el nombre legal de una empresa viene de SUNAT por su RUC, hay empresas que se registran cuando SUNAT no responde, con un nombre escrito a mano. Esa empresa no está «inactiva»: está sin comprobar. Y una empresa verificada puede dejar de recibir certificados por una decisión del negocio sin dejar de estar verificada. Un solo campo no distingue las dos causas.

## Decisión

Dos campos que cambian por separado:

- `status`: `ACTIVE` («Activa») o `INACTIVE` («Inactiva»), que cambia el personal con motivo.
- `verification`: `VERIFIED` («Verificada») o `UNVERIFIED` («No verificada»), que pasa a verificada con «Verificar con SUNAT».

Solo una empresa `ACTIVE` y `VERIFIED` recibe certificados. El servicio comprueba primero la actividad (`RPT-015`) y después la verificación (`RPT-016`).

## Consecuencias

- Cuatro combinaciones, una sola que emite (tabla en la página de estados).
- Las empresas heredadas, escritas a mano, figuran como no verificadas hasta verificarlas.
- Cada estado tiene una causa y una salida: reactivar o verificar.

## Alternativas descartadas

- **Un solo estado con tres valores (activa, inactiva, no verificada):** mezcla dos causas y obliga a perder una de las dos al cambiar de estado.
- **Dejar emitir a empresas no verificadas con una advertencia:** el certificado repetiría un nombre sin comprobar.
