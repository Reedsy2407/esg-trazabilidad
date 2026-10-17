# Descargar el CSV de un certificado

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Devuelve el certificado y las pesadas que lo respaldan como un archivo CSV.</tldr>

`GET /tracked-companies/{companyId}/certificates/{id}/csv`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/{companyId}/certificates/{id}/csv" method="GET"/>

## Ejemplo

Petición:

```http
GET /tracked-companies/01a0d8cb-f219-72a2-b006-5150f0e0d604/certificates/01a0d8cb-f219-72a2-b006-5150f0e0d685/csv
Authorization: Bearer <token>
```

Respuesta `200`: el archivo, con `Content-Type: text/csv; charset=UTF-8` y `Content-Disposition: attachment`.

## Reglas de negocio

- **UTF-8 con BOM, separador coma, saltos de línea CRLF y comillas RFC 4180**; los números usan punto decimal y no llevan separador de miles.
- Trae el estado (Vigente o Anulado) y, en los anulados, cuándo, quién y el motivo.
- Límite conocido: en un Windows con coma decimal (por ejemplo es-ES) Excel puede abrirlo en una sola columna; usar Datos → Desde texto/CSV y elegir la coma.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-003` | 404 | El certificado no existe o no es de esa empresa. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
