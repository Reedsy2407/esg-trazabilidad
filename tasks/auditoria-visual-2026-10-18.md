# Auditoría visual del frontend — 2026-10-18

Solo lectura: no se tocó código, estilos, tests ni configuración. Insumos: `frontend-demo/design/DESIGN-BRIEF.md` (fuente de verdad), `TOOLING.md`, `src/styles.css`, los CSS de las pantallas, 54 capturas Playwright (login, login con error y con validación, empresas lista/vacío/error, empresa lista/vacío/error, certificado y certificado con error, registrar recojo en reposo/validación/error, asociaciones lista/error, asociación, "Preparando el sistema"; cada una a 360, 768 y 1280 px) y la crítica de dos sub-agentes aislados (revisión de diseño y detección manual de patrones genéricos, método de `TOOLING.md`, sin el binario de Impeccable). Las capturas están en `frontend-demo/design/refs/auditoria-2026-10-18/` (ignoradas por git) junto con las medidas de texto y áreas táctiles por pantalla (`metrics-*.json`).

Límites: no hay captura de asociaciones en estado vacío (el backend simulado no tiene opción para listarlas vacías; el vacío de empresas y de la ficha sí). 768 px se miró en login, ficha y certificado: se comporta como 1280 apilado, sin hallazgos propios.

## Resumen: qué lo hace ver plano (por impacto)

1. **La ficha de empresa apila cuatro bloques blancos del mismo peso** (SUNAT, acciones, resumen+gráfico, tabla); el dato que importa (kilos y cumplimiento del último período) empieza a ~590 px de scroll en 1280 y a dos pantallas en 360.
2. **Las listas no tienen escala ni foco a 1280**: dos filas, ~65 % del lienzo vacío, nombre y RUC separados por ~350 px; Asociaciones es una tabla sin ningún gesto de marca.
3. **La metáfora del ticket vive solo en bordes**: el resto es una tinta, un papel y un concreto con un solo evento tipográfico por pantalla (el h1 de 44 px). Sin momentos de énfasis fuera del login y del certificado.
4. **Ruido dentado donde sobra** (talones de Empresas: cuatro hileras de festones para dos filas) y **texto de apoyo pequeño** (10–13 px) que baja el piso de legibilidad justo donde la pantalla ya es gris.
5. **Pulido suelto** que resta precisión: campo "Peso" más alto que el resto, barra de tinta del ticket más ancha que el papel, estado de validación que esconde su propio error.

Veredicto de ambos revisores: **no hay slop genérico**. La prueba de intercambio la pasan el login, el certificado y el talón; la tipografía, el color y la forma cumplen el brief. Lo que se ve plano es jerarquía y ritmo, no plantilla.

## Hallazgos

Esfuerzo: S ≤ 1 h, M ≤ media jornada, L > media jornada. "Brief" indica si hay que cambiar `DESIGN-BRIEF.md`.

### P0

**P0-1. Ficha de empresa: cuatro paneles con el mismo peso, el dato clave queda abajo** (`empresa-1280`, `empresa-360`, `empresa-768`)
- Se ve: tras el título aparecen "SUNAT y contacto" (rejilla de 6 celdas, 3 de ellas "—") y "Acciones de la empresa" (botones + párrafo largo) antes del resumen y del gráfico.
- Por qué es plano: cuatro superficies `paper` idénticas; los metadatos y las acciones raras compiten con lo que se usa a diario. A 360 hay ~1100 px antes del primer kilo.
- Evidencia: `company.page.css` `.panel`, `.sunat .lines` (`repeat(auto-fit, minmax(190px, 1fr))`), `.actions`; captura `empresa-360`.
- Arreglo: orden título → resumen+gráfico → certificados → SUNAT y contacto → acciones (al pie). SUNAT pasa a una lista de definición de dos columnas con guías punteadas (`.dots`, vocabulario del ticket) y oculta los vacíos: una línea "Sin contacto registrado · Editar contacto" en vez de tres celdas "—". Las acciones pasan a `.btn-link` en una fila ("Editar contacto · Desactivar empresa") con el párrafo explicativo en 14 px `ink-soft` solo cuando aplica. Espaciado: `--space-8` entre grupos, `--space-3` dentro.
- Esfuerzo: M. Brief: **desviación parcial y brief flojo**: el brief fija el encabezado y "resumen + gráfico + tabla" pero no dice dónde van SUNAT ni las acciones, ni limita los paneles; añadir "máximo un panel de metadatos y va después de los certificados". También cumple mejor su propia regla de "máximo 3 niveles de cromo".

