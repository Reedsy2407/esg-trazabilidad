# Recorrido de demostración

Un recorrido corto para probar el sistema real en unos minutos, con la cuenta de demostración. La cuenta tiene el rol de **personal** (no administra cuentas) y trabaja con datos de demostración: empresas, asociaciones y vecinos ficticios, más una entidad pública usada como ejemplo. Lo que se registre lo verá cualquiera que use la cuenta.

![Lista de empresas, con su estado](01-empresas.jpg)

## Antes de empezar

La primera carga puede tardar hasta un minuto, y a veces dos: los servicios están en un plan gratuito que los duerme tras 15 minutos sin uso. La pantalla «Preparando el sistema» (o el aviso «Despertando servicios…» del inicio de sesión) lo explica; ya se pueden escribir los datos mientras tanto. Ver [Servicio caído o despertando](servicio-caido.md).

## Los pasos

1. **Entrar.** En la pantalla de inicio de sesión, pulsa **Entrar con la cuenta de demostración**. Entra en un clic y abre la lista de **Empresas**. En la barra superior aparecen el correo de la sesión y **Cerrar sesión**.
2. **Ver una empresa.** Abre **MINISTERIO DEL AMBIENTE**. La página muestra su estado (activa y verificada), el resumen del período y los kilos trazados por período. Las empresas inactivas, como las de prueba anuladas, también se listan con su estado.
3. **Abrir el certificado.** En la misma empresa, abre el certificado del **17 al 30 de setiembre de 2026**: kilos trazados (35,30 kg), el cumplimiento de jerarquía **declarado** (85,5 %), el contexto distrital y las descargas en **PDF** y **CSV**. Qué trae cada archivo está en [Certificados en PDF y CSV](certificados-pdf-csv.md). Un certificado nunca se borra: si hay un error se anula con motivo.
4. **Registrar un recojo.** En **Registrar recojo**, elige un vecino de demostración (por ejemplo **Carmen Flores (demo)**), la asociación, la fecha del recojo y el peso en kilos, y confirma. Ten en cuenta que un recojo **no se edita ni se borra**: es un libro de solo agregar (ver [Los recojos son un libro inmutable](recojos-inmutables.md)). Usa la fecha de hoy para no tocar el período del certificado ya emitido.
5. **Mirar lo demás.** En **Vecinos** están los vecinos de demostración con su cronograma; en **Asociaciones**, las asociaciones ficticias y sus certificaciones («vence en menos de 30 días» marca las que están por vencer); en **SIGERSOL**, la cifra declarada que respalda el certificado.
6. **Salir.** Pulsa **Cerrar sesión**. La sesión dura una hora y no se renueva (ver [La sesión](sesion-jwt.md)).

## Lo que la cuenta no puede hacer

El rol de personal no ve **Cuentas del personal** ni puede crear, desactivar o restablecer cuentas: esas acciones son solo de un administrador y el servicio las rechaza con `AUTH-006` aunque se intenten a mano. Ver [Roles y cuentas](roles-y-cuentas.md).
