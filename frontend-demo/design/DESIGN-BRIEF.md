# DESIGN-BRIEF — frontend-demo

Única fuente de verdad del diseño. Stitch, Claude Design y Claude Code trabajan contra este archivo.

- **Audiencia:** operadores y personal de asociaciones de recicladores en Lima; uso diario, pantalla de escritorio.
- **Tres adjetivos:** preciso, sobrio, legible.
- **Personalidad:** herramienta de trabajo seria y calmada con el mundo del **ticket de balanza**: el ticket térmico que imprime la balanza del centro de acopio (dirección A, elegida el 2026-10-10 tras la crítica de Impeccable y tres maquetas; ver `refs/direcciones/`, local, no versionado). No es una web de marketing.
- **Dirección vigente (2026-10-18): «Visor y regla»**, evolución de la dirección A. El kilo trazado va en un **visor** de balanza (panel azul petróleo, cifras mono de hasta ~170 px que cuentan hacia arriba); el cumplimiento declarado se dibuja como una **regla 0–100 con aguja**; la lista de empresas es **un solo rollo continuo** de papel térmico; el certificado sale de una ranura. Elegida por el dueño tras comparar tres direcciones (`tasks/rediseno-direcciones-2026-10-18.md`).
- **Elemento firma:** la pantalla del certificado como el ticket del mes: papel térmico con borde dentado, una sola tinta. El título del documento es el h1 ("Certificado de trazabilidad ESG"); debajo, como líneas impresas: empresa, RUC, kilos trazados en grande, cumplimiento, período y emisión con guías punteadas, y el código (UUID completo en mono, con botón Copiar). No lleva sello de estado (el certificado no tiene estado) ni tabla de pesadas (van en el CSV). No existe verificación pública por terceros: nada en la pantalla debe sugerirla.

## Identidad y logo (decisión del 2026-10-18)

El logo oficial es una «T» blanca con la esquina superior derecha doblada y una hoja verde, sobre un cuadrado redondeado azul petróleo. Su archivo de referencia está en `design/brand/logo-trazabilidad-esg-transparente.png`; en la web se usa el redibujo en SVG de `public/brand/` (versión color, monocromo blanco sobre azul y símbolo solo), nunca el PNG.

**Decisión:** el azul petróleo del logo pasa a ser el color dominante de la interfaz (barra superior, visor de cifras, acción primaria, enlaces y foco) y sustituye al cobalto. **Razón:** el logo es lo único que el dueño ya tiene como identidad; un acento distinto (el cobalto) compitiendo con él dividía la marca. **El verde de la hoja no entra en la interfaz**: se reserva, como antes, a «vigente» y «cumplido» (con su verde de texto de la tabla de abajo, que sí pasa AA).

Muestras tomadas del PNG (promedio de 18×18 px): azul `#1A576C`, hoja `#46A972`.

### Reglas de uso
- **Dónde:** barra superior (símbolo + «Trazabilidad ESG»), login (grande, centrado sobre el título), «Preparando el sistema» (la esquina se dobla y la hoja aparece, máx. 1,2 s, estática con `prefers-reduced-motion`), encabezado del certificado (más una marca de agua tenue que no se imprime), favicon y manifest, páginas de error y 404, pie de página sobrio. El avatar (`avatar-trazabilidad-esg.png`) es solo del remitente de correos: no se usa en la web.
- **Espacio libre mínimo** alrededor del símbolo: el ancho de la hoja (≈ 1/6 del lado del cuadrado). **Tamaño mínimo:** 24 px de lado.
- **Solo las versiones de `public/brand/`**: no se estira, no se recolorea, no se le añade sombra ni degradado, no se redibuja la hoja.
- **`alt`:** `alt="Trazabilidad ESG"` cuando el logo es la única identificación (login, 404); `alt=""` cuando va junto al nombre en texto (la barra, el pie) o es decorativo.
- **Elemento firma nuevo: el pliegue.** La esquina doblada de la «T» se repite en los talones, los paneles de cifras y el certificado como un triángulo de pliegue en la esquina superior derecha (`clip-path` + un triángulo del color del fondo). Es sutil, siempre del mismo tamaño relativo, y en hover se pliega un poco más (solo en lo que se abre al pulsarlo). Nunca en todos los elementos de una pantalla a la vez.

## Paleta (contraste AA calculado; tokens en `src/styles.css`)

