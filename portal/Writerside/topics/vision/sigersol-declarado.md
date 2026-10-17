# Cumplimiento de jerarquía declarado (SIGERSOL)

<primary-label ref="reporting"/>

## Qué es esa cifra

El **cumplimiento de jerarquía** es un porcentaje (0 a 100) que describe cómo cumple una asociación con la jerarquía de manejo de residuos. Lo **declara el operador de la asociación** y el personal de %product% lo ingresa a mano con [Registrar un dato SIGERSOL declarado](reporting-registrar-sigersol.md), indicando el período que cubre.

> Esta aplicación **no se conecta con SIGERSOL** ni verifica la cifra. No es una cifra oficial del MINAM. Una integración real exigiría un convenio con el MINAM: no hay una API pública para terceros que dé el dato por empresa.

## Es obligatoria para emitir

Un certificado necesita **un único registro que cubra todo su período**. Si ninguno lo cubre, [emitir](reporting-emitir-certificado.md) responde `409` `RPT-005` y el [resumen](reporting-resumen-periodo.md) muestra la cobertura como `UNVERIFIED` («No verificado»). La consola lo detecta antes de pedir y enlaza al formulario de registro.

Los registros de una misma asociación **no pueden superponerse** (`RPT-006`): cada día de un período tiene una sola cifra declarada.

## Los dos orígenes

| Valor de la API | Etiqueta | Qué exige |
|---|---|---|
| `DECLARED_BY_OPERATOR` | Declarado por el operador | Solo la cifra. |
| `REFERENCED_PROOF` | Con comprobante referenciado | Una referencia escrita (`reference`, `RPT-021` si falta): el código de envío o el PDF «Informe» de la declaración, o el número y la fecha de facturas, boletas o liquidaciones de compra. |

La referencia es **solo texto**: no se sube ningún archivo y nadie la comprueba contra SIGERSOL. El dato queda como «referenciado, no verificado por SIGERSOL». Cada registro guarda además el correo de quien lo ingresó. Los registros anteriores a esta distinción se muestran como «Dato anterior al registro de origen».

## Cómo se rotula

Pantallas, PDF y CSV dicen siempre «Cumplimiento de jerarquía (declarado)» y llevan la nota de que lo declara el operador, no es una cifra oficial de SIGERSOL y esta plataforma no la verifica.

## Contexto distrital (otra cosa)

Aparte de esta cifra, el certificado muestra, por distrito, **conteos públicos de SIGERSOL Municipal** (asociaciones y recicladores, con su año). Esos conteos describen el distrito, no a la empresa ni a sus proveedores, no se usan como validación y quedan congelados al emitir. Ver la decisión [Los datos de SIGERSOL Municipal se guardan como snapshot por año y distrito](2026-10-10-snapshot-sigersol-municipal.md) y [Cumplimiento de jerarquía declarado, no oficial](2026-10-15-cumplimiento-jerarquia-declarado.md).
