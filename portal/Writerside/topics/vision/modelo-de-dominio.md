# Modelo de dominio

Las entidades de negocio y cómo se relacionan. Cada servicio guarda las suyas; las flechas punteadas entre servicios **no son llaves foráneas**: un servicio no consulta a otro, se comunican por eventos y por identificadores.

<code-block lang="plantuml" src="../../puml/modelo-dominio.puml"/>

## Cómo leerlo

- Una **asociación** agrupa a sus **recicladores** y sus **certificaciones**. Cuando una certificación vence sin renovarse, collection-service bloquea nuevos recojos de esa asociación.
- Un **vecino** tiene **cronogramas** y **recojos**. El recojo nombra a la asociación que recoge, pero collection-service no valida que exista.
- Una **empresa** se vincula a **una** asociación; sus **certificados** suman los recojos de esa asociación en un período, más el **registro SIGERSOL** declarado que cubre ese período.
- Un **certificado** copia congeladas sus cifras al emitirse.
- La **cuenta del personal** pertenece a auth-service y no se relaciona con las demás entidades: el resto solo recibe su identificador y su correo en el token.

Los estados de cada entidad están en el [glosario de estados](glosario.md).
