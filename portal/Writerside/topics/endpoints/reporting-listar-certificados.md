# Listar los certificados de una empresa

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve los certificados de la empresa, paginados, vigentes y anulados.</tldr>

`GET /tracked-companies/{companyId}/certificates`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates?page=0&size=20
Authorization: Bearer <token>
```

Respuesta `200`:

```json
{
  "content": [
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
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Reglas de negocio

- Cada certificado trae su `status`: `ISSUED` ("Emitido") o `ANNULLED` ("Anulado"). Un anulado sigue en la lista.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
