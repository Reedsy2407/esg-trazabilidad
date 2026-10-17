# Endpoints

Una página por operación, escrita para quien necesita entender qué hace el sistema: para qué sirve, quién puede llamarla, un ejemplo de petición y respuesta, las reglas de negocio y los errores posibles. El contrato técnico (parámetros y esquemas) sale de la especificación OpenAPI que cada servicio genera de su propio código.

- [auth-service: acceso del personal](referencia-auth.md): 11 operaciones. Cuentas del personal, roles (ADMIN y STAFF), inicio de sesión, emisión del token (JWT) y recuperación de contraseña por correo.
- [recycler-service: asociaciones, recicladores y certificaciones](referencia-recycler.md): 14 operaciones. Las asociaciones de recicladores formalizados, sus recicladores y las certificaciones de cada asociación, con su vencimiento.
- [collection-service: vecinos, cronogramas y recojos](referencia-collection.md): 20 operaciones. Los vecinos que entregan residuos, el cronograma de cada uno y el libro de recojos (kilos por fecha y asociación).
- [reporting-service: empresas, certificados y SIGERSOL](referencia-reporting.md): 19 operaciones. Las empresas a las que se emite el certificado, los certificados ESG (en pantalla, PDF y CSV) y los datos de cumplimiento de jerarquía declarados.

Todas las rutas de ejemplo usan datos ficticios. Las pantallas de la consola que usan cada endpoint están en `docs/frontend.md` del repositorio.
