// node --test portal/scripts/gen-adr-toc.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { statusLabel, parseAdr, renderIndex, renderToc, withStatusLabel, generate, collect } from './gen-adr-toc.mjs';

const adr = ({
  title = 'Los certificados se anulan, no se borran',
  estado = 'Aceptada, pendiente de implementación',
  fecha = '2026-10-10',
  where = 'reporting-service, anulación de certificados',
  extra = '',
  sections = ['Contexto', 'Decisión', 'Consecuencias', 'Alternativas descartadas'],
} = {}) =>
  [
    `# ${title}`,
    '',
    ...(estado === null ? [] : [`- **Estado:** ${estado}`]),
    ...(fecha === null ? [] : [`- **Fecha:** ${fecha}`]),
    ...(where === null ? [] : [`- **Dónde se aplica:** ${where}`]),
    extra,
    '',
    ...sections.flatMap((s) => [`## ${s}`, '', 'Texto.', '']),
  ].join('\n');

const NAME = '2026-10-10-certificados-se-anulan.md';

test('statusLabel: the four canonical values, with or without a trailing detail', () => {
  assert.equal(statusLabel('Aceptada, pendiente de implementación'), 'adr-pendiente');
  assert.equal(statusLabel('Aceptada, pendiente de implementación. Se implementa en M5.'), 'adr-pendiente');
  assert.equal(statusLabel('Implementada'), 'adr-implementada');
  assert.equal(statusLabel('Implementada (v2)'), 'adr-implementada');
  assert.equal(statusLabel('Descartada por calidad de datos (2026-10-10).'), 'adr-descartada');
  assert.equal(statusLabel('Reemplazada'), 'adr-reemplazada');
});

test('statusLabel: anything else is rejected, including the drafts\' bare "Aceptada"', () => {
  assert.equal(statusLabel('Aceptada (2026-10-10). Se implementa en M4.'), null);
  assert.equal(statusLabel('Aceptada'), null);
  assert.equal(statusLabel('Implementadas'), null);
  assert.equal(statusLabel('implementada'), null);
  assert.equal(statusLabel('Propuesta'), null);
});

test('parseAdr: a complete ADR is valid and carries what the index needs', () => {
  const parsed = parseAdr(NAME, adr());
  assert.deepEqual(parsed.errors, []);
  assert.equal(parsed.date, '2026-10-10');
  assert.equal(parsed.title, 'Los certificados se anulan, no se borran');
  assert.equal(parsed.label, 'adr-pendiente');
  assert.equal(parsed.where, 'reporting-service, anulación de certificados');
});

test('parseAdr: CRLF files (Windows checkouts) parse the same', () => {
  assert.deepEqual(parseAdr(NAME, adr().replace(/\n/g, '\r\n')).errors, []);
});

test('parseAdr: sections may come in any order (the drafts put Alternativas before Consecuencias)', () => {
  const text = adr({ sections: ['Contexto', 'Decisión', 'Alternativas descartadas', 'Consecuencias'] });
  assert.deepEqual(parseAdr(NAME, text).errors, []);
});

test('parseAdr: bad file names and impossible dates', () => {
  assert.match(parseAdr('certificados.md', adr()).errors.join(), /AAAA-MM-DD-slug/);
  assert.match(parseAdr('2026-10-10-Certificados.md', adr()).errors.join(), /AAAA-MM-DD-slug/);
  assert.match(parseAdr('2026-02-30-x.md', adr({ fecha: '2026-02-30' })).errors.join(), /no existe/);
});

