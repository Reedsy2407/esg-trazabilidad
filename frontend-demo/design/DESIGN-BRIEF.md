# DESIGN-BRIEF — frontend-demo

Única fuente de verdad del diseño. Stitch, Claude Design y Claude Code trabajan contra este archivo.

- **Audiencia:** operadores y personal de asociaciones de recicladores en Lima; uso diario, pantalla de escritorio.
- **Tres adjetivos:** preciso, sobrio, legible.
- **Personalidad:** herramienta de trabajo seria, densa y calmada, con aire de registro de balanza / libro de campo. No es una web de marketing.
- **Elemento firma:** la pantalla del certificado, como documento impreso verificable (código de verificación en mono, tabla de pesadas, sello de estado). El resto de la app es sobrio para que el certificado destaque.

## Paleta (contraste AA calculado sobre `paper`)

| Token | Hex | Uso |
|---|---|---|
| ink | #16211F | texto |
| muted | #56635F | texto secundario |
| paper | #F5F6F4 | fondo (gris neutro, no crema) |
| surface | #FFFFFF | tarjetas y tablas |
| petrol | #0D4F5C | marca, cabecera, enlaces |
| signal | #C2410C | SOLO acción primaria |
| vigente | #1A7A48 | certificado vigente |
| por-vencer | #9A5B00 | por vencer |
| vencido | #B3261E | vencido o suspendido |

Borde decorativo: #D5DAD6. Los bordes de campos de formulario necesitan >= 3:1 sobre el fondo: definir un token de borde de input más oscuro y comprobarlo con un verificador de contraste.

## Tipografía
IBM Plex Sans (interfaz) + IBM Plex Mono (IDs, kg, RUC, códigos), cifras tabulares alineadas a la derecha. Nunca Inter, Roboto, Arial ni la fuente del sistema.

## Forma, iconos y movimiento
- Radio pequeño y único (2-4 px) en datos. Sin sombras de elevación: estructura con bordes de 1 px. Espaciado base 4 px.
- Un solo juego de iconos (Material Symbols outlined), solo donde aporten significado.
- Movimiento solo en: transición de ruta corta, anillo de foco, esqueleto de carga y aviso de calentamiento. Respetar `prefers-reduced-motion`.

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
| Detalle de certificado | `GET /certificates/{id}`, `/pdf`, `/csv` | los mismos campos + id (código de verificación) + descargas PDF y CSV. No hay estado ni tabla de pesadas |
| Alta de recojo | `POST /neighbors/{id}/collection-records` | asociación (obligatoria), fecha (obligatoria), peso en kg (obligatorio, positivo), cronograma (opcional) |
| Asociaciones | `GET /associations` | nombre, RUC, N.º de registro, dirección, correo, teléfono, estado (ACTIVE/SUSPENDED) |

Reglas añadidas tras la primera ronda de Stitch:
- Fondo exacto `#F5F6F4` (gris neutro, NO verdoso ni menta).
- Máximo 6 columnas por tabla y UNA línea por fila (sin texto secundario dentro de la celda).
- Máximo 3 niveles de cromo: barra superior, título con su acción, contenido. Sin pestañas, sin franja de resumen, sin pie de estado de hardware.
- Los chips VIGENTE / POR VENCER / VENCIDO solo aplican a certificaciones de asociaciones, no a certificados ESG.

## Ficha de empresa: reglas fijadas tras la ronda 3 de Stitch (2026-10-03)

La captura de Stitch es solo guía de distribución. En la implementación mandan estas reglas:

- Encabezado: marca "Trazabilidad ESG" a la izquierda, correo y "Cerrar sesión" a la derecha, enlace "Volver a empresas", nombre, RUC en mono y estado. SIN sector, SIN ubicación y SIN botones de acción (no existen "Emitir certificado" ni "Descargar historial"; el PDF y el CSV son por certificado, en el detalle).
- Resumen del período: exactamente tres líneas (Período, Kilos trazados, Cumplimiento de jerarquía), valores en mono. Sin "Lotes", "Puntos de acopio" ni "Estado de auditoría".
- Gráfica: una sola, 12 períodos, eje Y en 0 / 5,000 / 10,000 / 15,000 kg, sin leyenda, sin sombras, sin animación. Debe tener alternativa accesible (tabla o texto).
- Tabla: columna Período como "Octubre 2024" (no "2024-10 (Octubre 2024)"); kilos y cumplimiento alineados a la derecha en mono; filas de 48 px, clicables, con chevron; pie "Mostrando N de N certificados".
- Prohibido: textos de sello oficial ("histórico formal auditado") y líneas de sincronización inventadas.
