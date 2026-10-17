# Eliminar una empresa: con o sin certificados

<primary-label ref="reporting"/>

Para eliminar una empresa de reporting-service hay una sola pregunta: **¿tiene certificados?**

| La empresa… | Qué responde [Eliminar](reporting-eliminar-empresa.md) | Qué hacer |
|---|---|---|
| Tiene al menos un certificado, **vigente o anulado** | `409` `RPT-017` | [Desactivarla](reporting-desactivar-empresa.md): deja de recibir certificados y conserva su historial. |
| No tiene certificados | `204`, sin cuerpo | Nada más: la empresa desaparece y **su RUC queda libre** para registrarse de nuevo. |

## Por qué los anulados también cuentan

Un certificado anulado sigue existiendo (nunca se borra) y su historial incluye quién lo anuló, cuándo y por qué. Eliminar la empresa borraría ese rastro.

## Motivo obligatorio

Eliminar exige un motivo (hasta 500 caracteres), igual que desactivar. Quién lo hizo y cuándo los toma el servicio del token y del reloj; la persona no los escribe.

## Otras consecuencias de tener certificados

La misma regla protege dos datos de la empresa: con certificados no se puede cambiar su **RUC** ni su **asociación** (`RPT-017`), porque cambiarlos haría que sus certificados hablaran de otra cosa.

## Para corregir una empresa con datos equivocados

1. [Anular](reporting-anular-certificado.md) los certificados erróneos.
2. [Desactivar](reporting-desactivar-empresa.md) la empresa.
3. [Registrar](reporting-registrar-empresa.md) la correcta.
4. [Emitir](reporting-emitir-certificado.md) de nuevo.

La consola oculta el botón «Eliminar empresa» cuando hay certificados y explica el motivo; el servicio igual responde `RPT-017` si apareciera uno mientras tanto.
