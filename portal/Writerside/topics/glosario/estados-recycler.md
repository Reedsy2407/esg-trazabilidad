# Estados de recycler-service

<primary-label ref="recycler"/>

## Asociación

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `ACTIVE` | Activa | Es el estado con el que nace una asociación. |
| `SUSPENDED` | Suspendida | Una asociación apartada con [Suspender](recycler-suspender-asociacion.md). No se pierde nada: se vuelve a `ACTIVE` con [Activar](recycler-activar-asociacion.md). |

Cambiar al mismo estado en el que ya está responde `ASO-003`.

## Reciclador

| Valor de la API | Etiqueta | Qué significa |
|---|---|---|
| `ACTIVE` | Activo | Estado inicial. |
| `INACTIVE` | Inactivo | El reciclador ya no trabaja en la asociación. Se alterna con [Activar](recycler-activar-reciclador.md) y [Desactivar](recycler-desactivar-reciclador.md); repetir el mismo estado responde `REC-004`. |

## Certificación de una asociación

No tiene un campo de estado: tiene una fecha de vencimiento y el indicador `expired`.

| Situación | Cómo se ve | Qué provoca |
|---|---|---|
| Vigente | `expired: false` | Nada. |
| Por vencer | `expired: false`, faltan menos de 30 días | Solo una advertencia de la pantalla: «por vencer» es una regla de la consola, no un estado del servicio. |
| Vencida | `expired: true` | recycler-service publica `CertificationExpiredEvent` y collection-service **bloquea** nuevos recojos de esa asociación (`COL-009`). |
| Renovada | nueva `expirationDate` | Se publica `CertificationRenewedEvent` y la asociación se desbloquea. |
