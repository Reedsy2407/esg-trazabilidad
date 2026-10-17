# Descargar el PDF de un certificado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve el certificado como un PDF con la forma del ticket.</tldr>

`GET /tracked-companies/{companyId}/certificates/{id}/pdf`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates/{id}/pdf" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates/01a0d8cb-f219-72a2-b006-5150f0e0d685/pdf
Authorization: Bearer <token>
```

Respuesta `200`: el archivo, con `Content-Type: application/pdf` y `Content-Disposition: attachment`.

## Reglas de negocio

- Marca **VIGENTE** o **ANULADO** (en los anulados, cuándo, quién y por qué, y una marca de agua en cada página).
- Fechas en `dd/MM/aaaa` y horas de Lima. Pie «Proyecto académico, sin validez legal» y «Página i de n».
- Reexportar nunca vuelve a consultar SIGERSOL: el contexto distrital está congelado en el certificado.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-003` | 404 | El certificado no existe o no es de esa empresa. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
