# Ver un certificado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve un certificado con sus cifras congeladas y, si fue anulado, cuándo, quién y por qué.</tldr>

`GET /tracked-companies/{companyId}/certificates/{id}`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates/{id}" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates/01a0d8cb-f219-72a2-b006-5150f0e0d685
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "annulledAt": null,
  "annulledByEmail": null,
  "annulledById": null,
  "annulmentReason": "Se emitió con el período equivocado",
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "companyName": "Empresa Demo Envases S.A.C. (ficticia)",
  "companyRuc": "20123456789",
  "districtContext": [
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
  "hierarchyOrigin": "DECLARED_BY_OPERATOR",
  "hierarchyReference": "Informe 2025, fila 3",
  "hierarchyRegisteredByEmail": "operador@asociacion.pe",
  "id": "01a0d8cb-f219-72a2-b006-5150f0e0d685",
  "issuedAt": "2026-10-16T15:20:00Z",
  "kilosTrazados": 35.3,
  "periodEnd": "2026-09-30",
  "periodStart": "2026-09-01",
  "status": "ISSUED",
  "trackedCompanyId": "01a0d8cb-f219-72a2-b006-5150f0e0d604"
}
```

## Reglas de negocio

- `hierarchyOrigin` indica el origen del cumplimiento declarado: `DECLARED_BY_OPERATOR` ("Declarado por el operador") o `REFERENCED_PROOF` ("Con comprobante referenciado").

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-003` | 404 | El certificado no existe o no es de esa empresa. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