**P0-2. Listas a 1280 sin escala ni foco: Empresas y Asociaciones** (`empresas-1280`, `empresas-vacio-1280`, `asociaciones-1280`)
- Se ve: contenido de 1056 px con dos filas y un vacío debajo; en Empresas el RUC y el estado flotan lejos del nombre; Asociaciones es una tabla pelada.
- Por qué es plano: el ancho de línea no se corresponde con la cantidad de datos; no hay jerarquía entre nombre (lo importante) y RUC/registro (referencia).
- Evidencia: `companies.page.css` `.stubs`; `.ledger` en `styles.css:451`; `shell.ts` `max-width: 1120px`.
- Arreglo: pistas fijas en el talón (nombre `1fr`, RUC `200px`, estado `140px`, chevron `48px`) y altura de fila 64 px; en `.ledger` de Asociaciones, nombre 17 px/600 y RUC y N.º de registro en mono 13 px `ink-soft`; `.page-head` con `margin-bottom: var(--space-8)`. No añadir contenido inventado para llenar.
- Esfuerzo: S–M. Brief: no cambia (la regla "filas de 48 px" es de la tabla de certificados; precisar que el talón de empresa mide 64 px).
- Nota de calibración: parte del vacío es efecto de tener 2 datos de demo (revisor B). Con 20 filas (página llena) el problema de ancho se reduce; el de jerarquía no.

### P1

**P1-1. Talones de Empresas: festones repetidos** (`empresas-1280`, `empresas-360`)
- Se ve: cada talón lleva borde dentado arriba y abajo, con ~24 px de concreto entre ellos: cuatro hileras de festones para dos filas; a 360 pasa igual.
- Por qué: el brief dice "nunca dos bandas dentadas seguidas" y aquí casi se tocan; la metáfora se vuelve patrón decorativo.
- Evidencia: `.ticket` en `styles.css:170` (máscara arriba y abajo en cada talón); `companies.page.css` separación entre talones.
- Arreglo: dos opciones, a elegir en el prompt de corrección: (a) un solo ticket continuo con dentado arriba del primero y abajo del último y filas separadas por `.dots`; (b) mantener talones pero con `gap: var(--space-8)` para que se lean como tickets distintos. Recomendada (a).
- Esfuerzo: M. Brief: **sí**, reformular "cada empresa de la lista (talón)" a "la lista de empresas, un ticket continuo".

**P1-2. Campo "Peso recolectado" más alto que los demás controles** (`recojo-1280`, `recojo-360`)
- Se ve: el campo mide ~64 px (alto) frente a 46 px de fecha, asociación y vecino; su "kg" va dentro, y la ayuda de abajo deja hueco en la rejilla.
- Por qué: rompe el vocabulario de controles ("46 px, un solo vocabulario") y desalinea la fila.
- Evidencia: `collection-record.page.css` (campo de peso con sufijo); `.control` mide 46 px en `styles.css:266`.
- Arreglo: altura 46 px (el sufijo "kg" absoluto, `padding-right: 48px`); etiquetas con `min-height` para que "Vecino (escribe un nombre…)" de 2 líneas no desplace el input respecto a "Asociación": acortar a "Vecino" y mover la explicación al placeholder o a `.hint`.
- Esfuerzo: S. Brief: desviación.

**P1-3. Validación del recojo: la lista abierta oculta su propio error** (`recojo-validacion-1280`)
- Se ve: al enviar vacío, el foco va al combo de vecino y se despliega la lista de sugerencias, que tapa el mensaje de error de ese campo; los errores de asociación y peso sí se ven.
- Por qué: el estado de error no dice qué hacer en el primer campo. (El revisor B lo reportó también a 360; yo lo verifiqué solo a 1280.)
- Evidencia: capturas `recojo-validacion-*`.
- Arreglo: al enfocar por validación no abrir la lista (abrirla solo con clic o flecha abajo).
- Esfuerzo: S. Brief: no.

**P1-4. Texto de apoyo por debajo de 13 px** (`empresa-1280` gráfico; CSS)
- Se ve: ejes y etiquetas del gráfico a 10 y 11 px (medidos: 18 y 72 caracteres en la ficha); cuatro notas a 12 px.
- Evidencia: `kilos-chart.component.ts:87-88`; `company.page.css:283`, `collection-record.page.css:263`, `neighbor.page.css:140`, `staff-list.page.css:35`; cabeceras `.ledger th` a 13 px (`styles.css:461`).
- Arreglo: piso de 13 px para texto de apoyo y 12 px solo para ticks mono del eje (escalar el SVG para que se mantenga proporcional); 14 px para ayudas. Contraste ya cumple AA.
- Esfuerzo: S. Brief: añadir "piso tipográfico: 13 px, ticks del gráfico 12 px".

