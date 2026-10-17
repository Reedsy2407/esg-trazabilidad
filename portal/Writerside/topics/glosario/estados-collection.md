# Estados de collection-service

<primary-label ref="collection"/>

## Vecino

| Valor de la API | Etiqueta | Qué permite | Qué bloquea |
|---|---|---|---|
| `ACTIVE` | Activo | Recibir recojos y cronogramas activos. | — |
| `INACTIVE` | Inactivo | Se conserva el historial. | Nuevos recojos, nuevos cronogramas activos y reanudar cronogramas (`COL-013`). |

`INACTIVE` es la forma de dar de baja a un vecino **con recojos**: eliminar solo se permite si no tiene ninguno (`COL-011`). Cada cambio de estado exige un motivo y queda en la bitácora con quién y cuándo. Al desactivarlo, sus cronogramas `ACTIVE` pasan a `PAUSED`.

## Cronograma

Un cronograma es el día de la semana (`MONDAY`… `SUNDAY`) y la hora en que se recoge en casa del vecino. Un vecino tiene **un solo cronograma `ACTIVE` por día** (`COL-002`).

| Valor de la API | Etiqueta | Cómo se llega | Cómo se sale |
|---|---|---|---|
| `ACTIVE` | Activo | Al crearlo, o al reactivar uno pausado. | Pausar o cancelar. |
| `PAUSED` | Pausado | Pausar uno `ACTIVE` (el vecino viaja) o desactivar al vecino. | Reactivar (vuelve a `ACTIVE`) o cancelar. |
| `CANCELLED` | Cancelado | Cancelar uno `ACTIVE` o `PAUSED`. | **No tiene salida**: si el vecino vuelve, se crea un cronograma nuevo. |

Cualquier otra transición responde `COL-008`. Al llevarse un recojo con `scheduleId` solo se guarda el vínculo: el cronograma no cambia de estado ni los kilos.

<code-block lang="plantuml" src="../../puml/ciclo-cronograma.puml"/>

## Recojo

El recojo **no tiene estados**: no se edita ni se borra. Es una fila de un [libro de solo agregar](recojos-inmutables.md).

## Empresa de collection-service

`ACTIVE` ("Activa") o `INACTIVE` ("Inactiva"). Es un dato interno de este servicio que la consola no usa; no es la empresa de los certificados (ver [Estados de reporting-service](estados-reporting.md)).
