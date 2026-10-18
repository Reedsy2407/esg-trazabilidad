# Exploración de rediseño del frontend: tres direcciones — 2026-10-18

Solo exploración: no se tocó `src/`, el backend, los contratos, las dependencias ni el brief vigente. Todo está en `frontend-demo/design/refs/rediseno-2026-10-18/` (ignorado por git): maquetas HTML/CSS estáticas por dirección (`a/`, `b/`, `c/`, cuatro pantallas cada una), `shots/` (36 capturas: 3 direcciones × 4 pantallas × 1920/1280/360), `motion/` (15 capturas de movimiento: certificado a 4 tiempos y hover por dirección), `contrast.mjs` (cálculo AA) y `index.html` (hoja índice con la comparativa). Datos de DEMO (la lista de empresas, kilos y porcentajes son inventados; cada página lo dice). Para verlo: abrir `index.html`.

Insumos: la auditoría `tasks/auditoria-visual-2026-10-18.md`, `DESIGN-BRIEF.md`, `TOOLING.md`, y la queja de Angel (a 1920 px la columna mide ~790 px y los lados quedan vacíos; menú y subtítulos muy pequeños; sin foco). Fuentes: solo las copias locales que ya existían en `design/refs/direcciones/fonts/` (sin descargar ni instalar nada).

## Qué comparten las tres

- **Ancho:** a ≥1100 px, columna principal + panel lateral con las cifras; contenedor de hasta 1920 px con margen lateral fluido (`clamp(16px, 3.4vw, 72px)`). La columna ya no se queda en ~790 px.
- **Escala:** cuerpo de 18 px (hoy 15), menú 18–19 px, subtítulos 20–22 px, y cifras de ~170 px (kilos) hasta ~480 px (el «05» de B): saltos de 9x a 25x, no de 1.5x.
- **Controles:** todos de 56–60 px de alto (el campo de peso deja de medir distinto), etiquetas de una línea, ayuda en `.hint`, áreas táctiles ≥44 px.
- **Ficha de empresa:** primero lo que importa (cifra grande de kilos y cumplimiento), luego gráfico y certificados; SUNAT y contacto, al final o en el lateral, en forma de lista compacta.
- **Movimiento (CSS puro, todo con `transform`/`clip-path`/`opacity`, sin rebotes ni propiedades de layout):** una entrada orquestada y escalonada por pantalla; cifras que cuentan hacia arriba con `@property` (`--t`, `--r`, `--n` enteros + `@counter-style` con relleno; el valor real va en un `.vh` para lectores de pantalla y la cifra animada es `aria-hidden`); hover con intención en las filas; un efecto con `animation-timeline: scroll()` que no oculta datos; `@view-transition { navigation: auto }` en cada hoja. Con `prefers-reduced-motion: reduce` se anula toda animación y transición (`animation: none`), así que se ve el estado final.
- **Limitación de las pruebas:** AXE no se corrió sobre las maquetas; el contraste es el cálculo WCAG de los pares de tokens (abajo); el movimiento se comprobó con capturas a 0,25/0,7/1,3/2,6 s, no con un e2e. `animation-timeline` solo funciona en Chromium (en el resto, el efecto simplemente no ocurre).

## Dirección A — «Visor y regla» (evolución de Ticket de balanza)

**Resumen.**
1. El ticket pasa de decoración a sistema: el kilo trazado va en un **visor de balanza** oscuro con cifras de ~168 px en Martian Mono que cuentan hacia arriba.
2. El cumplimiento (87,5 %) se dibuja como una **regla de balanza 0–100** con una aguja que barre hasta el valor declarado; la regla es dato, no adorno.
3. La lista de empresas es **un solo rollo de papel térmico** (dentado solo arriba y abajo, filas separadas por guías punteadas, textura de cabezal de impresión de 1 px cada 4 px) junto a un visor lateral con el recuento (05 / 04 activas / 01 inactiva).
4. El certificado sale **de la ranura** por pasos (`steps(24)`), con los kilos en mono de ~130 px; el registro de recojo es un ticket en blanco con el visor de vista previa a la derecha.
5. Paleta y tipografía no cambian (tinta, concreto, papel, cobalto; Archivo + Martian Mono); crece la escala y se ordena la jerarquía.

