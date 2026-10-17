# Registrar una asociación

<primary-label ref="recycler"/>
<secondary-label ref="estable"/>

<tldr>Da de alta una asociación de recicladores formalizados.</tldr>

`POST /associations`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/recycler-service.json" endpoint="/associations" method="POST"/>

## Ejemplo

Petición:

```http
POST /associations
Authorization: Bearer <token>
Content-Type: application/json

{
  "address": "Av. Los Olivos 123",
  "contactEmail": "contacto@empresa.pe",
  "contactPhone": "987654321",
  "name": "Asociación Recicladores Norte",
  "registrationNumber": "RN-2024-0123",
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
  "registrationNumber": "RN-2024-0123",
  "ruc": "20123456789",
  "status": "ACTIVE"
}
```

## Reglas de negocio

- El RUC es obligatorio y se valida: 11 dígitos, con prefijo 10, 15, 16, 17 o 20 y dígito verificador (módulo 11). La pantalla hace la misma comprobación antes de enviar.
- No puede haber dos asociaciones con el mismo RUC.
- La asociación nace `ACTIVE`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `ASO-002` | 409 | Se registró una asociación con un RUC ya usado. |
| `ASO-004` | 400 | El RUC no tiene 11 dígitos, no empieza con 10, 15, 16, 17 o 20, o falla el dígito verificador (módulo 11). |

Como cualquier endpoint de recycler-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
