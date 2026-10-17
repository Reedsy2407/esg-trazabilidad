# Emitir un certificado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Emite el certificado de trazabilidad ESG de una empresa para un período, congelando los kilos y las cifras de ese momento.</tldr>

`POST /tracked-companies/{companyId}/certificates`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates" method="POST"/>

## Ejemplo

Petición:

```http
POST /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates
Authorization: Bearer <token>
Content-Type: application/json

{
  "periodEnd": "2026-09-30",
  "periodStart": "2026-09-01"
}
```

Respuesta `201`:

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

- La empresa debe estar `ACTIVE` (`RPT-015`) y `VERIFIED` (`RPT-016`).
- Debe existir **un único registro SIGERSOL declarado que cubra todo el período** (`RPT-005`): el cumplimiento de jerarquía es obligatorio.
- El período no puede superponerse con otro certificado `ISSUED` de la empresa (`RPT-004`); uno `ANNULLED` libera su período.
- Cada valor impreso (nombre, RUC, kilos, cumplimiento, contexto distrital) se **copia congelado** al emitir; el certificado no cambia aunque lleguen recojos nuevos.
- Un certificado no se edita ni se borra; el único camino de vuelta es [anularlo](reporting-anular-certificado.md).

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-001` | 404 | La empresa no existe o ya fue eliminada. |
| `RPT-004` | 409 | El período comparte al menos un día con un certificado ISSUED de la misma empresa. Uno ANNULLED libera su período. |
| `RPT-005` | 409 | Ningún registro SIGERSOL de la asociación cubre el período completo. Sin ese dato declarado no se emite. |
| `RPT-009` | 400 | El período de un certificado o de su resumen está invertido. |
| `RPT-015` | 409 | Se intentó emitir un certificado para una empresa INACTIVE. |
| `RPT-016` | 409 | Se intentó emitir un certificado para una empresa UNVERIFIED. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
