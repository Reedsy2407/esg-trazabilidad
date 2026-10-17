# Un servicio sin base de datos responde 503 con un código estable, y la consola lo dice en una sola frase

- **Estado:** Implementada (M10: manejador compartido en shared-kernel, `DatabaseDownApiIT`, texto único en el frontend).
- **Fecha:** 2026-10-17
- **Dónde se aplica:** shared-kernel (los cuatro servicios) y consola; [Servicio caído o despertando](servicio-caido.md), `docs/frontend.md`, `docs/security.md`
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Cuando la base de datos no respondía (caída, sin conexiones o tiempo agotado), el servicio contestaba un `500` crudo, sin un código que un cliente pudiera usar. En la consola, los errores de servicios caídos mostraban «(HTTP 503)» y otros códigos técnicos. Además, los servicios del plan gratuito tardan entre 95 y 126 segundos en despertar y mientras tanto Render responde 502, 503 o 504.

## Decisión

- Un fallo de acceso a datos (`DataAccessResourceFailureException`, `TransientDataAccessResourceException`, `QueryTimeoutException`, `CannotCreateTransactionException`) responde **`503`** con el código **`SERVICE_UNAVAILABLE`**, un mensaje fijo y **`Retry-After: 10`**, sin host, SQL ni texto del controlador. El registro lleva solo la clase de la excepción.
- El estado agregado `/actuator/health` pasa a `503` y la sonda de vida sigue en `UP`.
- La consola usa **un solo texto** para «servicio caído o despertando» (502, 503, 504 y `SERVICE_UNAVAILABLE`), **sin códigos HTTP** y con el siguiente paso; otros 5xx dicen «tuvo un problema inesperado».
- Una **escritura** sin respuesta o con un 5xx **nunca dice que falló**: dice que no se sabe el resultado y cómo comprobarlo, porque pudo haberse aplicado.
- No se reintenta solo.

## Consecuencias

- Un cliente distingue «puedo repetir más tarde» de un error de programa.
- Solo se cubren los fallos de acceso a datos; RabbitMQ no está en el camino de una petición.
- El `Retry-After` es una estimación, no una promesa.

## Alternativas descartadas

- **Reintentar solo en el servidor o en la consola:** una escritura repetida podría duplicarse.
- **Dejar el 500:** no dice si repetir sirve ni oculta el detalle interno.
- **Mostrar el código HTTP al usuario:** no le sirve para decidir qué hacer.
