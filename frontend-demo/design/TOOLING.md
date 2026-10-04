# TOOLING — skills de diseño del frontend-demo

Skills de Claude Code instaladas en ámbito de proyecto (`.claude/skills/`, gitignored). Este archivo es el registro versionado de qué se instaló, de dónde y por qué. Instaladas el 2026-10-02 tras una auditoría de cada repo; se copiaron archivos desde un commit fijado, sin ejecutar ningún instalador, `npx` ni script de terceros.

## Regla de uso

- **La fuente de verdad del diseño es `frontend-demo/design/DESIGN-BRIEF.md`.** Ninguna skill la sustituye ni la contradice. Si una skill sugiere otra dirección, gana el brief.
- **frontend-design genera**: es la skill que se usa para construir pantallas.
- **Impeccable solo revisa**: crítica y auditoría sobre lo ya construido. No genera ni rediseña.
- **enhance-prompt** solo prepara prompts para Stitch (`STITCH-PROMPTS.md`), tomando el sistema de diseño del brief.

## Instalado

| Skill | Repo | Commit fijado | Fecha del commit | Qué se copió |
|---|---|---|---|---|
| frontend-design | `anthropics/skills` | `8a1541c4a3ffa5a20a5a91de0dcf3f0bab1d1ef4` | 2026-09-28 | `skills/frontend-design/` → `.claude/skills/frontend-design/` (`SKILL.md`, `LICENSE.txt`). Solo Markdown. |
| impeccable | `pbakaus/impeccable` | `508d7e8955de3b3caf2d8676e85206723d41a887` (tag `skill-v4.5.0`) | 2026-10-01 | `.claude/skills/impeccable/` → `.claude/skills/impeccable/` (57 archivos: `SKILL.md`, `reference/`, `scripts/` con el launcher, sin binario). |
| enhance-prompt | `google-labs-code/stitch-skills` | `0337446dadde6f8c94210444e2aa9d546126480f` | 2026-08-17 | `plugins/stitch-utilities/skills/enhance-prompt/` → `.claude/skills/enhance-prompt/`, con el bloque `allowed-tools` eliminado de `SKILL.md`. |

Las tres tienen licencia Apache-2.0.

## Excluido a propósito

- **Binario de Impeccable (`impeccable.exe`, 17 MB).** Es nativo y no se puede auditar leyéndolo. Su hash coincide entre npm y el release de GitHub, pero eso no prueba que corresponda al código fuente. Sin él, los comandos de Impeccable que necesitan el motor no funcionan. Si se invocan, el launcher intenta descargarlo desde GitHub Releases a `~/.impeccable/bin/`, fuera del proyecto. **No aprobar esa descarga** sin una decisión explícita.
- **Hooks de Impeccable** (`SessionStart`, `PostToolUse` en Edit/Write y `Stop`). Ejecutarían el binario automáticamente en cada sesión y en cada edición. Por eso no se usó `npx impeccable install`, que los instala en `.claude/settings.local.json`, y **no se debe ejecutar `/impeccable hooks on`**.
- **Subagentes de Impeccable** (`.claude/agents/impeccable-*.md`). Traen `Bash` y `Write`, y solo tienen sentido con el motor instalado.
- **`allowed-tools` de enhance-prompt** (`Read`, `Write`). Mientras la skill está activa le daría permiso de escritura sin preguntar. Sin el bloque, cada escritura pide confirmación.
- **design-md** (`stitch-skills`). Todo su procedimiento depende del MCP de Stitch, que no se configura en este proyecto, y su `allowed-tools` preautoriza `web_fetch` y cualquier herramienta `stitch*:*`.
- **MCP de Stitch.** No se configura. Stitch se usa desde su web, pegando los prompts.

## Archivos que Impeccable podría crear

Si se usa, Impeccable escribe `.impeccable/`, `PRODUCT.md` y `DESIGN.md` en la raíz del repo. Los tres están en `.gitignore`, para que no se versionen por accidente ni compitan con `DESIGN-BRIEF.md`.

## Actualizar

Ninguna se actualiza sola. Para subir de versión: volver a auditar el repo en el nuevo commit, copiar de nuevo desde ese SHA y actualizar esta tabla.