**P1-5. Áreas táctiles bajo 44 px en enlaces de texto** (medido a 360)
- Se ve: "Volver a empresas" 23 px de alto, "Volver a Envases…" 23 px, "Registra la primera" 16 px, "Actualizar la lista" 23 px, "¿Olvidaste tu contraseña?" 37 px, pestaña "Todas" 40 px de ancho. La barra de navegación y "Cerrar sesión" sí miden 44.
- Evidencia: `styles.css:361` `.btn-link` con `min-height: auto`, `.back-link` en `styles.css:419`; `metrics-*.json`.
- Arreglo: `.back-link` y los enlaces de los estados vacíos con `display:inline-flex; min-height:44px; align-items:center`; el estado vacío con un solo botón en vez de dos enlaces en la misma frase (ver P2-2).
- Esfuerzo: S. Brief: no (el brief ya pide 44 px en la revisión).

**P1-6. Ficha: tablas con ojo viajero y gráfico más bajo que el resumen** (`empresa-1280`)
- Se ve: las columnas de kilos y cumplimiento quedan a ~400 px del período; a la derecha del gráfico hay un hueco bajo (resumen de 480 px, gráfico de 330 px).
- Arreglo: columnas de cifras con ancho fijo y alineación a la derecha junto a su encabezado, `max-width: 880px` en `.ledger` de certificados o columnas de 160 px; que el gráfico estire a la altura del resumen (`align-items: stretch`).
- Esfuerzo: S. Brief: no.

### P2

**P2-1. Barra de tinta del ticket más ancha que el papel** (`login-1280`, `login-360`, `warmup-1280`): la barra superior sobresale ~10 px a cada lado del papel dentado (es un "ranura de impresora" o un desajuste de máscara; el brief no lo describe). Arreglo: confirmar la intención; si es ranura, hacerla más obvia (altura 10 px y radio de ranura) o igualar anchos. S. Brief: sí, nombrarlo si se queda.

**P2-2. Estado vacío de Empresas**: una línea de 70 px con dos enlaces en la misma frase (`empresas-vacio-1280`). Dice qué hacer, pero compite. Arreglo: un solo botón primario "Registrar empresa" (ya está en el encabezado: dejar solo el texto "Todavía no hay empresas…" y el enlace "Actualizar la lista" como secundario). S. Brief: no.

**P2-3. Avisos con `border-left: 4px`** en `company.page.css:59`, `neighbor.page.css:206`, `staff-new.page.css:5`, `change-password.page.css:25`, `certificate-issue.page.css:125`. Es el patrón vetado de acento lateral si son avisos; sustituir por `.problem`/`.done` (marco de 2 px + signo). No verifiqué en captura cuáles son avisos y cuáles datos. S. Brief: no.

**P2-4. Certificado: ritmo al pie** (`certificado-1280`): la columna de descargas arranca bajo el borde superior del ticket y termina a 340 px; "Anular certificado" cae como bloque suelto con h2 de 20 px que casi compite con "Contexto distrital" (dentro del ticket). Arreglo: columna de descargas pegada al borde superior del ticket (hoy ya lo está) y `position: sticky`; "Anular" como bloque de menor jerarquía (h3, `ink-soft`, botón con borde); "Contexto distrital" a 14 px con `.dots` para que el momento firma sea el kilo. M. Brief: no.

**P2-5. Navegación a 360**: ocupa tres filas (~125 px) antes del título; el correo y "Cerrar sesión" van en su propia línea. Arreglo: correo bajo "Cerrar sesión" fuera de la barra o una sola fila con scroll horizontal; hoy cumple 44 px. S–M. Brief: no.

**P2-6. Login a 360**: el titular queda debajo del ticket como cola (3 líneas) y el ticket con ~150 px de aire arriba. Arreglo: a 360 el titular arriba en 2 líneas y 32 px, o `margin-top: 48px` al ticket. S. Brief: no.

**P2-7. Cobalto**: solo aparece en enlaces y en la raya activa; "Volver a …" subrayado es lo más vivo de la pantalla. Si se quiere más presencia (revisor A), contorno cobalto en el período destacado del gráfico; lo dejo como opción, no como defecto (el brief lo reserva a "dónde estás, enlaces y foco"). S. Brief: sí, si se adopta.

**P2-8. Error de login**: el marco `.problem` (2 px, tinta) iguala el peso del botón contiguo y compiten. Arreglo: `border-width: 1.5px` o fondo `--selection`. S. Brief: no.

### Revisiones de los sub-agentes que no se confirmaron

- "Nav a 360 sin altura mínima" (B): falso, `shell.ts` fija `min-height: 44px` en `nav a`; la medición lo confirma.
- "Fila Activa de Asociaciones a 360 queda con fondo hover permanente" (A): en la captura la primera entrada es gris claro por `a.entry:hover` capturado con el cursor del test; no es un defecto de CSS.
- Puntuaciones numéricas de A (4,5 a 7,5): opinión, no medida.