**Auditoría que resuelve.** P0-1 (ficha: visor primero, SUNAT lateral con acciones como enlaces), P0-2 (columnas fijas, filas de 88–96 px, panel lateral que usa el ancho), P1-1 (un solo rollo, sin festones repetidos), P1-2 (controles de 56 px, etiqueta de una línea), P1-4 (piso de 15 px en ejes, 16–18 px en el resto), P1-5 (enlaces y botones ≥44 px), P2-1 (la ranura del ticket es ahora intencional: barra 14 px más ancha que el papel y sombra interior).

**Contraste AA (calculado):** tinta/papel 15,6; tinta/concreto 11,3; ink-soft/papel 8,0; ink-soft/concreto 5,8; visor-ink/visor 14,4; visor-dim/visor 8,7; cobalto/papel 7,4; cobalto/concreto 5,4; cobalto claro/visor 6,5.

**Costo en Angular: M.** Un shell más ancho, componentes `Visor` y `Regla` (la regla usa `container-type` y `cqw`, sin JS), un `RolloEmpresas` (el talón actual ya existe), el contador como CSS (`@property`), y re-maquetar las cuatro pantallas con la grilla principal+lateral. Sin dependencias; sin fuentes nuevas.
**Tokens nuevos:** `--visor-ink`, `--visor-dim`, `--dots` (guía punteada más oscura: `#7F8B87`), escala `--t-0…--t-5` (18/20/26/40/88/168 con `clamp`), `--gutter`, `--tear: 9px`, `--ease`.
**Riesgos:** el visor oscuro es la primera superficie oscura grande del sistema: el brief prohíbe el «modo oscuro neón», no esto, pero hay que ponerlo por escrito. «Visor» invoca una balanza: el brief veta «balanzas» como entidad de dominio, y aquí es solo vocabulario visual (confirmar). La regla 0–100 puede leerse como una verificación oficial del cumplimiento: se mantiene el texto «(declarado)» y la nota de que no es una cifra oficial. A 360 px el «100» de la regla casi toca la aguja (retoque pendiente).
**Cambio al brief: pequeño** (amplía el «ticket» con visor y regla; añade escala y piso tipográfico).

## Dirección B — «Edición» (editorial, contraste fuerte)

**Resumen.**
1. Un **bloque cobalto a sangre** domina cada pantalla (la columna izquierda en Empresas, la banda del nombre en la ficha, todo el fondo en el certificado); el naranja de chaleco aparece solo en tres sitios: el cumplimiento, el período destacado del gráfico y el botón «Emitir certificado».
2. Tipografía de contraste fuerte: **Big Shoulders Stencil** (el estarcido de las cajas de acopio) solo para rótulos, **Hanken Grotesk** para texto y para **todas las cifras** (~170 px los kilos, ~480 px el recuento). (El estarcido se descartó para los números: un «4» estarcido se leía «4·»; ver Riesgos.)
3. Composición asimétrica: «05» gigante a sangre en el bloque cobalto y la lista como renglones tipográficos de 40 px con hairlines; el hover llena el renglón de cobalto con un barrido `clip-path`.
4. El certificado es un papel blanco de **esquina cortada** sobre el cobalto, que se despliega al abrir; el gráfico usa barras de tinta macizas y la barra destacada en naranja.
5. Sin esquinas redondeadas, sin tarjetas, sin sombras: estructura por filetes y espacio.

**Auditoría que resuelve.** P0-1 (banda de nombre + cifras primero, SUNAT al final en dos columnas), P0-2 (la lista ocupa 7/12 con un bloque de resumen de 5/12), P1-1 (sin talones: renglones con filete), P1-2 (controles de 60 px; el recojo es título + vista previa a la izquierda y formulario ancho a la derecha), P1-4 (ejes de 16 px), P1-5 (≥44 px).

**Contraste AA (calculado):** tinta/papel 16,6; soft/papel 7,4; blanco/cobalto 8,8; cobalto claro/cobalto 6,4; tinta/naranja 6,4; cobalto/papel 7,9. **Ojo:** el naranja sobre el papel es 2,57:1 (no llega al 3:1 de un objeto gráfico): la barra destacada lleva contorno de tinta de 3 px y el foco usa cobalto, no naranja.

