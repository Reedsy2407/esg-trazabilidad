# Registrar una empresa (collection-service)

<primary-label ref="collection"/>
<secondary-label ref="interno"/>

<tldr>Da de alta una empresa en collection-service. No genera certificados.</tldr>

`POST /companies`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/collection-service.json" endpoint="/companies" method="POST"/>

## Ejemplo

Petición:

```http
POST /companies
Authorization: Bearer <token>
Content-Type: application/json

{
  "address": "Av. Los Olivos 123",
  "contactEmail": "contacto@empresa.pe",
  "contactPhone": "987654321",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789"
}
```

Respuesta `201`:

```json
{
  "address": "Av. Los Olivos 123",
  "contactEmail": "contacto@empresa.pe",
  "contactPhone": "987654321",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789",
  "status": "ACTIVE"
}
```

## Reglas de negocio

- El RUC tiene 11 dígitos y no puede repetirse.
- La empresa de los certificados se registra en reporting-service con [Registrar una empresa](reporting-registrar-empresa.md).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `COL-005` | 409 | Se registró una empresa de collection-service con un RUC ya usado. |

Como cualquier endpoint de collection-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
