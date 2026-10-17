# Registrar un dato SIGERSOL declarado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Registra el cumplimiento de jerarquía que declara el operador de una asociación para un período. Sin él no se puede emitir un certificado.</tldr>

`POST /sigersol-syncs`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/sigersol-syncs" method="POST"/>

## Ejemplo

Petición:

```http
POST /sigersol-syncs
Authorization: Bearer <token>
Content-Type: application/json

{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "hierarchyCompliancePercent": 85.5,
  "officialKilosDeclared": 1200,
  "origin": "DECLARED_BY_OPERATOR",
  "periodEnd": "2026-09-30",
  "periodStart": "2026-09-01",
  "reference": "Informe 2025, fila 3",
  "sourceNote": "Informe anual de la asociación"
}
```

Respuesta `201`:

```json
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
```

## Reglas de negocio

- `hierarchyCompliancePercent` va de 0 a 100, con punto y hasta 2 decimales.
- `origin` es `DECLARED_BY_OPERATOR` ("Declarado por el operador") o `REFERENCED_PROOF` ("Con comprobante referenciado"). Con comprobante, `reference` es obligatoria (`RPT-021`): es un texto (código de envío, PDF «Informe», o número y fecha de facturas), no un archivo, y queda como «referenciado, no verificado por SIGERSOL».
- El período no puede superponerse con otro de la misma asociación (`RPT-006`).
- Quién lo registró sale del token: se guarda su correo.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-006` | 409 | El período del nuevo registro comparte al menos un día con otro de la misma asociación. |
| `RPT-008` | 400 | Una regla del dominio rechazó los datos: porcentaje fuera de 0–100, kilos negativos o fecha final anterior a la inicial. |
| `RPT-021` | 400 | El origen es `REFERENCED_PROOF` y `reference` viene vacío. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
