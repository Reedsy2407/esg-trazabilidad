# Los recojos son un libro inmutable

<primary-label ref="collection"/>

Los recojos forman un **libro de solo agregar** (append-only): se crean y se consultan; **no se editan ni se borran**. No existe un endpoint para modificar o eliminar un recojo.

## Por qué

Los certificados se calculan sumando recojos y se emiten con cifras congeladas. Si un recojo pudiera cambiar o desaparecer, el certificado ya emitido dejaría de coincidir con su respaldo. Un libro que solo crece mantiene el rastro auditable.

## Cómo se corrige un error

| Situación | Qué se hace |
|---|---|
| Se atribuyó el recojo al vecino equivocado | Se desactiva al vecino equivocado ([Desactivar](collection-desactivar-vecino.md)): no recibirá más recojos. El recojo correcto se registra en el vecino correcto. |
| Faltaron kilos por registrar | Se registra un recojo nuevo ([Registrar](collection-registrar-recojo.md)). |
| Un certificado ya emitido usó el dato erróneo | Se [anula el certificado](reporting-anular-certificado.md) y se emite el correcto. |

**Límite:** el libro no permite restar kilos ya registrados (el peso siempre es mayor que 0 y no existe anular un recojo). Un recojo de más sigue en el libro, y los certificados que se emitan después de anular el erróneo lo seguirán sumando.

## Un vecino con recojos solo se desactiva

[Eliminar un vecino](collection-eliminar-vecino.md) solo se permite si **no tiene ningún recojo**; con al menos uno responde `COL-011` y la salida es desactivarlo. Desactivarlo no toca sus recojos: conserva el historial y solo impide nuevos recojos y cronogramas activos (`COL-013`).

## Dónde se hace cumplir

En el servicio: los recojos solo tienen operaciones de crear, listar y ver ([Registrar](collection-registrar-recojo.md), [Listar](collection-listar-recojos.md), [Ver](collection-ver-recojo.md)).

Al guardarse, cada recojo se publica como evento y reporting-service lo guarda en **su propio** libro, con el distrito del vecino en ese momento. Los certificados leen ese libro.
