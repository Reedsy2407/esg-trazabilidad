# Ver la bitácora de estados de un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Lista los cambios de estado de un vecino: de qué estado a cuál, el motivo, quién lo hizo y cuándo.</tldr>

`GET /neighbors/{id}/status-changes`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{id}/status-changes" method="GET"/>

## Ejemplo

Petición:

```http
GET /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d685/status-changes
Authorization: Bearer <token>
```

Respuesta `200`:

```json
[
  {
    "actorEmail": "admin@asociacion.pe",
    "actorId": "01a0d8cb-f219-72a2-b006-5150f0e0d605",
    "changedAt": "2026-10-16T15:20:00Z",
    "fromStatus": "ACTIVE",
    "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
    "reason": "Motivo escrito por quien hace el cambio",
    "toStatus": "ACTIVE"
  }
]
```

## Reglas de negocio

- Quién y cuándo salen del token y del reloj del servicio; nunca los escribe la persona.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
