# Errores de collection-service

<primary-label ref="collection"/>

Cada error llega como `application/problem+json` con su `code`. La tabla dice qué lo provoca y qué hacer.

Los códigos de `COL-001` a `COL-013`. `COL-003` no existe (el número quedó sin usar).

| Código | HTTP | Respuesta | Causa | Qué hacer | Dónde aparece |
|---|---|---|---|---|---|
| `COL-001` | 404 | Vecino no encontrado | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. | Recargar la lista de vecinos. | [Ver un vecino](collection-ver-vecino.md), [Editar un vecino](collection-editar-vecino.md), [Desactivar un vecino](collection-desactivar-vecino.md), [Reactivar un vecino](collection-activar-vecino.md) y 7 más |
| `COL-002` | 409 | Conflicto de horario de recojo | El vecino ya tiene un cronograma ACTIVE ese día de la semana. | Pausar o cancelar el cronograma activo de ese día, o elegir otro día. | [Crear un cronograma](collection-crear-cronograma.md), [Reactivar un cronograma](collection-reactivar-cronograma.md) |
| `COL-004` | 404 | Empresa no encontrada | La empresa de collection-service (`/companies`) no existe. | Ver «Dos tipos de empresa» en la visión del producto. | [Ver una empresa (collection-service)](collection-ver-empresa.md) |
| `COL-005` | 409 | Ya existe una empresa con ese RUC | Se registró una empresa de collection-service con un RUC ya usado. | Buscar la empresa existente. | [Registrar una empresa (collection-service)](collection-crear-empresa.md) |
| `COL-006` | 404 | Programación de recojo no encontrada | El cronograma no existe o no es de ese vecino (también responde un `scheduleId` de otro vecino en un recojo). | Recargar los cronogramas del vecino. | [Ver un cronograma](collection-ver-cronograma.md), [Pausar un cronograma](collection-pausar-cronograma.md), [Cancelar un cronograma](collection-cancelar-cronograma.md), [Reactivar un cronograma](collection-reactivar-cronograma.md) y 1 más |
| `COL-007` | 404 | Registro de recojo no encontrado | El recojo no existe o no es de ese vecino. | Recargar los recojos del vecino. | [Ver un recojo](collection-ver-recojo.md) |
| `COL-008` | 409 | Transición de estado no permitida para esta programación | El cambio de estado del cronograma no está permitido desde el estado actual (por ejemplo, reactivar uno cancelado). | Ver el ciclo del cronograma en el glosario de collection. | [Pausar un cronograma](collection-pausar-cronograma.md), [Cancelar un cronograma](collection-cancelar-cronograma.md), [Reactivar un cronograma](collection-reactivar-cronograma.md) |
| `COL-009` | 409 | La asociación tiene una certificación vencida y no puede registrar recojos | recycler-service avisó por evento que una certificación de la asociación venció y no se renovó. | Renovar la certificación en recycler-service; al renovarla se desbloquea la asociación. | [Registrar un recojo](collection-registrar-recojo.md) |
| `COL-010` | 400 | El distrito no está en la lista de Lima Metropolitana y Callao | El código de ubigeo no es uno de los 50 distritos INEI. | Elegir el distrito de la lista. | [Registrar un vecino](collection-registrar-vecino.md), [Editar un vecino](collection-editar-vecino.md) |
| `COL-011` | 409 | El vecino tiene recojos registrados: solo se puede desactivar | Se intentó eliminar un vecino con al menos un recojo. | Desactivar al vecino en lugar de eliminarlo. | [Eliminar un vecino](collection-eliminar-vecino.md) |
| `COL-012` | 409 | El vecino ya se encuentra en el estado solicitado | Se desactivó un vecino inactivo o se activó uno activo. | Recargar: el estado ya cambió. | [Desactivar un vecino](collection-desactivar-vecino.md), [Reactivar un vecino](collection-activar-vecino.md) |
| `COL-013` | 409 | El vecino está inactivo: no puede recibir recojos ni cronogramas activos | Se registró un recojo, se creó un cronograma o se reactivó uno pausado para un vecino INACTIVE. | Reactivar primero al vecino. | [Crear un cronograma](collection-crear-cronograma.md), [Reactivar un cronograma](collection-reactivar-cronograma.md), [Registrar un recojo](collection-registrar-recojo.md) |
