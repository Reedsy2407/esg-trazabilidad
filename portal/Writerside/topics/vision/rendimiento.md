# Rendimiento: qué se midió y qué cambió

El plan gratuito de Render da a cada servicio 0,1 CPU y 512 MB, y lo duerme tras 15 minutos sin tráfico. Casi todo lo que parece «la aplicación es lenta» es ese plan, no el código. Esta página dice qué se midió, qué se cambió por eso y qué se dejó como estaba a propósito.

## Qué registran los servicios

Cada servicio escribe líneas con forma fija (formato logfmt) y sin datos personales: sin identificadores, correos, tokens, cadenas de consulta ni valores enlazados. Con ellas se puede resumir, por servicio, método y ruta (plantilla de la ruta), la cantidad de llamadas, el p50, el p95, el máximo y el tiempo medio de base de datos.

| Evento | Nivel | Campos |
|---|---|---|
| `event=http_request` | INFO; ERROR si la respuesta es 5xx o escapó una excepción | `service method route status durationMs dbMs poolMs dbQueries` (`route` es la plantilla de la ruta, `UNMATCHED` si ningún controlador la atendió) |
| `event=slow_query` | INFO | `service durationMs sql` (solo la forma de la sentencia: literales y números pasan a `?`, 160 caracteres como máximo). Umbral configurable, 150 ms por defecto |
| `event=password_hash` | INFO | `service durationMs` (auth-service, el paso de BCrypt de un inicio de sesión) |
| `event=warmup` | INFO; ERROR si falló un paso | `service step durationMs` |

Las sondas de salud (`/actuator/**`) no se registran.

## Mediciones (antes y después)

En local, con los mismos límites que Render (0,1 CPU, 512 MB, las banderas de la JVM de producción) y PostgreSQL en un contenedor local, así que la latencia de red de la base no está en estas cifras (ver «Producción»).

| Qué | Antes | Después | Por qué |
|---|---|---|---|
| reporting: primer `GET /tracked-companies` tras arrancar | 2.317 ms (base 90 ms) | 340 ms | el calentamiento recorre el camino de la petición, la cadena del JWT y la primera consulta de Hibernate una vez |
| reporting: la misma petición, en caliente | 70 a 170 ms | 70 a 170 ms | sin cambio (2 consultas de 2 a 3 ms con 200 filas) |
| auth: primer inicio de sesión tras arrancar | 2.071 ms | 565 ms | el calentamiento hace un inicio de sesión con una dirección sin cuenta: una comparación BCrypt y la búsqueda por correo |
| auth: inicios de sesión siguientes | 680 ms | 600 ms | BCrypt con fuerza 10 toma de 500 a 600 ms a 0,1 CPU; se quitó la transacción que retenía una conexión del pool durante esa comparación |
| arranque hasta que responde la sonda de vida | 71 s (auth), 110 s (reporting) | 66 s con inicialización perezosa, descartada | limitado por CPU a 0,1 CPU; un 7 % no vale el riesgo |

El calentamiento cuesta unos 5 a 6 segundos tras quedar listo el servicio, en su propio hilo, así que la disponibilidad nunca lo espera.

### Producción (solo endpoints públicos, desde Lima)

| Qué | Tiempo |
|---|---|
| Sonda de vida de un servicio despierto | 0,21 a 0,23 s (es la red: Lima a Render, más TLS) |
| `/actuator/health` agregado (incluye comprobar la base) | 0,25 a 0,31 s |
| Inicio de sesión con un correo desconocido (una consulta), en caliente | 0,28 s |
| Lo mismo, el primero tras un rato inactivo | 2,1 s |
| Un servicio dormido | sin respuesta en 90 s la primera vez y de 15 a 35 s más la segunda: de 105 a 125 s en total |

Lectura: la base no está lejos (una consulta suma unos 60 ms), así que Render y Neon no están en continentes distintos. Los 2,1 s de la primera llamada tras un rato son el camino en frío (JIT, la primera conexión del pool y Neon reanudando su cómputo tras 5 minutos inactivo); el calentamiento ya cubre los dos primeros, y `dbMs` y `poolMs` de la primera petición tras un rato dirán si el tercero importa.

## Se cambió porque los datos lo pedían

- **Calentamiento al arrancar** (activo en las imágenes Docker, apagado en las pruebas): la salud del propio servicio, una petición con un token falso (decodificador del JWT y cadena de seguridad) y una tarea por servicio.
- **El inicio de sesión de auth-service ya no es transaccional:** retenía una conexión del pool durante la comparación BCrypt.
- **La pantalla de inicio de sesión sondea en segundo plano los cuatro servicios** y dice «Despertando servicios…» si tardan más de 1,5 s. El formulario funciona mientras tanto. (Entrar directo a `/login` se saltaba la pantalla de entrada que los despierta.)

## Se dejó como estaba, a propósito

- **BCrypt con fuerza 10.** Bajarla a 8 dejaría un inicio de sesión en 0,1 a 0,2 s a 0,1 CPU, pero 10 es el mínimo que recomienda OWASP y las contraseñas protegen la emisión de certificados.
- **Tamaño del pool, índices, forma de las consultas.** Las consultas en caliente toman de 2 a 3 ms; nada en los datos apunta a ellas. El evento `slow_query` lo mostrará si cambia con volumen real.
- **Regiones.** Render está en Oregón y la base a unos 60 ms de ahí. Pasar a Virginia ahorraría quizá 80 ms por petición a los usuarios de Lima, pero exige recrear los cuatro servicios y la base.
- **Inicialización perezosa:** arranque 7 % más rápido, descartada.
- **Mantener los servicios despiertos.** Un ping programado quitaría la espera de 2 minutos, pero las 750 horas mensuales del plan gratuito no cubren cuatro servicios las 24 horas. Es una decisión de plan y de horas, no de código.

## Sin medir

Las peticiones autenticadas en producción (registro de empresas, las dos listas): no hay credenciales para medirlas desde fuera. Con los registros anteriores, basta resumir el log de Render tras una sesión normal y mirar el `p95` y el tiempo medio de base por ruta. El registro de una empresa además espera a SUNAT (Decolecta) cuando el RUC no está en la caché de 24 horas; ese tiempo queda fuera de este sistema.
