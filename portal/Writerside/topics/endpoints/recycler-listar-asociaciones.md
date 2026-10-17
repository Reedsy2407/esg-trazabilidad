# Listar asociaciones

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve las asociaciones de recicladores, paginadas, con filtro opcional por estado.</tldr>

`GET /associations`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations?status=ACTIVE&page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "address": "Av. Los Olivos 123",
      "contactEmail": "contacto@empresa.pe",
      "contactPhone": "987654321",
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "name": "Asociación Recicladores Norte",
      "registrationNumber": "RN-2024-0123",
      "ruc": "20123456789",
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- `status` filtra por `ACTIVE` ("Activa") o `SUSPENDED` ("Suspendida"). Sin filtro devuelve todas.
- La respuesta es paginada (`page`, `size`, `totalElements`, `totalPages`); el tamaño máximo de página es 100.

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
