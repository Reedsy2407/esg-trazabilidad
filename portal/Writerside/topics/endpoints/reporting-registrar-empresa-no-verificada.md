# Registrar una empresa como no verificada

<primary-label ref="reporting"/>
<secondary-label ref="estable"/>

<tldr>Salida cuando SUNAT no responde: registra la empresa con un nombre escrito a mano, marcada `UNVERIFIED` ("No verificada").</tldr>

`POST /tracked-companies/unverified`

**Quién puede llamarlo.** Cualquier persona con un token válido (ADMIN o STAFF): este servicio no distingue el rol.

## Contrato

<api-endpoint openapi-path="../../specs/reporting-service.json" endpoint="/tracked-companies/unverified" method="POST"/>

## Ejemplo

Petición:

```http
POST /tracked-companies/unverified
Authorization: Bearer <token>
Content-Type: application/json

{
  "associationId": "01a0d8cb-f219-72a2-b006-5150f0e0d601",
  "contactEmail": "contacto@empresa.pe",
  "contactName": "Rosa Medina",
  "contactPhone": "987654321",
  "name": "Asociación Recicladores Norte",
  "ruc": "20123456789"
}
```

Respuesta `200`:

```json
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
```

## Reglas de negocio

- Una empresa `UNVERIFIED` **no puede recibir certificados** (`RPT-016`) hasta que se verifique con [Verificar con SUNAT](reporting-verificar-empresa-sunat.md).
- Si SUNAT sí responde en ese momento, se rechaza con `RPT-014`: hay que registrar con la consulta normal.

## Errores posibles

| Código | HTTP | Cuándo |
|---|---|---|
| `RPT-002` | 409 | Se registró una empresa con un RUC ya usado (el RUC de una empresa eliminada queda libre). |
| `RPT-010` | 400 | El RUC falla el formato o el dígito verificador. |
| `RPT-014` | 409 | Se pidió registrar como no verificada, pero SUNAT sí responde ahora. |

Como cualquier endpoint de reporting-service: sin un token válido responde `401` con `AUTH-000`; con un cuerpo o parámetros mal formados, `400` con `VALIDATION_ERROR`; si la base de datos del servicio no responde, `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`. Ver el [catálogo de errores comunes](errores.md).
