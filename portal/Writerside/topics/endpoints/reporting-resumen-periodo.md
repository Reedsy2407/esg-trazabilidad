# Ver el resumen de un período

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Calcula, sin guardar nada, los kilos trazados y el cumplimiento declarado que tendría un certificado de esa empresa en un período.</tldr>

`GET /tracked-companies/{companyId}/certificate-summary`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificate-summary" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificate-summary?periodStart=2026-09-01&periodEnd=2026-09-30
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "districts": [
    {
      "associationCount": 12,
      "districtName": "Comas",
      "districtUbigeo": "150110",
      "kilos": 35.3,
      "recyclerCount": 340,
      "status": "AVAILABLE",
      "year": 2024
    }
  ],
  "hierarchyCompliancePercent": 85.5,
  "kilosTrazados": 35.3,
  "periodEnd": "2026-09-30",
  "periodStart": "2026-09-01",
  "sigersolCoverage": "RECORDED",
  "sigersolOrigin": "DECLARED_BY_OPERATOR",
  "sigersolRegisteredByEmail": "operador@asociacion.pe",
  "trackedCompanyId": "01a0d8cb-f219-72a2-b006-5150f0e0d604"
}
```

## Reglas de negocio

- `periodStart` y `periodEnd` son obligatorios y van incluidos; con la fecha final anterior a la inicial responde `RPT-009`.
- Los kilos son la suma de los recojos de la asociación de la empresa en el período. El cumplimiento sale del registro SIGERSOL declarado que cubre todo el período; si ninguno lo cubre, `sigersolCoverage` es `UNVERIFIED` ("No verificado") y no se puede emitir.
- Trae el contexto distrital (conteos de SIGERSOL Municipal por distrito) y marca los kilos sin distrito como «distrito desconocido».

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |
| `RPT-009` | 400 | El período de un certificado o de su resumen está invertido. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
