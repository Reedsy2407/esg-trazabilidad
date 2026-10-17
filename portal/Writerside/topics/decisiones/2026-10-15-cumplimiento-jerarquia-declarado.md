# El cumplimiento de jerarquía es una cifra declarada y obligatoria, sin integración con SIGERSOL

- **Estado:** Implementada (M5: origen del dato; M7: rotulado «declarado» en pantallas, PDF y CSV; `RPT-005` y `RPT-021`).
- **Fecha:** 2026-10-15
- **Dónde se aplica:** reporting-service, registros SIGERSOL y emisión de certificados; [Cumplimiento de jerarquía declarado](sigersol-declarado.md), [Limitaciones conocidas](limitaciones-conocidas.md)
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

El cumplimiento de jerarquía por empresa solo existe en SIGERSOL tras la cuenta de cada empresa, con un acceso con inicio de sesión que no se puede automatizar. No hay una API pública para terceros; una integración real exigiría un convenio con el MINAM. Lo único público es el recuento por distrito de SIGERSOL Municipal, que describe el distrito y no a la empresa.

## Decisión

- El cumplimiento de jerarquía es una cifra **declarada por el operador** de la asociación e ingresada a mano, con dos orígenes: `DECLARED_BY_OPERATOR` («Declarado por el operador») y `REFERENCED_PROOF` («Con comprobante referenciado»).
- Es **obligatoria**: sin un registro que cubra todo el período, emitir responde `RPT-005`.
- Con comprobante referenciado, la referencia es obligatoria (`RPT-021`), y es solo texto.
- Pantallas, PDF y CSV la llaman «Cumplimiento de jerarquía (declarado)» y dicen que la declara el operador, que no es una cifra oficial de SIGERSOL y que la plataforma no la verifica.
- **No hay integración real** con SIGERSOL, y la plataforma **no es oficial del MINAM**.

## Consecuencias

- Un certificado nunca dice que SIGERSOL validó algo: ningún texto usa «verificado por SIGERSOL».
- La referencia a un comprobante no se puede comprobar públicamente.
- Si algún día hay convenio con el MINAM, el puerto de lectura permitiría cambiar el origen del dato sin tocar las reglas de emisión.

## Alternativas descartadas

- **Llamar «oficial» a la cifra:** falso; nadie la valida.
- **Dejar la cifra opcional:** un certificado sin dato declarado diría más de lo que sabe.
- **Raspar SIGERSOL con un usuario compartido:** frágil, sin autorización y contra sus términos.
