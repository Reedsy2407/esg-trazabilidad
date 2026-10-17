# Las contraseñas se validan según NIST 800-63B: de 15 a 64 caracteres, sin reglas de composición

- **Estado:** Implementada (M7: `PasswordPolicy`, `AUTH-007`).
- **Fecha:** 2026-10-15
- **Dónde se aplica:** auth-service, crear, cambiar, restablecer y recuperar contraseñas; [Política de contraseñas](politica-contrasenas.md), `docs/security.md`
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Con cuentas con distintos permisos (ADMIN y STAFF) la contraseña es el único factor de acceso. Las reglas clásicas (una mayúscula, un número, un símbolo, cambiar cada 90 días) producen contraseñas previsibles; NIST SP 800-63B recomienda longitud, una lista de bloqueo y ninguna regla de composición. BCrypt solo lee los primeros 72 bytes de la contraseña.

## Decisión

- Longitud de **15 a 64 caracteres**; el tope existe por BCrypt y una contraseña más larga se rechaza en vez de recortarse.
- Se rechazan las contraseñas de una **lista de comunes** y las que incluyen el correo de la cuenta; la nueva no puede repetir la actual.
- **Sin reglas de composición** y sin rotación forzada.
- **Normalización NFKC** en todos los puntos donde se lee la contraseña (crear, cambiar, restablecer y entrar).
- Se guarda con BCrypt.
- Un incumplimiento responde `400` `AUTH-007`.

## Consecuencias

- La consola muestra la regla en una frase («Entre 15 y 64 caracteres…») y no una lista de símbolos.
- La lista de comunes es corta: frena lo obvio, no reemplaza a un gestor de contraseñas.

## Alternativas descartadas

- **Reglas de composición y rotación:** NIST las desaconseja.
- **Consultar un corpus de contraseñas filtradas por red:** agrega una dependencia y manda prefijos de hash a un tercero para un puñado de cuentas.
- **Sin tope de longitud:** BCrypt la cortaría en silencio a los 72 bytes.
