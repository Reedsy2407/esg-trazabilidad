# La sesión es un JWT de 1 hora, sin estado y sin revocación

- **Estado:** Implementada (`JwtIssuer`, validación por firma en shared-kernel).
- **Fecha:** 2026-10-15
- **Dónde se aplica:** auth-service (emisión) y los cuatro servicios (validación); [La sesión](sesion-jwt.md), `docs/security.md`
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Los servicios del plan gratuito de Render se duermen tras 15 minutos sin tráfico. Si cada llamada a recycler, collection o reporting tuviera que consultar a auth-service para saber si un token sigue valiendo, despertar uno exigiría despertar a todos y una caída de auth-service tumbaría el resto.

## Decisión

- auth-service emite un JWT firmado con **HS256** que dura **1 hora** y **no se renueva**.
- Los demás servicios lo validan **solo por firma y vencimiento**, sin llamar a auth-service.
- **No hay revocación.** Decisión de Angel (2026-10-15): se acepta como limitación conocida y documentada.
- Las acciones de ADMIN son la excepción: auth-service lee la base de datos en cada una.

## Consecuencias

- Tras cambiar o restablecer una contraseña, desactivar una cuenta o recuperar una contraseña, un token ya emitido sigue valiendo hasta que vence (máximo 1 hora). La consola lo avisa en esas pantallas.
- Un ADMIN desactivado pierde su poder al instante aunque su token valga.
- Si algún día se necesita revocación real, hace falta un estado compartido (una lista de tokens revocados o sesiones en la base) y los servicios dejarían de validar sin consultar.

## Alternativas descartadas

- **Sesiones en el servidor o consulta a auth-service en cada llamada:** rompe con servicios que duermen.
- **Token de refresco:** más superficie de ataque y más estado para un proyecto de portafolio con un puñado de cuentas.
- **Duración larga (un día):** agranda la ventana de un token robado sin poder revocarlo.