**Costo en Angular: L.** Cambia la identidad completa: tokens de color y tipografía, dos fuentes nuevas autoalojadas (Big Shoulders Stencil 600/800 y Hanken Grotesk 400/600/700), un shell con bandas a sangre, el certificado sobre fondo cobalto (una variante de layout), y hay que rehacer las pruebas e2e de contraste y de impresión del ticket.
**Tokens nuevos:** `--cobalt`, `--cobalt-dim`, `--wash`, `--vest`, `--display`, `--text`, escala de 18 a ~330 px, `--gutter`.
**Riesgos:** abandona «Ticket de balanza» (elegido el 2026-10-10) y «una sola tinta»; el estarcido en mayúsculas es una apuesta fuerte y puede cansar o parecer «de obra»; hay que fijar que las cifras nunca van en estarcido; los rótulos pequeños en estarcido (la marca «TRAZABILIDAD ESG» de la barra) pierden legibilidad; el kerning negativo de las cifras grandes (-0,04em) deja la coma pegada al dígito: revisar (en 12,480 se lee, pero justo). El cobalto a pantalla completa en el certificado es lo más llamativo y lo más caro de mantener coherente con un PDF.
**Cambio al brief: grande** (paleta, tipografía, forma, la metáfora firma).

## Dirección C — «Señalética» (apuesta propia)

**Por qué.** (1) El público trabaja en la calle y el acopio de Lima, donde lo que más se lee de lejos son los **paneles de guía verdes de las vías** y las **placas**: tipografía de señalización (Overpass, descendiente de la Highway Gothic), cifras grandes en blanco sobre verde y un RUC como placa. (2) Un panel de guía ya es «una cifra grande con una dirección», que es exactamente un kilo trazado con su empresa: resuelve el foco sin tarjetas de KPI. (3) Es la única de las tres que no deriva de la balanza ni de la edición: su metáfora (orientarse y llegar a un destino) es la de «trazabilidad».

**Resumen.**
1. El **panel de guía** (verde `#0E5A3A`, borde blanco interior, esquinas de 14 px) es la pieza firma: nombre de la empresa, kilos y resumen viven en paneles; el resto es papel plano.
2. El **RUC es una placa** (blanco, borde de tinta, mono) y cada empresa de la lista es un «destino» con su flecha; el hover subraya con una banda amarilla.
3. Barra superior como **pórtico verde** con el ítem activo en una placa amarilla (`#F5C400`); una línea de calzada amarilla discontinua avanza con el scroll.
4. El cumplimiento es una **barra tipo velocímetro** (amarillo sobre verde oscuro, 0–100); el certificado tiene cabecera verde con filete amarillo y un panel de kilos; al abrir, el panel se abre desde el centro y el cuerpo se desdobla.
5. Tipografía: Overpass 400/600/800 y Overpass Mono; formas redondeadas solo en paneles (14 px) y placas (6 px).

**Auditoría que resuelve.** P0-1 (nombre + kilos en paneles primero; SUNAT lateral en una ficha), P0-2 (lista de destinos + panel de resumen), P1-1 (sin festones), P1-2 (controles de 58 px; el formulario con cabecera «Datos del recojo»), P1-4, P1-5 (≥44 px; las flechas de 52 px).

**Contraste AA (calculado):** tinta/fondo 14,5; soft/fondo 6,4; blanco/verde 8,3; verde claro/verde 6,3; tinta/amarillo 10,3; verde/fondo (enlace) 7,1; amarillo/verde oscuro 7,3.

**Costo en Angular: M.** Tokens y shell nuevos, componentes `Panel` y `Placa`, la flecha de destino como CSS; dos fuentes nuevas (Overpass, Overpass Mono). Sin otras dependencias.
**Tokens nuevos:** `--green`, `--green-deep`, `--green-dim`, `--yellow`, `--r-sign: 14px`, `--r-plate: 6px`, escala de 18 a ~190 px.
**Riesgos:** **el verde pasa a ser el color de marca, pero el brief lo reserva a «VIGENTE»** (estados de certificación de asociaciones): habría que cambiar ese estado de color (el estado ya lleva marca y palabra) o aceptar la ambigüedad. Introduce esquinas redondeadas y borde+radio repetido en muchas superficies, lo que se acerca al patrón «caja con borde y radio en todo» que el brief rechaza. Una «placa» es vocabulario vial: el brief veta «placas» como dato de dominio (de vehículos); aquí es solo una forma, pero hay que confirmarlo con Angel. Es la menos específica del dominio ESG/reciclaje.
**Cambio al brief: grande** (paleta, forma, la metáfora firma, color reservado).

## Revisión de slop (sub-agente aislado, método de TOOLING.md) y «¿se ve plano?»

