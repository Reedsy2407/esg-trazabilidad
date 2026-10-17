# Anular un certificado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Marca un certificado como `ANNULLED` ("Anulado") con un motivo; es la forma de corregir uno emitido con datos erróneos.</tldr>

`POST /tracked-companies/{companyId}/certificates/{id}/annul`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates/{id}/annul" method="POST"/>

## Ejemplo

Petición:

```http
POST /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates/01a0d8cb-f219-72a2-b006-5150f0e0d685/annul
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Motivo escrito por quien hace el cambio"
}
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

- El motivo es obligatorio (hasta 500 caracteres). Quién y cuándo los toma el servicio del token y del reloj.
- El certificado **no se borra**: sigue en la lista marcado «Anulado», con sus cifras intactas, y su PDF y su CSV llevan la marca.
- Un certificado anulado libera su período y no cuenta en los resúmenes.
- Anular uno ya anulado responde `RPT-019`.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-003` | 404 | El certificado no existe o no es de esa empresa. |
| `RPT-019` | 409 | Se anuló un certificado que ya estaba ANNULLED (por ejemplo, otra persona lo anuló un momento antes). |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
