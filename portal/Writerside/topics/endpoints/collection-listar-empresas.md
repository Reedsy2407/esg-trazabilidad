# Listar empresas (collection-service)

<primary-label ref="collection"/>
<secondary-label ref="interno"/>

<tldr>Devuelve las empresas registradas en collection-service. **No es la empresa de los certificados**: ver [Dos tipos de empresa](vision-y-servicios.md).</tldr>

`GET /companies`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/companies" method="GET"/>

## Ejemplo

Petición:

```http
GET /companies?status=ACTIVE&page=0&size=20
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

- La consola no usa estos endpoints. Ningún evento los une con las empresas de reporting-service (`/tracked-companies`).

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
