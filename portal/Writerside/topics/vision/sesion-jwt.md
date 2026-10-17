# La sesión: un token de 1 hora

<primary-label ref="auth"/>

Al [iniciar sesión](auth-iniciar-sesion.md), auth-service entrega un **token JWT** firmado con HS256. Es toda la «sesión»: no hay cookie ni sesión guardada en el servidor.

| Característica | Valor |
|---|---|
| Duración | **1 hora**, fija |
| Renovación | **Ninguna**: no hay token de refresco; al vencer, la persona inicia sesión de nuevo |
| Contenido | Id de la cuenta, correo, rol y, mientras la persona deba elegir su contraseña, `pwdChange: true` |
| Quién lo valida | Los **cuatro** servicios, por firma y vencimiento, **sin llamar a auth-service** |
| Dónde lo guarda la consola | En el `sessionStorage` de la pestaña, con una política de seguridad de contenido (CSP) que limita qué código puede leerlo |

## Por qué sin estado

Los servicios del plan gratuito de Render **se duermen** tras un rato sin tráfico. Si cada llamada tuviera que consultar a auth-service, despertar uno exigiría despertar a todos y una caída de auth-service tumbaría el resto. Validando por firma, cada servicio decide solo.

## Sin revocación (limitación aceptada)

Como nadie consulta a auth-service, **un token ya emitido sigue valiendo hasta que vence**:

- tras **cambiar** o **restablecer** una contraseña,
- tras **desactivar** una cuenta,
- tras **recuperar** la contraseña (la sesión anterior sigue viva hasta 1 hora).

La excepción son las **acciones de ADMIN**: auth-service lee la base de datos, así que un ADMIN desactivado pierde su poder al instante aunque su token valga. La consola lo dice en las pantallas de desactivar y restablecer: «un token ya emitido sigue valiendo hasta que vence (como máximo 1 hora)».

Es una decisión de Angel (2026-10-15), documentada y aceptada: ver [JWT sin estado y sin revocación](2026-10-15-jwt-sin-estado-sin-revocacion.md) y [Limitaciones conocidas](limitaciones-conocidas.md).

## Lo que ve la persona

«Mi sesión» muestra «Tu sesión vence a las HH:MM» (hora de Lima) y la duración, leídas del propio token (`exp` e `iat`). Si un servicio rechaza el token (`AUTH-000`), la consola cierra la sesión y lleva al inicio de sesión con un aviso.
