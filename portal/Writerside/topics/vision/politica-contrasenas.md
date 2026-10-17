# Política de contraseñas

<primary-label ref="auth"/>

La política sigue **NIST SP 800-63B** para una contraseña que es el único factor de acceso: pide **longitud** y evita las reglas que empujan a elegir contraseñas previsibles.

| Regla | Valor |
|---|---|
| Longitud | **De 15 a 64 caracteres** |
| Contraseñas comunes | Se rechazan las de una **lista de contraseñas comunes** bloqueadas |
| Normalización | **NFKC** antes de guardar y antes de comparar |
| Reglas de composición | **Ninguna**: no se exigen mayúsculas, números ni símbolos, ni se obliga a rotarla |
| Caracteres | Cualquiera, incluidos los espacios (una frase larga sirve) |
| Otras | No puede incluir el correo de la cuenta, ni ser igual a la actual |

Un incumplimiento responde `400` con `AUTH-007`. En la recuperación de contraseña, ese error no gasta el token: se puede escribir otra.

## Por qué así

- **15 a 64 caracteres.** La longitud es lo que más protege. El tope de 64 existe por BCrypt, que solo lee los primeros 72 bytes de la contraseña: una más larga (en UTF-8) se rechaza en vez de recortarse en silencio.
- **Sin reglas de composición.** NIST las desaconseja: producen contraseñas como `Clave123!` y fuerzan cambios por costumbre. La consola dice la regla («Entre 15 y 64 caracteres…») en vez de una lista de símbolos.
- **NFKC.** Se normaliza el texto en todos los puntos donde se lee la contraseña (crear, cambiar, restablecer y entrar), de modo que la misma contraseña escrita con otra distribución de teclado o con una forma Unicode equivalente siga coincidiendo.
- **Lista de comunes corta.** Es una lista corta mantenida con el código, no el corpus completo de contraseñas filtradas: una consulta externa agregaría una dependencia y mandaría prefijos de hash a un tercero para un puñado de cuentas. Sirve para frenar lo obvio, no reemplaza a un gestor de contraseñas.

## Dónde se aplica

| Momento | Endpoint |
|---|---|
| Crear una cuenta (contraseña inicial) | [Crear una cuenta](auth-crear-cuenta.md) |
| Cambiar la propia | [Cambiar mi contraseña](auth-cambiar-mi-contrasena.md) |
| Restablecer por un ADMIN | [Restablecer la contraseña de una cuenta](auth-restablecer-contrasena-cuenta.md) |
| Recuperar con código | [Elegir la contraseña nueva](auth-recuperar-confirmar.md) |

Las contraseñas se guardan con **BCrypt**; nunca se registran ni se devuelven. La única excepción deliberada es la contraseña aleatoria de la primera cuenta ADMIN, que se escribe una sola vez en el registro del servicio (ver [Roles y cuentas](roles-y-cuentas.md)).

Ver la decisión [Política de contraseñas según NIST 800-63B](2026-10-15-politica-de-contrasenas-nist.md).