Resultado de la revisión manual, con mis correcciones sobre su informe:
- **Sin patrones de slop duros en las tres:** sin degradados de color, sin glassmorphism, sin emojis, sin KPI de tarjetas con icono, sin avatares ni numeración decorativa (los «05/04/01» son datos), sin eyebrows en mayúsculas, sin `→` añadidos. Los `repeating-linear-gradient` de A son (a) textura de cabezal de impresión sin función —**prescindible**— y (b) la regla y la barra rayada, que sí son datos.
- **Plano:** NO en Empresas, Ficha y Certificado de las tres. **Recojo salió «plano» en las tres** (formulario blanco estándar con una vista previa): lo rehice antes de entregar (A: formulario como ticket dentado y visor con «0.00»; B: título y vista previa a la izquierda con el formulario ancho a la derecha; C: cabecera verde del formulario y la fecha como placa). El revisor no vio estas versiones; las miré yo en captura. Siguen siendo las pantallas de menos carácter, a propósito: el formulario es la superficie de trabajo.
- **Defectos que sí encontró y corregí:** cifra ambigua en B con el estarcido (se pasó a Hanken para las cifras), la coma pegada al dígito (kerning más suave), el desborde del período a 360 px, la flecha del hover de B recortada, el «–» gigante del vacío (ahora «0.00» atenuado), el «kg» huérfano en los distritos (sin salto en `kg`).
- **Pendientes que no corregí:** el «100» de la regla de A casi toca la aguja a 360 px; la marca estarcida pequeña de B pierde legibilidad (ya está más separada pero sigue siendo estarcido a 31 px); el menú de C ocupa tres filas a 360; el cero con punto de Overpass Mono se parece a «O» en «05».
- **Limitación del revisor:** declaró no haber leído `TOOLING.md` ni el `SKILL.md` de Impeccable, así que el método degradado se aplicó solo a medias. Afirmó que el brief veta las «placas»: es una mala lectura (el brief veta placas **como datos de dominio** — vehículos —, no como forma visual), pero lo dejo como riesgo a confirmar. Su ranking: más carácter B > A > C; menos riesgo A > B > C. Coincide con mi lectura.

## Comparativa

| | A · Visor y regla | B · Edición | C · Señalética |
|---|---|---|---|
| Carácter | Alto, ya conocido | Muy alto | Alto, temático |
| Uso del ancho a 1920 | Principal + visor lateral | Bloques a sangre asimétricos | Principal + panel lateral |
| Salto tipográfico (cifra/cuerpo) | ~9x (kilos) a ~12x (recuento) | ~9x (kilos) a ~27x (recuento) | ~10x |
| Cercanía al dominio ESG/reciclaje | Alta (balanza, ticket) | Media (estarcido de acopio) | Media-baja (vías) |
| Costo Angular | M | L | M |
| Fuentes nuevas | 0 | 2 | 2 |
| Cambio al brief | Pequeño | Grande | Grande |
| Riesgo principal | Superficie oscura grande; vocabulario «balanza» | Rompe la identidad elegida el 10-10 y «una sola tinta» | Verde = «vigente»; radio y borde en todo |

## Recomendación: A, «Visor y regla»

La recomiendo por tres razones. (1) **Es la que contesta la queja de Angel sin tirar lo ya decidido:** usa todo el ancho (principal + visor), pasa de 15 a 18 px, da un foco claro por pantalla (la cifra en el visor) y hace que las cifras sean protagonistas, y lo hace con la identidad que el dueño eligió el 2026-10-10 y que ya está en producción. (2) **Es la única que no exige fuentes nuevas, paleta nueva ni cambiar el color de «vigente»**, y por eso su costo es M, con riesgos acotados. (3) **Su carácter viene del dominio, no de un estilo importado:** la balanza, el ticket y la regla son lo que hace el público; B y C, aunque más llamativas, se podrían aplicar a otro sistema cambiando el logo con más facilidad.

Lo que A no resuelve del todo, y se puede tomar de las otras sin cambiar la dirección: la escala de B (más grande) para la cifra del certificado, y la regla de que un solo color lleva el énfasis. Si Angel prefiere más audacia aun a costa de reescribir el brief, la siguiente es B (con las cifras en grotesca, no en estarcido), no C.

**Antes de implementarla:** `frontend-demo` ya está en el tope de 30 commits (CLAUDE.md, «Límite de commits»): hay que avisar a Angel antes de pasarlo. Y la decisión de cuál dirección sigue es de Angel; este documento no toca el brief ni el código.
