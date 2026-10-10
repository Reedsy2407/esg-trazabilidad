# DESIGN-BRIEF — frontend-demo

Única fuente de verdad del diseño. Stitch, Claude Design y Claude Code trabajan contra este archivo.

- **Audiencia:** operadores y personal de asociaciones de recicladores en Lima; uso diario, pantalla de escritorio.
- **Tres adjetivos:** preciso, sobrio, legible.
- **Personalidad:** herramienta de trabajo seria y calmada con el mundo del **ticket de balanza**: el ticket térmico que imprime la balanza del centro de acopio (dirección A, elegida el 2026-10-10 tras la crítica de Impeccable y tres maquetas; ver `refs/direcciones/`, local, no versionado). No es una web de marketing.
- **Elemento firma:** la pantalla del certificado como el ticket del mes: papel térmico con borde dentado, una sola tinta. El título del documento es el h1 ("Certificado de trazabilidad ESG"); debajo, como líneas impresas: empresa, RUC, kilos trazados en grande, cumplimiento, período y emisión con guías punteadas, y el código (UUID completo en mono, con botón Copiar). No lleva sello de estado (el certificado no tiene estado) ni tabla de pesadas (van en el CSV). No existe verificación pública por terceros: nada en la pantalla debe sugerirla.

## Paleta (contraste AA calculado; tokens en `src/styles.css`)

| Token | Hex | Uso |
|---|---|---|
| ink | #1B2422 | la única tinta del ticket: texto, acción primaria, barra superior (15.6:1 sobre paper) |
| ink-soft | #4F5B58 | texto secundario y barras del gráfico (6.9:1 sobre paper, 5.05:1 sobre ground) |
| ground | #D6DBD9 | fondo de página: el piso de concreto del acopio |
| paper | #FCFDFD | papel térmico: tickets, tablas y formularios |
| accent | #2347C5 | cobalto: dónde estás, enlaces y foco (7.4:1 sobre paper) |
| accent-on-ink | #8EA3F0 | cobalto aclarado para la barra oscura: foco y subrayado activo (6.5:1 sobre ink) |
| vigente / por-vencer / vencido | #1A7A48 / #9A5B00 / #B3261E | **solo** estados de certificación de asociaciones; nunca errores ni interfaz |

Bordes de campos: #6F7A77 (4.4:1 sobre paper). Guías punteadas: #A9B2AF. Los errores van en tinta, con un signo dibujado y borde más grueso, no en rojo. Excepción vigente por decisión anterior del dueño: la pantalla "Preparando el sistema" usa verde/ámbar/rojo para el estado de cada servicio.

## Tipografía
Archivo (interfaz, 400/600/800) + Martian Mono (kilos, RUC, fechas, códigos: medición, no disfraz). Autoalojadas en woff2, solo subconjunto latin (`public/fonts`, @fontsource 5.3.0, OFL), con caras de respaldo locales ajustadas (`size-adjust`, `ascent/descent-override`) para que el cambio de fuente no mueva la maquetación. Cifras tabulares en toda la app. Nunca Inter, Roboto, Arial ni la fuente del sistema como voz.

## Forma, iconos y movimiento
- Esquinas rectas: el papel se corta o se rasga, no se redondea. Sin sombras de elevación. Espaciado base 4 px.
- **Borde dentado solo donde la metáfora es literal:** el ticket del login, cada empresa de la lista (talón), el resumen del período y el certificado. Tablas, gráfico y formularios son papel plano. Nunca dos bandas dentadas seguidas.
- Gráfico: barras sólidas en ink-soft; solo el período destacado lleva rayas de impresión y contorno en tinta (las rayas en todas las barras producían aliasing a 360 px y restaban precisión a la altura).
- Controles propios: chevron y calendario dibujados, mismo vocabulario en input, select y fecha; deshabilitado con aspecto de deshabilitado.
- Iconos: un solo juego (Material Symbols outlined) más signos dibujados en SVG, solo donde aportan significado.
- Movimiento: la "impresión" del ticket (revelado por pasos) **una vez** al abrir el login y el certificado; esqueleto de carga y aviso de calentamiento. Nada en tablas. Con `prefers-reduced-motion` todo se reduce a un instante (regla global); la impresión del login y del certificado está probada en e2e.

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
