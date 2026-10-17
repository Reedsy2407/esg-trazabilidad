# Servicio caído o despertando

El caso «un servicio no responde» tiene dos causas y la plataforma las trata con la misma frase, sin pantallas rotas ni mensajes técnicos.

## Arranque en frío (el caso normal)

Los servicios del plan gratuito de Render se **duermen tras 15 minutos sin tráfico**. La primera petición tras ese rato espera a que arranque el contenedor y Spring Boot: se midieron **95 a 126 segundos**. Mientras el servicio despierta, el proxy de Render responde `502`, `503` o `504`. No es una falla: la pantalla «Preparando el sistema» despierta los cuatro servicios al abrir el sitio y avisa de cuánto falta. Mientras duerme, los trabajos en segundo plano (como el despachador de eventos) tampoco corren: los eventos esperan en las colas de RabbitMQ hasta que el consumidor despierta, sin pérdida de datos.

## Base de datos no disponible (lo que responde la API)

Si el servicio está despierto pero **su base de datos no responde** (caída, sin conexiones libres o tiempo agotado), responde:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 10
Content-Type: application/problem+json

{ "status": 503, "detail": "El servicio no está disponible en este momento. Inténtalo de nuevo en un momento.", "code": "SERVICE_UNAVAILABLE" }
```

- El mensaje es **fijo**: no incluye el host, el SQL ni el texto del controlador de la base de datos. En el registro solo queda el nombre de la clase de la excepción.
- `Retry-After: 10` es una estimación de cuánto tarda en despertar una base gratuita, no una promesa.
- El código está en el manejador compartido (`shared-kernel`), así que lo responden los cuatro servicios. Es un `503` con código propio y no un `500` crudo, para que un cliente sepa que **puede repetir la petición más tarde**.
- El estado agregado `/actuator/health` pasa a `503` para que un monitor lo vea, pero la sonda de **vida** (`/actuator/health/liveness`) sigue en `UP`: Render no debe reiniciar una JVM sana por una caída del proveedor.

<code-block lang="plantuml" src="../../puml/servicio-caido.puml"/>

## Qué dice la consola

Un solo texto para «servicio caído o despertando» (`502`, `503`, `504` y `SERVICE_UNAVAILABLE`), sin códigos HTTP y con el siguiente paso:

> No se pudieron cargar los vecinos: el servicio de recojos no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo.

- Cualquier **otro** error 5xx dice «tuvo un problema inesperado», también sin códigos.
- Sin respuesta alguna (la conexión falla) dice «Comprueba tu conexión».
- El inicio de sesión y la descarga de PDF y CSV dicen lo mismo de su servicio.
- Las **escrituras** que no obtienen respuesta o reciben un 5xx **nunca dicen que fallaron**: dicen que no se sabe el resultado y cómo comprobarlo antes de reintentar, porque la escritura pudo haberse aplicado. Reintentar a ciegas podría duplicarla.

## Lo que no se hace

- No se reintenta solo (ni el servicio ni la consola): una escritura repetida podría duplicarse.
- Solo se traducen a `503` los fallos de acceso a datos. RabbitMQ no está en el camino de una petición (el despachador de eventos escribe en la base), así que su caída no se ve como un error de la API.

Decisión: [Un servicio sin base de datos responde 503](2026-10-17-servicio-caido-responde-503.md). Ver también [SERVICE_UNAVAILABLE en el catálogo](errores.md).
