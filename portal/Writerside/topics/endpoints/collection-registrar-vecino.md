# Registrar un vecino

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Da de alta a un vecino que entrega residuos, con su dirección y su distrito.</tldr>

`POST /neighbors`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors" method="POST"/>

## Ejemplo

Petición:

```http
POST /neighbors
Authorization: Bearer <token>
Content-Type: application/json

{
  "address": "Av. Los Olivos 123",
  "district": "Comas",
  "districtUbigeo": "150110",
  "fullName": "Ana Paredes",
  "phone": "987654321"
}
```

Respuesta `201`:

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

- El distrito se elige de la lista de **50 distritos** de Lima Metropolitana y Callao por su código INEI (`districtUbigeo`); otro código responde `COL-010`.
- El vecino nace `ACTIVE`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-010` | 400 | El código de ubigeo no es uno de los 50 distritos INEI. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
