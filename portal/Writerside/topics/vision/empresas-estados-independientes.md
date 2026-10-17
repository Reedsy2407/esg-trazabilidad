# Estados de empresa independientes

<primary-label ref="reporting"/>

Una empresa de reporting-service tiene **dos estados que cambian por separado**:

- **Actividad:** `ACTIVE` («Activa») o `INACTIVE` («Inactiva»). La cambia el personal con [Desactivar](reporting-desactivar-empresa.md) y [Reactivar](reporting-activar-empresa.md), con motivo.
- **Verificación:** `VERIFIED` («Verificada») o `UNVERIFIED` («No verificada»). Una empresa nace verificada cuando SUNAT responde al registrarla; nace no verificada si SUNAT no respondía y se registró con un nombre escrito a mano ([Registrar como no verificada](reporting-registrar-empresa-no-verificada.md)). Pasa a verificada con [Verificar con SUNAT](reporting-verificar-empresa-sunat.md).

## Las cuatro combinaciones

| Actividad | Verificación | ¿Se le puede emitir un certificado? | Qué hacer para poder |
|---|---|---|---|
| `ACTIVE` | `VERIFIED` | **Sí** | — |
| `ACTIVE` | `UNVERIFIED` | No (`RPT-016`) | Verificarla con SUNAT. |
| `INACTIVE` | `VERIFIED` | No (`RPT-015`) | Reactivarla. |
| `INACTIVE` | `UNVERIFIED` | No (`RPT-015` primero) | Reactivarla y luego verificarla. |

**Una empresa no verificada no puede emitir**, porque su nombre legal no viene de SUNAT y el certificado repetiría un dato sin comprobar. El servicio comprueba primero la actividad (`RPT-015`) y después la verificación (`RPT-016`).

## Qué no cambia con el estado

- Los certificados ya emitidos siguen igual: desactivar una empresa no los toca.
- Una empresa **con certificados** no se elimina: se desactiva ([Eliminar una empresa: con o sin certificados](eliminar-empresa.md)).
- Las empresas de datos heredados, escritas a mano antes de existir la verificación, figuran como no verificadas hasta verificarlas.

## Por qué dos estados

La decisión está en [La empresa se registra como no verificada cuando SUNAT no responde](2026-10-10-respaldo-empresa-no-verificada.md) y en [Estados de empresa independientes (ADR)](2026-10-12-estados-de-empresa-independientes.md): cada estado tiene una sola causa y una sola salida.
