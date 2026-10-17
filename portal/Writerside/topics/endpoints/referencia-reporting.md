# reporting-service: empresas, certificados y SIGERSOL

<primary-label ref="reporting"/>

Las empresas a las que se emite el certificado, los certificados ESG (en pantalla, PDF y CSV) y los datos de cumplimiento de jerarquía declarados.

## Cómo se usa

- **Autenticación:** todos los endpoints, salvo los públicos de auth-service, piden `Authorization: Bearer <token>` con el token que entrega [Iniciar sesión](auth-iniciar-sesion.md).
- **Errores:** siempre un cuerpo `application/problem+json` con `status`, `detail` y un `code` estable (`AUTH-011`, `RPT-005`…). La pantalla y los clientes deben decidir por el `code`, nunca por el texto. Ver el [catálogo de errores](errores-reporting.md).
- **Servicio caído:** si la base de datos no responde, el servicio contesta `503` con `SERVICE_UNAVAILABLE` y `Retry-After: 10`; ver [Servicio caído o despertando](servicio-caido.md).
- Consulta a SUNAT por el RUC al registrar una empresa y trae, cuando existe, el contexto público de SIGERSOL Municipal por distrito.
- Un certificado emitido es inmutable: guarda copias congeladas de sus cifras.
- No hay integración real con SIGERSOL: el cumplimiento de jerarquía lo declara el operador. Ver [Cumplimiento de jerarquía declarado](sigersol-declarado.md).

## Endpoints

| Operación | Método y ruta | Quién | Estabilidad |
|---|---|---|---|
| [Listar empresas](reporting-listar-empresas.md) | `GET /tracked-companies` | Personal con sesión | Estable |
| [Registrar una empresa](reporting-registrar-empresa.md) | `POST /tracked-companies` | Personal con sesión | Estable |
| [Registrar una empresa como no verificada](reporting-registrar-empresa-no-verificada.md) | `POST /tracked-companies/unverified` | Personal con sesión | Estable |
| [Ver una empresa](reporting-ver-empresa.md) | `GET /tracked-companies/{id}` | Personal con sesión | Estable |
| [Editar una empresa](reporting-editar-empresa.md) | `PATCH /tracked-companies/{id}` | Personal con sesión | Estable |
| [Desactivar una empresa](reporting-desactivar-empresa.md) | `PATCH /tracked-companies/{id}/deactivate` | Personal con sesión | Estable |
| [Reactivar una empresa](reporting-activar-empresa.md) | `PATCH /tracked-companies/{id}/activate` | Personal con sesión | Estable |
| [Eliminar una empresa](reporting-eliminar-empresa.md) | `DELETE /tracked-companies/{id}` | Personal con sesión | Estable |
| [Verificar con SUNAT](reporting-verificar-empresa-sunat.md) | `POST /tracked-companies/{id}/sunat-refresh` | Personal con sesión | Estable |
| [Ver el resumen de un período](reporting-resumen-periodo.md) | `GET /tracked-companies/{companyId}/certificate-summary` | Personal con sesión | Estable |
| [Listar los certificados de una empresa](reporting-listar-certificados.md) | `GET /tracked-companies/{companyId}/certificates` | Personal con sesión | Estable |
| [Emitir un certificado](reporting-emitir-certificado.md) | `POST /tracked-companies/{companyId}/certificates` | Personal con sesión | Estable |
| [Ver un certificado](reporting-ver-certificado.md) | `GET /tracked-companies/{companyId}/certificates/{id}` | Personal con sesión | Estable |
| [Anular un certificado](reporting-anular-certificado.md) | `POST /tracked-companies/{companyId}/certificates/{id}/annul` | Personal con sesión | Estable |
| [Descargar el PDF de un certificado](reporting-descargar-pdf.md) | `GET /tracked-companies/{companyId}/certificates/{id}/pdf` | Personal con sesión | Estable |
| [Descargar el CSV de un certificado](reporting-descargar-csv.md) | `GET /tracked-companies/{companyId}/certificates/{id}/csv` | Personal con sesión | Estable |
| [Listar los registros SIGERSOL](reporting-listar-sigersol.md) | `GET /sigersol-syncs` | Personal con sesión | Estable |
| [Registrar un dato SIGERSOL declarado](reporting-registrar-sigersol.md) | `POST /sigersol-syncs` | Personal con sesión | Estable |
| [Ver un registro SIGERSOL](reporting-ver-sigersol.md) | `GET /sigersol-syncs/{id}` | Personal con sesión | Estable |

## Etiquetas de estabilidad

**Estable:** en uso por la consola, cubierto por pruebas de integración y sin cambios previstos. **Reciente:** añadido o cambiado entre M7 y M10 (cuentas y roles, recuperación de contraseña); puede ajustarse. **Interno:** existe pero la consola no lo usa.
