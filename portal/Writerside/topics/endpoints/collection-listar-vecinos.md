# Listar vecinos

<primary-label ref="collection"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los vecinos que entregan residuos, paginados, con filtros opcionales por estado y por distrito.</tldr>

`GET /neighbors`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/neighbors" method="GET"/>

## Ejemplo

Petición:

```http
GET /neighbors?status=ACTIVE&districtUbigeo=150110&page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "address": "Av. Los Olivos 123",
      "district": "Comas",
      "districtUbigeo": "150110",
      "fullName": "Ana Paredes",
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "phone": "987654321",
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

- `status` filtra por `ACTIVE` ("Activo") o `INACTIVE` ("Inactivo").
- `districtUbigeo` filtra por el código INEI exacto del distrito; `district` filtra por el nombre. No hay búsqueda por texto libre del nombre de la persona.

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
