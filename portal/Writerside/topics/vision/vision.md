# Visión del producto

Esta sección explica qué hace la plataforma y las reglas de negocio que la sostienen, en lenguaje de negocio. Cada página dice si una regla está en el servicio (el código la hace cumplir) o solo en la consola.

- [Visión, servicios y flujo de punta a punta](vision-y-servicios.md): para qué existe, los cuatro servicios y el camino de un kilo hasta el certificado.
- [Modelo de dominio](modelo-de-dominio.md): las entidades y cómo se relacionan.
- [Estados de empresa independientes](empresas-estados-independientes.md): activa o inactiva, verificada o no verificada, y qué permite cada combinación.
- [El cronograma del vecino](cronogramas-del-vecino.md): qué es, uno activo por día y su ciclo.
- [Los recojos son un libro inmutable](recojos-inmutables.md): no se editan ni se borran.
- [Eliminar una empresa: con o sin certificados](eliminar-empresa.md): con certificados se desactiva; sin ellos se elimina.
- [Cumplimiento de jerarquía declarado (SIGERSOL)](sigersol-declarado.md): una cifra declarada y obligatoria, sin integración real.
- [Certificados en PDF y CSV](certificados-pdf-csv.md): qué trae cada archivo.
- [La consola del personal](consola-del-personal.md): qué pantalla usa cada endpoint, las reglas que viven solo en la consola y sus límites.
- [Roles y cuentas](roles-y-cuentas.md): ADMIN y STAFF.
- [Política de contraseñas](politica-contrasenas.md): NIST 800-63B.
- [La sesión: un token de 1 hora](sesion-jwt.md): sin estado y sin revocación.
- [Recuperar la contraseña en dos pasos](recuperar-contrasena.md): código, luego token.
- [Correo por HTTPS con Google Apps Script](correo-apps-script.md): por qué no SMTP.
- [Servicio caído o despertando](servicio-caido.md): el 503 y el arranque en frío.
- [Operación y seguridad](operacion-y-seguridad.md): despliegue, ramas de la base de datos, pruebas y gitleaks.
- [Despliegue paso a paso](despliegue-paso-a-paso.md): Neon, CloudAMQP, Render, variables, primer despliegue y comprobación.
- [Lista de seguridad: los 20 puntos](lista-de-seguridad.md): estado de cada control y las decisiones tomadas a propósito.
- [Rendimiento](rendimiento.md): qué se midió, qué cambió y qué se dejó igual.
- [Limitaciones conocidas](limitaciones-conocidas.md): lo que la plataforma no hace, a propósito.
