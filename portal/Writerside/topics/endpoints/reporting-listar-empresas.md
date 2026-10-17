# Listar empresas

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve las empresas a las que se emiten certificados, paginadas.</tldr>

`GET /tracked-companies`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies?page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
      "contactEmail": "contacto@empresa.pe",
      "contactName": "Rosa Medina",
      "contactPhone": "987654321",
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "name": "Asociación Recicladores Norte",
      "ruc": "20123456789",
      "status": "ACTIVE",
      "sunatCheckedAt": "2026-10-16T15:20:00Z",
      "sunatCondicion": "HABIDO",
      "sunatEstado": "ACTIVO",
      "verification": "VERIFIED",
      "warning": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- Es la pantalla «Empresas»: nombre, RUC y si está `ACTIVE` ("Activa") o `INACTIVE` ("Inactiva").
- Las empresas eliminadas no aparecen.

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
