#!/usr/bin/env node
// Decision records (ADR) for the portal: one file per decision in
// Writerside/topics/decisiones/AAAA-MM-DD-slug.md, so several terminals can add
// decisions without touching a shared file. This script validates every ADR and
// generates what would otherwise be shared:
//   - the ADR entries in esg.tree (at the ADR-TOC marker),
//   - the index table in decisiones.md (at the ADR-INDEX marker),
//   - each ADR's status label (<secondary-label>), derived from its "Estado" line.
//
//   node gen-adr-toc.mjs --check             validate portal/Writerside, write nothing
//   node gen-adr-toc.mjs <writerside-dir>    validate and generate, in place
//
// build-local.sh runs the second form on a throwaway copy (portal/.build/), so the
// committed tree and index keep only their markers.
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Forward slashes: valid for fs on Windows too, and readable in error messages.
export const ADR_DIR = 'topics/decisiones';
export const INDEX_TOPIC = 'decisiones.md';
export const TOC_MARKER = '<!-- ADR-TOC -->';
export const INDEX_MARKER = '<!-- ADR-INDEX -->';

// Canonical status values, in the order the template lists them, and their label ids
// in labels.list.
export const STATUSES = [
  ['Aceptada, pendiente de implementación', 'adr-pendiente'],
  ['Implementada', 'adr-implementada'],
  ['Descartada', 'adr-descartada'],
  ['Reemplazada', 'adr-reemplazada'],
];

const METADATA = ['Estado', 'Fecha', 'Dónde se aplica', 'Reemplaza a', 'Reemplazada por'];
const REQUIRED_SECTIONS =['Contexto', 'Decisión', 'Consecuencias', 'Alternativas descartadas'];
const FILE_NAME = /^(\d{4}-\d{2}-\d{2})-[a-z0-9]+(?:-[a-z0-9]+)*\.md$/;
const NONE = /^[—-]$/;