## Propuestas de elemento firma o jerarquía (máximo 3)

**1. "Total de la jornada" en la cabecera de la ficha** (jerarquía, sin añadir datos): el resumen sube bajo el título y el kilo del último período se imprime como línea de ticket con guía punteada.

```
Envases del Pacífico S.A.C.            [Emitir certificado]
RUC 20512345678 · ■ Activa
┌────────────────────────────────────────────────────┐
 ÚLTIMO PERÍODO ............................ Dic 2024
 KILOS TRAZADOS .......................... 12,480.50 kg   ← mono 34 px
 CUMPLIMIENTO (declarado) .................... 87.5 %
└────────────────────────────────────────────────────┘
 [gráfico de 12 períodos, a la altura del ticket]
```

Reutiliza `.ticket`, `.dots` y los tokens actuales; el brief ya pide "tres líneas, ticket dentado a la izquierda del gráfico": solo cambia la posición en la página y el ancho.

**2. Lista de empresas como un solo rollo** (carácter sin complejidad): un ticket continuo con guías punteadas entre filas y un total al pie, en vez de talones sueltos.

```
 ⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇
  Envases del Pacífico S.A.C.    RUC 20512345678   ■ Activa  ›
  ·····················································
  Reciclajes Industriales …      RUC 20603829104   □ Inactiva ›
  ·····················································
  2 empresas
 ⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇⌇
```

Da una sola forma reconocible, quita el ruido dentado (P1-1) y "Mostrando 1–2 de 2" se convierte en el pie impreso.

**3. Sello de tinta en lugar de chip para estados** (solo si el dueño quiere más carácter): "Activa/Inactiva/Suspendida" como línea de ticket con palabra en mayúsculas mono 13 px y marca llena/hueca (ya existe), alineada a la derecha con guía punteada desde el nombre en la tabla de Asociaciones. Mantiene "color no es el único canal" y no toca los colores de certificación.

```
 Asociación Recicla Rímac ········· REG-2021-0145   ■ ACTIVA
 Recicladores Unidos de Comas ····· REG-2019-0087   □ SUSPENDIDA
```

## Qué NO tocar (ya está bien)

- Paleta de una tinta, `ground` concreto, `paper`, cobalto escaso; contrastes AA calculados; semáforo reservado a certificaciones.
- Archivo + Martian Mono autoalojadas, h1 800 con tracking negativo, cifras tabulares; el mono solo para medición.
- Esquinas rectas y cero sombras en todo el sistema.
- El login (ticket + titular "Del kilo pesado al certificado de la empresa."), el certificado completo (título, kilos en mono grande entre guías, líneas con puntos guía, UUID con Copiar), "Así quedará el registro" y "Preparando el sistema" (marca llena/hueca/tachada con palabra).
- Errores en tinta con signo dibujado (`.problem`, `.field-error`) y mensajes de error con acción ("Volver a intentar"; vacíos con "Registra la primera").
- Controles propios (chevron y calendario dibujados, 46 px, deshabilitado reconocible), tablas apiladas en entradas bajo 640 px (sin scroll lateral en ninguna captura: `scrollWidth == innerWidth` en las 54), regla global de `prefers-reduced-motion`, impresión del ticket una sola vez.
- Navegación y "Cerrar sesión" ya miden 44 px.

## Comparación con el brief

- **Desviaciones del brief** (corregir sin tocar el brief): campo de peso (P1-2), validación (P1-3), áreas táctiles (P1-5), avisos de borde lateral (P2-3), texto de apoyo bajo el piso que el propio brief pide en su revisión anti-slop.
- **El brief es flojo** (conviene precisarlo): orden y número de paneles de la ficha (P0-1), talón de empresa contra "nunca dos bandas seguidas" (P1-1), piso tipográfico (P1-4), la barra de tinta del ticket (P2-1).
- **El brief está bien y el defecto es del dato de demo**: el vacío a 1280 de las listas (P0-2, parte).

## Método y reproducibilidad

Capturas con `design/refs/auditoria-2026-10-18/capture.spec.ts` y `pw.config.ts` (locales, ignorados por git) contra `e2e/backend.ts`, puerto 5733 (el 5201 está en un rango reservado de Windows). No se instaló ni descargó nada, no se ejecutó el binario de Impeccable ni `hooks on`. Los dos sub-agentes no se vieron entre sí. Las afirmaciones de los sub-agentes sobre números de línea fueron comprobadas con grep (tamaños de fuente y `border-left`); la de la navegación resultó falsa y está descartada arriba.
