# Activar una asociación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Pasa una asociación de `SUSPENDED` ("Suspendida") a `ACTIVE` ("Activa").</tldr>

`PATCH /associations/{id}/activate`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations/{id}/activate" method="PATCH"/>

## Ejemplo

Petición:

```http
PATCH /associations/01a0d8cb-f219-72a2-b006-5150f0e0d685/activate
Authorization: Bearer <token>
```

Respuesta `200`:

```json
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
```

## Reglas de negocio

- Activar una asociación que ya está activa responde `ASO-003`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `ASO-001` | 404 | El id de la asociación no existe. |
| `ASO-003` | 409 | Se suspendió una asociación suspendida o se activó una activa. |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
