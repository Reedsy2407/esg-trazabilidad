# Roles y cuentas

<primary-label ref="auth"/>

## Dos roles

| Rol | Puede |
|---|---|
| `ADMIN` | Todo lo del personal y, además, administrar cuentas: [listar](auth-listar-cuentas.md), [crear](auth-crear-cuenta.md), [desactivar](auth-desactivar-cuenta.md), [reactivar](auth-reactivar-cuenta.md) y [restablecer la contraseña](auth-restablecer-contrasena-cuenta.md). |
| `STAFF` | Registrar datos, emitir y anular certificados, cambiar su propia contraseña. No ve ni administra cuentas. |

Con solo dos roles, cualquier persona con sesión puede también emitir certificados, cancelar cronogramas y registrar empresas o datos SIGERSOL. Permisos más finos quedan fuera del alcance (ver [Limitaciones conocidas](limitaciones-conocidas.md)).

## La autoridad de ADMIN se verifica en la base de datos

El rol viaja en el token, pero **solo para que la pantalla muestre u oculte opciones**. Los otros tres servicios lo ignoran, y auth-service **no confía en él**: en cada acción de administración lee la cuenta en la base de datos y exige que esté activa y sea ADMIN (`AUTH-006` si no). Por eso un ADMIN desactivado o degradado pierde su poder **al instante**, aunque su token siga vigente.

## El primer ADMIN: `AdminBootstrapRunner`

Si la variable `ADMIN_BOOTSTRAP_EMAIL` está definida, `AdminBootstrapRunner` se asegura al arrancar de que exista un ADMIN:

- Con la tabla de cuentas **vacía**, crea la cuenta de `ADMIN_BOOTSTRAP_EMAIL` con una contraseña aleatoria que escribe **una sola vez** en el registro (es el único lugar donde el sistema escribe una contraseña, porque no hay otro canal para entregarla). La cuenta queda con la contraseña pendiente de cambiar.
- Con cuentas ya existentes y **ningún ADMIN activo**, **promueve** a ADMIN la cuenta de `ADMIN_BOOTSTRAP_EMAIL`, solo si existe y está activa.
- Si ya hay un ADMIN activo, no hace nada.

No existe una variable con la contraseña inicial: deliberadamente.

## Gestión de cuentas

- **Crear:** el ADMIN define una contraseña inicial (la pantalla la genera en el navegador); la cuenta nace activa y con la contraseña pendiente de cambiar.
- **Desactivar y reactivar:** siempre con motivo (hasta 500 caracteres), que queda con quién y cuándo. Un ADMIN no puede desactivar su propia cuenta (`AUTH-009`), así nunca se pierde el último ADMIN activo.
- **Restablecer la contraseña:** un ADMIN fija una contraseña nueva a otra cuenta; la persona la cambia en su siguiente ingreso. Un ADMIN no restablece la suya (`AUTH-009`), pero sí puede restablecer la de otro ADMIN: es una cuenta de plena confianza y la acción queda registrada con ambos ids.
- Tras desactivar o restablecer, **un token ya emitido sigue valiendo hasta vencer** (máximo 1 hora); ver [La sesión](sesion-jwt.md).

## «Mi sesión»

La pantalla muestra a la persona sus datos y **cuándo se cambió su contraseña por última vez** (`passwordChangedAt`, de [Ver mi cuenta](auth-mi-cuenta.md)), cuándo vence su sesión y, si es ADMIN, la lista de cuentas.
