# recycler-service: asociaciones, recicladores y certificaciones

<primary-label ref="recycler"/>

Las asociaciones de recicladores formalizados, sus recicladores y las certificaciones de cada asociación, con su vencimiento.

## Cómo se usa

- **Autenticación:** todos los endpoints, salvo los públicos de auth-service, piden `Authorization: Bearer <token>` con el token que entrega [Iniciar sesión](auth-iniciar-sesion.md).
- **Errores:** siempre un cuerpo `application/problem+json` con `status`, `detail` y un `code` estable (`AUTH-011`, `RPT-005`…). La pantalla y los clientes deben decidir por el `code`, nunca por el texto. Ver el [catálogo de errores](errores-recycler.md).
- **Servicio caído:** si la base de datos no responde, el servicio contesta `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`; ver [Servicio caído o despertando](servicio-caido.md).
- Cuando una certificación vence sin renovarse, el servicio publica un evento y collection-service bloquea nuevos recojos de esa asociación; al renovarla, la desbloquea.
- Las listas son paginadas (`page`, `size`; máximo 100 por página).

## Endpoints

| Operación | Método y ruta | Quién | Estabilidad |
|---|---|---|---|
| [Listar asociaciones](recycler-listar-asociaciones.md) | `GET /associations` | Personal con sesión | Estable |
| [Registrar una asociación](recycler-crear-asociacion.md) | `POST /associations` | Personal con sesión | Estable |
| [Ver una asociación](recycler-ver-asociacion.md) | `GET /associations/{id}` | Personal con sesión | Estable |
| [Suspender una asociación](recycler-suspender-asociacion.md) | `PATCH /associations/{id}/suspend` | Personal con sesión | Estable |
| [Activar una asociación](recycler-activar-asociacion.md) | `PATCH /associations/{id}/activate` | Personal con sesión | Estable |
| [Listar las certificaciones de una asociación](recycler-listar-certificaciones.md) | `GET /associations/{associationId}/certifications` | Personal con sesión | Estable |
| [Registrar una certificación](recycler-crear-certificacion.md) | `POST /associations/{associationId}/certifications` | Personal con sesión | Estable |
| [Ver una certificación](recycler-ver-certificacion.md) | `GET /associations/{associationId}/certifications/{id}` | Personal con sesión | Estable |
| [Renovar una certificación](recycler-renovar-certificacion.md) | `PATCH /associations/{associationId}/certifications/{id}/renew` | Personal con sesión | Estable |
| [Listar los recicladores de una asociación](recycler-listar-recicladores.md) | `GET /associations/{associationId}/recyclers` | Personal con sesión | Estable |
| [Registrar un reciclador](recycler-crear-reciclador.md) | `POST /associations/{associationId}/recyclers` | Personal con sesión | Estable |
| [Ver un reciclador](recycler-ver-reciclador.md) | `GET /associations/{associationId}/recyclers/{id}` | Personal con sesión | Estable |
| [Activar un reciclador](recycler-activar-reciclador.md) | `PATCH /associations/{associationId}/recyclers/{id}/activate` | Personal con sesión | Estable |
| [Desactivar un reciclador](recycler-desactivar-reciclador.md) | `PATCH /associations/{associationId}/recyclers/{id}/deactivate` | Personal con sesión | Estable |

## Etiquetas de estabilidad

**Estable:** en uso por la consola, cubierto por pruebas de integración y sin cambios previstos. **Reciente:** añadido o cambiado entre M7 y M10 (cuentas y roles, recuperación de contraseña); puede ajustarse. **Interno:** existe pero la consola no lo usa.