/** The status label id for an "Estado" value, or null when it isn't one of the four. */
export function statusLabel(estado) {
  for (const [canonical, label] of STATUSES) {
    if (estado === canonical) return label;
    // A detail may follow, separated by punctuation or a space:
    // "Implementada (v2)", "Descartada por calidad de datos", "Aceptada, pendiente de
    // implementación. Se implementa en M5." -- but not "Implementadas".
    if (estado.startsWith(canonical) && /^[\s.,;:(—-]/.test(estado.slice(canonical.length))) return label;
  }
  return null;
}

function isRealDate(iso) {
  const d = new Date(`${iso}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === iso;
}

/** ADR file names a "Reemplaza a" / "Reemplazada por" value links to. */
function linkedAdrs(value) {
  return [...value.matchAll(/\]\(([^)\s]+\.md)\)/g)].map((m) => m[1]);
}

/**
 * Parses and validates one ADR. Returns { name, date, title, estado, label, where,
 * errors }; errors is empty when the ADR is valid. `existing` is the set of ADR file
 * names, to check links to other decisions.
 */
export function parseAdr(name, text, existing = new Set()) {
  const errors = [];
  const nameMatch = FILE_NAME.exec(name);
  if (!nameMatch) {
    errors.push('el nombre debe ser AAAA-MM-DD-slug.md (slug en minúsculas, con guiones)');
  } else if (!isRealDate(nameMatch[1])) {
    errors.push(`la fecha del nombre (${nameMatch[1]}) no existe`);
  }
  const date = nameMatch ? nameMatch[1] : null;

  const lines = text.split(/\r?\n/);
  const titleLine = lines.find((l) => l.trim() !== '');
  const title = titleLine && titleLine.startsWith('# ') ? titleLine.slice(2).trim() : null;
  if (!title) errors.push('la primera línea debe ser el título: "# La decisión en una frase"');
  // The title becomes link text in the index: brackets would break the link, and
  // Writerside parses < as XML.
  else if (/[[\]<>]/.test(title)) errors.push('el título no puede llevar [ ] < >');

  // Metadata bullets: - **Campo:** valor. The first occurrence wins, so a repeated
  // key (a second "Estado" line added instead of editing the first) is an error.
  const meta = new Map();
  for (const line of lines) {
    const m = /^- \*\*([^*]+?):\*\*\s*(.*)$/.exec(line.trim());
    if (!m) continue;
    if (!meta.has(m[1])) meta.set(m[1], m[2].trim());
    else if (METADATA.includes(m[1])) errors.push(`"${m[1]}" aparece más de una vez; edita la línea existente`);
  }

  const estado = meta.get('Estado') ?? null;
  const label = estado ? statusLabel(estado) : null;
  if (!estado) {
    errors.push('falta "- **Estado:**"');
  } else if (!label) {
    errors.push(
      `estado no reconocido: "${estado}". Debe empezar con uno de: ${STATUSES.map(([s]) => `"${s}"`).join(', ')}`,
    );
  }

  const fecha = meta.get('Fecha') ?? null;
  if (!fecha) {
    errors.push('falta "- **Fecha:**"');
  } else if (date && fecha !== date) {
    errors.push(`la fecha (${fecha}) no coincide con la del nombre del archivo (${date})`);
  }

  const where = meta.get('Dónde se aplica') ?? null;
  if (!where) errors.push('falta "- **Dónde se aplica:**"');

  for (const field of ['Reemplaza a', 'Reemplazada por']) {
    const value = meta.get(field);
    if (value === undefined || NONE.test(value)) continue;
    const links = linkedAdrs(value);
    if (links.length === 0) errors.push(`"${field}" debe ser "—" o un enlace [título](AAAA-MM-DD-slug.md)`);
    for (const link of links) {
      if (!existing.has(link)) errors.push(`"${field}" enlaza a ${link}, que no existe en decisiones/`);
    }
  }
  if (label === 'adr-reemplazada') {
    const by = meta.get('Reemplazada por');
    if (!by || NONE.test(by)) errors.push('una decisión Reemplazada debe enlazar la que la reemplaza en "Reemplazada por"');
  }

  const sections = new Set(lines.filter((l) => l.startsWith('## ')).map((l) => l.slice(3).trim()));
  for (const section of REQUIRED_SECTIONS) {
    if (!sections.has(section)) errors.push(`falta la sección "## ${section}"`);
  }

  return { name, date, title, estado, label, where, errors };
}

/** Newest first; same day, by file name. */
export function sortAdrs(adrs) {
  return [...adrs].sort((a, b) => (a.date === b.date ? a.name.localeCompare(b.name) : b.date.localeCompare(a.date)));
}

export function renderToc(adrs, indent) {
  return sortAdrs(adrs)
    .map((adr) => `${indent}<toc-element topic="${adr.name}"/>`)
    .join('\n');
}

// Writerside's Markdown doesn't treat \| as an escape inside tables (it renders the
// backslash), so a pipe in a cell becomes its HTML entity.
const cell = (s) => s.replace(/\|/g, '&#124;');

export function renderIndex(adrs) {
  if (adrs.length === 0) return 'Todavía no hay decisiones registradas.';
  const status = (label) => STATUSES.find(([, l]) => l === label)[0];
  return [
    '| Fecha | Decisión | Estado | Dónde se aplica |',
    '|---|---|---|---|',
    ...sortAdrs(adrs).map(
      (adr) => `| ${adr.date} | [${cell(adr.title)}](${adr.name}) | ${status(adr.label)} | ${cell(adr.where)} |`,
    ),
  ].join('\n');
}

/** Puts the status label right under the title, where Writerside expects labels. */
export function withStatusLabel(text, label) {
  const eol = text.includes('\r\n') ? '\r\n' : '\n';
  const lines = text.split(/\r?\n/);
  const titleAt = lines.findIndex((l) => l.trim() !== '');
  lines.splice(titleAt + 1, 0, '', `<secondary-label ref="${label}"/>`);
  return lines.join(eol);
}

/** Validates every ADR under writersideDir; returns { adrs, errors }. */
export function collect(writersideDir) {
  const dir = join(writersideDir, ADR_DIR);
  const names = existsSync(dir) ? readdirSync(dir).filter((n) => n.endsWith('.md') && n !== INDEX_TOPIC) : [];
  const existing = new Set(names);
  const adrs = names.map((name) => parseAdr(name, readFileSync(join(dir, name), 'utf8'), existing));
  const errors = adrs.flatMap((adr) => adr.errors.map((e) => `${ADR_DIR}/${adr.name}: ${e}`));
  return { adrs, errors };
}

/** Validates, then generates the tree entries, the index and the labels in place. */
export function generate(writersideDir) {
  const { adrs, errors } = collect(writersideDir);
  if (errors.length > 0) return { adrs, errors };

  // Both markers are checked before the first write: a failure writes nothing.
  const treePath = join(writersideDir, 'esg.tree');
  const tree = readFileSync(treePath, 'utf8');
  const markerLine = tree.split(/\r?\n/).find((l) => l.includes(TOC_MARKER));
  if (!markerLine) return { adrs, errors: [`esg.tree: falta el marcador ${TOC_MARKER}`] };
  const indexPath = join(writersideDir, ADR_DIR, INDEX_TOPIC);
  const index = readFileSync(indexPath, 'utf8');
  if (!index.includes(INDEX_MARKER)) return { adrs, errors: [`${ADR_DIR}/${INDEX_TOPIC}: falta el marcador ${INDEX_MARKER}`] };

  // Replacements go through a function: a string replacement would expand $&, $'
  // and $$ found in an ADR's title or "Dónde se aplica".
  const indent = markerLine.slice(0, markerLine.indexOf(TOC_MARKER));
  writeFileSync(treePath, tree.replace(markerLine, () => renderToc(adrs, indent)));
  writeFileSync(indexPath, index.replace(INDEX_MARKER, () => renderIndex(adrs)));

  for (const adr of adrs) {
    const path = join(writersideDir, ADR_DIR, adr.name);
    writeFileSync(path, withStatusLabel(readFileSync(path, 'utf8'), adr.label));
  }
  return { adrs, errors: [] };
}

function main(args) {
  const here = dirname(fileURLToPath(import.meta.url));
  if (args.length !== 1) {
    // Generating writes in place: it must never default to the committed sources.
    console.error('Uso: node gen-adr-toc.mjs --check | node gen-adr-toc.mjs <copia-de-Writerside>');
    return 2;
  }
  const check = args[0] === '--check';
  const dir = check ? resolve(here, '..', 'Writerside') : resolve(args[0]);
  const { adrs, errors } = check ? collect(dir) : generate(dir);
  if (errors.length > 0) {
    console.error(`ADR inválidos (${errors.length} problema(s)):`);
    for (const e of errors) console.error(`  - ${e}`);
    return 1;
  }
  console.log(`${adrs.length} ADR válidos${check ? '' : `; árbol e índice generados en ${dir}`}.`);
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
