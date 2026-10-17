# Errores de recycler-service

<primary-label ref="recycler"/>

Cada error llega como `application/problem+json` con su `code`. La tabla dice qué lo provoca y qué hacer.

Tres familias: `ASO-` (asociaciones), `CER-` (certificaciones) y `REC-` (recicladores).

| Código | HTTP | Respuesta | Causa | Qué hacer | Dónde aparece |
|---|---|---|---|---|---|
| `ASO-001` | 404 | Asociación no encontrada | El id de la asociación no existe. | Recargar la lista de asociaciones. | [Ver una asociación](recycler-ver-asociacion.md), [Suspender una asociación](recycler-suspender-asociacion.md), [Activar una asociación](recycler-activar-asociacion.md) |
| `ASO-002` | 409 | Ya existe una asociación con ese RUC | Se registró una asociación con un RUC ya usado. | Buscar la asociación existente. | [Registrar una asociación](recycler-crear-asociacion.md) |
| `ASO-003` | 409 | La asociación ya se encuentra en el estado solicitado | Se suspendió una asociación suspendida o se activó una activa. | Recargar: el estado ya cambió. | [Suspender una asociación](recycler-suspender-asociacion.md), [Activar una asociación](recycler-activar-asociacion.md) |
| `ASO-004` | 400 | El RUC no es válido | El RUC no tiene 11 dígitos, no empieza con 10, 15, 16, 17 o 20, o falla el dígito verificador (módulo 11). | Revisar los 11 dígitos. | [Registrar una asociación](recycler-crear-asociacion.md) |
| `CER-001` | 404 | Certificación no encontrada | La certificación no existe o no es de esa asociación. | Recargar las certificaciones de la asociación. | [Ver una certificación](recycler-ver-certificacion.md), [Renovar una certificación](recycler-renovar-certificacion.md) |
| `CER-002` | 404 | La asociación indicada no existe | Se registró una certificación para una asociación inexistente. | Elegir una asociación de la lista. | [Registrar una certificación](recycler-crear-certificacion.md) |
| `CER-003` | 400 | La fecha de emisión debe ser anterior a la fecha de vencimiento | Las fechas de la certificación (o la nueva fecha de renovación) quedan en orden inverso. | Corregir las fechas. | [Registrar una certificación](recycler-crear-certificacion.md), [Renovar una certificación](recycler-renovar-certificacion.md) |
| `REC-001` | 404 | Reciclador no encontrado | El reciclador no existe o no es de esa asociación. | Recargar la lista de recicladores. | [Ver un reciclador](recycler-ver-reciclador.md), [Activar un reciclador](recycler-activar-reciclador.md), [Desactivar un reciclador](recycler-desactivar-reciclador.md) |
| `REC-002` | 409 | Ya existe un reciclador con ese DNI | Se registró un reciclador con un DNI ya usado. | Buscar al reciclador existente. | [Registrar un reciclador](recycler-crear-reciclador.md) |
| `REC-003` | 404 | La asociación indicada no existe | Se registró un reciclador en una asociación inexistente. | Elegir una asociación de la lista. | [Registrar un reciclador](recycler-crear-reciclador.md) |
| `REC-004` | 409 | El reciclador ya se encuentra en el estado solicitado | Se desactivó un reciclador inactivo o se activó uno activo. | Recargar: el estado ya cambió. | [Activar un reciclador](recycler-activar-reciclador.md), [Desactivar un reciclador](recycler-desactivar-reciclador.md) |
