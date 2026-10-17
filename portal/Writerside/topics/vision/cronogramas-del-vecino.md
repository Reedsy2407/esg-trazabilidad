# El cronograma del vecino

<primary-label ref="collection"/>

## Qué es

Un cronograma dice **qué día de la semana y a qué hora** se recoge en casa de un vecino (por ejemplo, los jueves a las 07:30). Sirve para organizar la ruta; no es un requisito para registrar un recojo.

## Un solo cronograma activo por día

Un vecino puede tener varios cronogramas, pero **solo uno `ACTIVE` por día de la semana**, sin importar la hora. Crear o reanudar otro para ese día responde `COL-002` y la solución es pausar o cancelar el que ya está.

## Ciclo

<code-block lang="plantuml" src="../../puml/ciclo-cronograma.puml"/>

| Acción | Desde | Hasta | Endpoint |
|---|---|---|---|
| Crear | — | `ACTIVE` | [Crear un cronograma](collection-crear-cronograma.md) |
| Pausar | `ACTIVE` | `PAUSED` | [Pausar](collection-pausar-cronograma.md) |
| Reactivar | `PAUSED` | `ACTIVE` | [Reactivar](collection-reactivar-cronograma.md) |
| Cancelar | `ACTIVE` o `PAUSED` | `CANCELLED` | [Cancelar](collection-cancelar-cronograma.md) |

`CANCELLED` es final: no tiene salida. Si el vecino vuelve, se crea un cronograma nuevo. Cualquier transición que no esté en la tabla responde `COL-008`.

## Qué pasa cuando un recojo se lleva «con cronograma»

Al registrar un recojo se puede enviar el `scheduleId` de un cronograma **de ese vecino**. El servicio **solo guarda el vínculo**:

- el cronograma **no cambia de estado**;
- los **kilos** del recojo son los que se escribieron, no dependen del cronograma;
- los **certificados** no cambian por el vínculo: se calculan con los kilos de los recojos.

Un `scheduleId` de otro vecino responde `COL-006`, igual que uno inexistente, para no revelar que existe en otro lado.

## Con el vecino inactivo

Desactivar a un vecino pasa sus cronogramas `ACTIVE` a `PAUSED`, y mientras esté inactivo no se pueden crear cronogramas activos ni reanudar los pausados (`COL-013`). Al reactivarlo, la consola ofrece los pausados para reanudarlos uno por uno; no se reanudan solos. Eliminar a un vecino (solo posible sin recojos) cancela los cronogramas que le queden.
