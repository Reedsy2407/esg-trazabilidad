# Listar los registros SIGERSOL

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los datos de cumplimiento de jerarquía declarados, paginados, con filtro opcional por asociación.</tldr>

`GET /sigersol-syncs`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/sigersol-syncs" method="GET"/>

## Ejemplo

Petición:

```http
GET /sigersol-syncs?associationId=01a0d8cb-f219-72a2-b006-5150f0e0d601&page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
    {
      "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
      "declaredAt": "2026-10-16T15:20:00Z",
      "hierarchyCompliancePercent": 85.5,
      "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "officialKilosDeclared": 1200,
      "origin": "DECLARED_BY_OPERATOR",
      "periodEnd": "2026-09-30",
      "periodStart": "2026-09-01",
      "reference": "Informe 2025, fila 3",
      "registeredByEmail": "operador@asociacion.pe",
      "registeredById": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
      "sourceNote": "Informe anual de la asociación"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- Son cifras **declaradas por el operador** e ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.

## Errores posibles

Ninguno propio de esta operación.

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