test('parseAdr: each missing piece is reported', () => {
  assert.match(parseAdr(NAME, adr({ estado: null })).errors.join(), /falta "- \*\*Estado/);
  assert.match(parseAdr(NAME, adr({ estado: 'Aceptada' })).errors.join(), /estado no reconocido/);
  assert.match(parseAdr(NAME, adr({ fecha: null })).errors.join(), /falta "- \*\*Fecha/);
  assert.match(parseAdr(NAME, adr({ fecha: '2026-10-11' })).errors.join(), /no coincide/);
  assert.match(parseAdr(NAME, adr({ where: null })).errors.join(), /Dónde se aplica/);
  assert.match(parseAdr(NAME, adr({ sections: ['Contexto', 'Decisión'] })).errors.join(), /Consecuencias/);
  assert.match(parseAdr(NAME, '\nSin título\n').errors.join(), /primera línea/);
});

test('parseAdr: a repeated metadata key is an error (edit the line, don\'t add another)', () => {
  const twice = adr({ extra: '- **Estado:** Implementada' });
  assert.match(parseAdr(NAME, twice).errors.join(), /"Estado" aparece más de una vez/);
  // Bullets with other keys (e.g. inside Alternativas descartadas) may repeat.
  const alternatives = adr({ extra: '- **Opción:** a\n- **Opción:** b' });
  assert.deepEqual(parseAdr(NAME, alternatives).errors, []);
});

test('parseAdr: the title can\'t carry characters that break the index link or Writerside', () => {
  for (const title of ['Pesos < 1 kg', 'Uso de [corchetes]', 'a > b']) {
    assert.match(parseAdr(NAME, adr({ title })).errors.join(), /no puede llevar/, title);
  }
  assert.deepEqual(parseAdr(NAME, adr({ title: 'Cobertura "no verificada" (UNVERIFIED) al 100 % y $5' })).errors, []);
});

test('parseAdr: links to other decisions must exist; Reemplazada needs "Reemplazada por"', () => {
  const other = '2026-01-05-borrar-certificados.md';
  const replaced = adr({ estado: 'Reemplazada', extra: `- **Reemplazada por:** [Anular](${NAME})` });
  assert.deepEqual(parseAdr(other, replaced.replace('2026-10-10', '2026-01-05'), new Set([NAME, other])).errors, []);

  const dangling = adr({ extra: '- **Reemplaza a:** [Borrar](2026-01-05-no-existe.md)' });
  assert.match(parseAdr(NAME, dangling, new Set([NAME])).errors.join(), /no existe en decisiones/);

  const noLink = adr({ extra: '- **Reemplaza a:** la de borrar' });
  assert.match(parseAdr(NAME, noLink, new Set([NAME])).errors.join(), /debe ser "—" o un enlace/);

  const orphan = adr({ estado: 'Reemplazada', extra: '- **Reemplazada por:** —' });
  assert.match(parseAdr(NAME, orphan).errors.join(), /debe enlazar la que la reemplaza/);

  assert.deepEqual(parseAdr(NAME, adr({ extra: '- **Reemplaza a:** —' })).errors, []);
});

test('renderToc and renderIndex: newest first, same day by name, pipes escaped', () => {
  const adrs = [
    parseAdr('2026-01-05-b.md', adr({ fecha: '2026-01-05', title: 'B' })),
    parseAdr('2026-10-10-z.md', adr({ title: 'Z', estado: 'Implementada', where: 'a | b' })),
    parseAdr('2026-10-10-a.md', adr({ title: 'A' })),
  ];
  assert.equal(
    renderToc(adrs, '        '),
    [
      '        <toc-element topic="2026-10-10-a.md"/>',
      '        <toc-element topic="2026-10-10-z.md"/>',
      '        <toc-element topic="2026-01-05-b.md"/>',
    ].join('\n'),
  );
  const index = renderIndex(adrs).split('\n');
  assert.equal(index[2], '| 2026-10-10 | [A](2026-10-10-a.md) | Aceptada, pendiente de implementación | reporting-service, anulación de certificados |');
  assert.equal(index[3], '| 2026-10-10 | [Z](2026-10-10-z.md) | Implementada | a &#124; b |');
  assert.equal(index[4].startsWith('| 2026-01-05 | [B]'), true);
  assert.equal(renderIndex([]), 'Todavía no hay decisiones registradas.');
});

test('withStatusLabel: the label goes right under the title, line endings kept', () => {
  assert.equal(withStatusLabel('# T\n\n- **Estado:** x\n', 'adr-implementada'), '# T\n\n<secondary-label ref="adr-implementada"/>\n\n- **Estado:** x\n');
  assert.equal(withStatusLabel('# T\r\nx', 'adr-pendiente'), '# T\r\n\r\n<secondary-label ref="adr-pendiente"/>\r\nx');
});

function writersideFixture(adrs) {
  const dir = mkdtempSync(join(tmpdir(), 'adr-'));
  mkdirSync(join(dir, 'topics', 'decisiones'), { recursive: true });
  writeFileSync(join(dir, 'esg.tree'), '<instance-profile>\n    <toc-element topic="decisiones.md">\n        <!-- ADR-TOC -->\n    </toc-element>\n</instance-profile>\n');
  writeFileSync(join(dir, 'topics', 'decisiones', 'decisiones.md'), '# Decisiones\n\n<!-- ADR-INDEX -->\n');
  for (const [name, text] of Object.entries(adrs)) writeFileSync(join(dir, 'topics', 'decisiones', name), text);
  return dir;
}

test('generate: fills the tree, the index and each label in place', () => {
  const dir = writersideFixture({ [NAME]: adr() });
  try {
    assert.deepEqual(generate(dir).errors, []);
    const tree = readFileSync(join(dir, 'esg.tree'), 'utf8');
    assert.match(tree, /\n        <toc-element topic="2026-10-10-certificados-se-anulan.md"\/>\n    <\/toc-element>/);
    assert.doesNotMatch(tree, /ADR-TOC/);
    const index = readFileSync(join(dir, 'topics', 'decisiones', 'decisiones.md'), 'utf8');
    assert.match(index, /\[Los certificados se anulan, no se borran\]\(2026-10-10-certificados-se-anulan.md\)/);
    assert.match(readFileSync(join(dir, 'topics', 'decisiones', NAME), 'utf8'), /^# .*\n\n<secondary-label ref="adr-pendiente"\/>\n/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('generate: $ patterns in a title or "Dónde se aplica" are copied literally', () => {
  const text = adr({ title: "Costo en $' y $& y $$", where: "reporting-service $'" });
  const dir = writersideFixture({ [NAME]: text });
  try {
    assert.deepEqual(generate(dir).errors, []);
    const index = readFileSync(join(dir, 'topics', 'decisiones', 'decisiones.md'), 'utf8');
    assert.match(index, /\[Costo en \$' y \$& y \$\$\]/);
    assert.match(index, /\| reporting-service \$' \|\n$/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

const snapshot = (dir) =>
  ['esg.tree', join('topics', 'decisiones', 'decisiones.md'), join('topics', 'decisiones', NAME)].map((f) =>
    readFileSync(join(dir, f), 'utf8'),
  );

test('generate: an invalid ADR writes nothing (tree, index and the ADR itself)', () => {
  const dir = writersideFixture({ [NAME]: adr({ estado: 'Aceptada' }) });
  try {
    const before = snapshot(dir);
    assert.equal(generate(dir).errors.length, 1);
    assert.deepEqual(snapshot(dir), before);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('generate: a missing index marker writes nothing, not even the tree', () => {
  const dir = writersideFixture({ [NAME]: adr() });
  try {
    writeFileSync(join(dir, 'topics', 'decisiones', 'decisiones.md'), '# Decisiones\n');
    const before = snapshot(dir);
    assert.match(generate(dir).errors.join(), /falta el marcador <!-- ADR-INDEX -->/);
    assert.deepEqual(snapshot(dir), before);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('collect: only the index is zero ADRs and valid', () => {
  const dir = writersideFixture({});
  try {
    assert.deepEqual(collect(dir), { adrs: [], errors: [] });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('collect: no decisiones/ folder at all is zero ADRs and valid', () => {
  const dir = mkdtempSync(join(tmpdir(), 'adr-'));
  try {
    assert.deepEqual(collect(dir), { adrs: [], errors: [] });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('CLI: without arguments it refuses instead of writing over the sources', () => {
  const script = fileURLToPath(new URL('./gen-adr-toc.mjs', import.meta.url));
  const run = spawnSync(process.execPath, [script], { encoding: 'utf8' });
  assert.equal(run.status, 2);
  assert.match(run.stderr, /Uso:/);
});
