# Catálogo de errores

Cada respuesta de error es `application/problem+json` con un `code` estable. Decide siempre por el `code`, nunca por el texto. Hay un catálogo por servicio y uno de errores comunes a todos:

- [Errores de auth-service](errores-auth.md)
- [Errores de recycler-service](errores-recycler.md)
- [Errores de collection-service](errores-collection.md)
- [Errores de reporting-service](errores-reporting.md)

## Errores comunes

Los responde cualquiera de los cuatro servicios (los dos últimos, el código compartido `shared-kernel`).

| Código | HTTP | Respuesta | Causa | Qué hacer | Dónde aparece |
|---|---|---|---|---|---|
| `AUTH-000` | 401 / 403 | Token faltante o inválido / No tienes permiso para esta acción | La petición no trae un token, el token no es válido (firma o vencimiento) o la ruta no admite esa cuenta. | Iniciar sesión de nuevo. Si ya es una sesión recién iniciada, comprobar que el token se envía en `Authorization: Bearer`. |  |
| `VALIDATION_ERROR` | 400 | Detalle por campo, o «El cuerpo de la solicitud no es válido o está mal formado» | Falta un campo obligatorio, un valor no tiene el tipo o formato esperado (un UUID, una fecha) o el JSON está mal formado. | Corregir la petición según el contrato. |  |
| `DATA_CONFLICT` | 409 | La operación viola una restricción de integridad de datos | La base de datos rechazó la operación por una restricción que ninguna regla específica cubre. | Recargar y reintentar; si persiste, es un defecto. |  |
| `SERVICE_UNAVAILABLE` | 503 | El servicio no está disponible en este momento. Inténtalo de nuevo en un momento. | La base de datos no responde (caída, sin conexiones o tiempo agotado). Trae `Retry-After: 10`. No incluye host, SQL ni mensaje del controlador. | Reintentar en unos segundos. Una escritura sin respuesta puede haberse aplicado: comprobar antes de repetirla. |  |

## Cómo se mantiene al día

El script `portal/scripts/catalog-drift.mjs` compara los códigos que declaran los `*Errors.java` con los de estas páginas. El job `catalog-drift` de `portal.yml` solo informa: no frena el despliegue del portal.
