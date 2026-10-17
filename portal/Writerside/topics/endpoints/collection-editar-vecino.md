# Editar un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Corrige la dirección, el distrito, el nombre o el teléfono de un vecino.</tldr>

`PATCH /neighbors/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors/{id}" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /neighbors/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
Content-Type: application/json

{
  "address": "Av. Los Olivos 123",
  "districtUbigeo": "150110",
  "fullName": "Ana Paredes",
  "phone": "987654321"
}
```

Respuesta `200`:

```json
{
  "address": "Av. Los Olivos 123",
  "district": "Comas",
  "districtUbigeo": "150110",
  "fullName": "Ana Paredes",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "phone": "987654321",
  "status": "ACTIVE"
}
```

## Reglas de negocio

- Dirección, distrito y nombre son obligatorios en la edición; el teléfono es opcional.
- Cambiar el distrito vale para los recojos que se registren después; el distrito que ya viajó con un recojo anterior no se corrige.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-001` | 404 | El vecino no existe. También responde un cambio de estado o una edición que pierde la carrera contra una eliminación. |
| `COL-010` | 400 | El código de ubigeo no es uno de los 50 distritos INEI. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