| Token | Hex | Uso | Contraste |
|---|---|---|---|
| brand | #1A576C | azul petróleo del logo: barra superior, visor de cifras, acción primaria, enlaces, foco | blanco sobre brand 8,0:1; brand sobre paper 7,9:1; brand sobre ground 5,7:1 |
| leaf | #46A972 | la hoja del logo; solo dentro del logo | 2,7:1 sobre brand (marca, exenta de contraste de texto) |
| brand-deep | #123F4F | pulsado/hover de lo que va en brand, pista de la regla | paper sobre brand-deep 11,1:1 |
| brand-soft | #C4DDE6 | texto secundario sobre brand | 5,65:1 sobre brand; 8,0:1 sobre brand-deep |
| brand-light | #9CD5E6 | subrayado activo y foco sobre brand, aguja de la regla | 5,0:1 sobre brand; 7,1:1 sobre brand-deep |
| ink | #1B2422 | texto y títulos | 15,6:1 sobre paper; 11,3:1 sobre ground |
| ink-soft | #46524F | texto secundario | 8,0:1 sobre paper; 5,8:1 sobre ground |
| ground | #D6DBD9 | fondo de página: el piso de concreto del acopio | |
| paper | #FCFDFD | papel térmico: tickets, tablas y formularios | |
| hover | #E3ECEE | fondo de fila al pasar | ink 13,2:1; brand 6,7:1 |
| vigente / por-vencer / vencido | #1A7A48 / #9A5B00 / #B3261E | **solo** estados de certificación de asociaciones y el «cumplido»; nunca errores ni interfaz | vigente sobre paper 5,3:1 |

La hoja del logo (token `--leaf`, `#46A972`, 2,7:1 sobre brand) es parte de la marca y está exenta de contraste de texto; no se usa como color de interfaz. Los archivos de `public/brand/` llevan los hex literales (son recursos estáticos); el logo en línea usa los tokens.

Bordes de campos: #46524F (8,0:1 sobre paper). Guías punteadas: #7F8B87. Los errores van en tinta, con un signo dibujado y borde más grueso, no en rojo. "Preparando el sistema" también va en tinta: marca llena = listo, hueca que respira = despertando, tachada = sin respuesta, siempre con la palabra.

## Tipografía
Archivo (interfaz, 400/600/800) + Martian Mono (kilos, RUC, fechas, códigos: medición, no disfraz). Autoalojadas en woff2, solo subconjunto latin (`public/fonts`, @fontsource 5.3.0, OFL), con caras de respaldo locales ajustadas (`size-adjust`, `ascent/descent-override`) para que el cambio de fuente no mueva la maquetación. Cifras tabulares en toda la app. Nunca Inter, Roboto, Arial ni la fuente del sistema como voz.

**Escala (2026-10-18):** cuerpo de 18 px; menú y enlaces 18 px; subtítulos 20 px; títulos de bloque 26 px; títulos de página `clamp(44px, 4.6vw, 88px)`; cifras de visor hasta 168 px (kilos) y 220 px (recuento). Piso: 15 px para ejes y notas, nada por debajo. Saltos de 3x o más entre cuerpo y título. Controles de 56 px de alto y áreas táctiles de 44 px como mínimo.

## Forma, iconos y movimiento
- Esquinas rectas: el papel se corta o se rasga, no se redondea (el único redondeo es el del logo). Sin sombras de elevación. Espaciado base 4 px.
- **Ancho:** el contenido ocupa hasta 1920 px con márgenes fluidos; a partir de 1100 px va en columna principal + panel lateral con las cifras; a 360 px se apila con el dato clave en la primera pantalla.
- **Textura:** el papel térmico lleva bandas de 1 px cada 4 px a 4 % de opacidad (cabezal de impresión). Es la única textura; no hay degradados.
- **Borde dentado solo donde la metáfora es literal:** el ticket del login, la lista de empresas (un solo rollo, dentado arriba y abajo del rollo, no de cada fila), el resumen del período y el certificado. Tablas, gráfico y formularios son papel plano. Nunca dos bandas dentadas seguidas.
- Gráfico: barras sólidas en ink-soft; solo el período destacado lleva rayas de impresión y contorno en tinta (las rayas en todas las barras producían aliasing a 360 px y restaban precisión a la altura).
- Controles propios: chevron y calendario dibujados, mismo vocabulario en input, select y fecha; deshabilitado con aspecto de deshabilitado.
- Iconos: un solo juego (Material Symbols outlined) más signos dibujados en SVG, solo donde aportan significado.
- **Movimiento (2026-10-18):** una entrada escalonada por pantalla (el papel «sale impreso», por pasos); cifras que cuentan hacia arriba (`@property` + contadores CSS, con el valor real para lectores de pantalla); la aguja de la regla barre hasta su valor; el certificado sale de la ranura; hover con intención en filas (barrido y flecha que avanza) y el pliegue que se dobla un poco más; barra de progreso de scroll con `animation-timeline` donde existe. Solo `transform`, `clip-path` y `opacity`; sin rebotes ni elásticos; nada en tablas. Navegación entre rutas con View Transitions de Angular (`withViewTransitions`, sin dependencias).
- Movimiento anterior: la "impresión" del ticket (revelado por pasos) **una vez** al abrir el login y el certificado; esqueleto de carga y aviso de calentamiento. Nada en tablas. Con `prefers-reduced-motion` todo se reduce a un instante (regla global); la impresión del login y del certificado está probada en e2e.

