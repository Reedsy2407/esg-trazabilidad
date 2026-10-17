# Listar los recicladores de una asociación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los recicladores de la asociación, paginados, con filtro opcional por estado.</tldr>

`GET /associations/{associationId}/recyclers`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{associationId}/recyclers" method="GET"/>

## Ejemplo

Petición:

```http
GET /associations/01a0d8cb-f219-72a2-b006-5150f0e0d601/recyclers?status=ACTIVE&page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
      "dni": "40123456",
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

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
