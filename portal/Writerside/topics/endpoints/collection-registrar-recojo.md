# Registrar un recojo

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Anota cuántos kilos se recogieron en casa de un vecino, en una fecha y para una asociación. Es el dato del que salen los certificados.</tldr>

`POST /neighbors/{neighborId}/collection-records`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{neighborId}/collection-records" method="POST"/>

## Ejemplo

Petición:

```http
POST /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d602/collection-records
Authorization: Bearer <token>
Content-Type: application/json

{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "collectionDate": "2026-09-17",
  "scheduleId": "01a0d8cb-f219-72a2-b006-5150f0e0d603",
  "weightKg": 12.5
}
```

Respuesta `201`:

```json
{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "collectionDate": "2026-09-17",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "neighborId": "01a0d8cb-f219-72a2-b006-5150f0e0d602",
  "scheduleId": "01a0d8cb-f219-72a2-b006-5150f0e0d603",
  "weightKg": 12.5
}
```

## Reglas de negocio

- Un recojo **no se edita ni se borra**: es un [libro de solo agregar](recojos-inmutables.md). Un error se corrige desactivando al vecino o registrando un recojo nuevo.
- `weightKg` debe ser mayor que 0, con punto decimal y hasta 2 decimales.
- El vecino debe estar `ACTIVE` (`COL-013`).
- `scheduleId` es opcional: si se envía, debe ser un cronograma **de ese vecino**. Solo se guarda el vínculo; el cronograma no cambia ni cambia los kilos ni los certificados.
- Si la asociación tiene una certificación vencida y sin renovar, se rechaza con `COL-009`. collection-service lo sabe por un evento, sin llamar a recycler-service; por eso **no valida que la asociación exista**.
- Al guardarse, el recojo se publica como `CollectionRegisteredEvent` (con el distrito del vecino) para que reporting-service lo sume a los certificados.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-006` | 404 | El cronograma no existe o no es de ese vecino (también responde un `scheduleId` de otro vecino en un recojo). |
| `COL-009` | 409 | recycler-service avisó por evento que una certificación de la asociación venció y no se renovó. |
| `COL-013` | 409 | Se registró un recojo, se creó un cronograma o se reactivó uno pausado para un vecino INACTIVE. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