## Contenido
Español de Perú. Datos plausibles de Lima (distritos, asociaciones inventadas, kilos con 2 decimales). Fechas dd/MM/yyyy en hora de Lima. Estados vacíos con una acción concreta. Nunca "Lorem ipsum", "John Doe" ni saludos tipo "Bienvenido de nuevo".

## Prohibido
Degradados morado/azul y texto con degradado. Glassmorphism y `backdrop-blur`. Cuadrículas de tarjetas idénticas con icono + título + frase. KPI de 3-4 tarjetas con iconos en la portada. `rounded-2xl` + sombra grande en todo. Héroe con formas flotantes. Emojis como iconografía. Numeración decorativa 01/02/03. Avatares de relleno y gráficos inventados. Modo oscuro neón. Centrar todo. Dejar el tema por defecto de Angular Material.

## Revisión anti-slop (antes de dar una pantalla por buena)
1. Prueba de intercambio: con solo cambiar el logo, ¿serviría para otro sistema? Si sí, falta carácter.
2. Prueba de escala de grises: sin color, ¿se entiende la jerarquía?
3. Capturas a 360, 768 y 1280 px.
4. AXE sin violaciones, contraste AA, teclado completo, reduced-motion respetado.
5. Revisión humana contra este brief.

## Datos reales del dominio (OBLIGATORIO; verificado contra los controladores, 2026-10-02)
Las maquetas solo pueden usar estas entidades y campos. Nada más: sin balanzas, taras, manifiestos, guías de remisión, placas, DNI ni encabezados o leyes de entidades del Estado (el sistema NO es oficial).

| Pantalla | Fuente | Campos reales |
|---|---|---|
| Login | `POST /auth/login` | correo electrónico, contraseña. Errores: `AUTH-001` (credenciales), `AUTH-004` (bloqueo temporal con `Retry-After`) |
| Empresas | `GET /tracked-companies` (paginado, 20, orden por nombre) | nombre, RUC, estado (ACTIVE/INACTIVE). No hay filtros todavía |
| Certificados de una empresa | `GET /tracked-companies/{id}/certificates` (paginado, 20, más recientes primero) | empresa, RUC, período (inicio-fin), kilos trazados, % de cumplimiento de jerarquía, fecha de emisión |
| Detalle de certificado | `GET /tracked-companies/{companyId}/certificates/{id}`, `/pdf`, `/csv` | los mismos campos + id (código del certificado, UUID completo) + descargas PDF y CSV. No hay estado ni tabla de pesadas (las pesadas salen en el CSV) |
| Alta de recojo | `POST /neighbors/{id}/collection-records` | asociación (obligatoria), fecha (obligatoria), peso en kg (obligatorio, positivo), cronograma (opcional) |
| Asociaciones | `GET /associations` | nombre, RUC, N.º de registro, dirección, correo, teléfono, estado (ACTIVE/SUSPENDED) |

Reglas añadidas tras la primera ronda de Stitch:
- Fondo: el concreto `ground` #D6DBD9 de la dirección A (antes #F5F6F4, gris neutro; nunca verdoso, menta ni crema).
- Máximo 6 columnas por tabla y UNA línea por fila (sin texto secundario dentro de la celda).
- Máximo 3 niveles de cromo: barra superior, título con su acción, contenido. Sin pestañas, sin franja de resumen, sin pie de estado de hardware.
- Los chips VIGENTE / POR VENCER / VENCIDO solo aplican a certificaciones de asociaciones, no a certificados ESG.

## Ficha de empresa: reglas fijadas tras la ronda 3 de Stitch (2026-10-03)

La captura de Stitch es solo guía de distribución. En la implementación mandan estas reglas:

- Encabezado: marca "Trazabilidad ESG" a la izquierda, correo y "Cerrar sesión" a la derecha, enlace "Volver a empresas", nombre, RUC en mono y estado. SIN sector, SIN ubicación y SIN botones de acción (no existen "Emitir certificado" ni "Descargar historial"; el PDF y el CSV son por certificado, en el detalle).
- Resumen del período: exactamente tres líneas (Período, Kilos trazados, Cumplimiento de jerarquía), como ticket dentado a la izquierda del gráfico; kilos y cumplimiento en mono grande. Sin "Lotes", "Puntos de acopio" ni "Estado de auditoría".
- Gráfica: una sola, 12 períodos, eje Y en 0 / 5,000 / 10,000 / 15,000 kg, sin leyenda, sin sombras, sin animación. Debe tener alternativa accesible (tabla o texto).
- Tabla: columna Período como "Octubre 2024" (no "2024-10 (Octubre 2024)"); kilos y cumplimiento alineados a la derecha en mono; filas de 48 px, clicables, con chevron; pie "Mostrando N de N certificados".
- Prohibido: textos de sello oficial ("histórico formal auditado") y líneas de sincronización inventadas.
