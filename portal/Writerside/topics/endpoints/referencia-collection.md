# collection-service: vecinos, cronogramas y recojos

<primary-label ref="collection"/>

Los vecinos que entregan residuos, el cronograma de cada uno y el libro de recojos (kilos por fecha y asociación).

## Cómo se usa

- **Autenticación:** todos los endpoints, salvo los públicos de auth-service, piden `Authorization: Bearer <token>` con el token que entrega [Iniciar sesión](auth-iniciar-sesion.md).
- **Errores:** siempre un cuerpo `application/problem+json` con `status`, `detail` y un `code` estable (`AUTH-011`, `RPT-005`…). La pantalla y los clientes deben decidir por el `code`, nunca por el texto. Ver el [catálogo de errores](errores-collection.md).
- **Servicio caído:** si la base de datos no responde, el servicio contesta `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`; ver [Servicio caído o despertando](servicio-caido.md).
- Cada recojo registrado se publica como evento (`CollectionRegisteredEvent`, con el distrito del vecino) para que reporting-service lo sume a los certificados.
- No depende de recycler-service: no valida que la asociación exista; sabe si está bloqueada por un evento.
- Las empresas de `/companies` son de este servicio y **no** son las empresas de los certificados.

## Endpoints

| Operación | Método y ruta | Quién | Estabilidad |
|---|---|---|---|
| [Listar vecinos](collection-listar-vecinos.md) | `GET /neighbors` | Personal con sesión | Estable |
| [Registrar un vecino](collection-registrar-vecino.md) | `POST /neighbors` | Personal con sesión | Estable |
| [Ver un vecino](collection-ver-vecino.md) | `GET /neighbors/{id}` | Personal con sesión | Estable |
| [Editar un vecino](collection-editar-vecino.md) | `PATCH /neighbors/{id}` | Personal con sesión | Estable |
| [Desactivar un vecino](collection-desactivar-vecino.md) | `PATCH /neighbors/{id}/deactivate` | Personal con sesión | Estable |
| [Reactivar un vecino](collection-activar-vecino.md) | `PATCH /neighbors/{id}/activate` | Personal con sesión | Estable |
| [Eliminar un vecino](collection-eliminar-vecino.md) | `DELETE /neighbors/{id}` | Personal con sesión | Estable |
| [Ver la bitácora de estados de un vecino](collection-bitacora-vecino.md) | `GET /neighbors/{id}/status-changes` | Personal con sesión | Estable |
| [Listar los cronogramas de un vecino](collection-listar-cronogramas.md) | `GET /neighbors/{neighborId}/schedules` | Personal con sesión | Estable |
| [Crear un cronograma](collection-crear-cronograma.md) | `POST /neighbors/{neighborId}/schedules` | Personal con sesión | Estable |
| [Ver un cronograma](collection-ver-cronograma.md) | `GET /neighbors/{neighborId}/schedules/{id}` | Personal con sesión | Estable |
| [Pausar un cronograma](collection-pausar-cronograma.md) | `PATCH /neighbors/{neighborId}/schedules/{id}/pause` | Personal con sesión | Estable |
| [Cancelar un cronograma](collection-cancelar-cronograma.md) | `PATCH /neighbors/{neighborId}/schedules/{id}/cancel` | Personal con sesión | Estable |
| [Reactivar un cronograma](collection-reactivar-cronograma.md) | `PATCH /neighbors/{neighborId}/schedules/{id}/reactivate` | Personal con sesión | Estable |
| [Registrar un recojo](collection-registrar-recojo.md) | `POST /neighbors/{neighborId}/collection-records` | Personal con sesión | Estable |
| [Listar los recojos de un vecino](collection-listar-recojos.md) | `GET /neighbors/{neighborId}/collection-records` | Personal con sesión | Estable |
| [Ver un recojo](collection-ver-recojo.md) | `GET /neighbors/{neighborId}/collection-records/{id}` | Personal con sesión | Estable |
| [Listar empresas (collection-service)](collection-listar-empresas.md) | `GET /companies` | Personal con sesión | Interno |
| [Registrar una empresa (collection-service)](collection-crear-empresa.md) | `POST /companies` | Personal con sesión | Interno |
| [Ver una empresa (collection-service)](collection-ver-empresa.md) | `GET /companies/{id}` | Personal con sesión | Interno |

## Etiquetas de estabilidad

**Estable:** en uso por la consola, cubierto por pruebas de integración y sin cambios previstos. **Reciente:** añadido o cambiado entre M7 y M10 (cuentas y roles, recuperación de contraseña); puede ajustarse. **Interno:** existe pero la consola no lo usa.
