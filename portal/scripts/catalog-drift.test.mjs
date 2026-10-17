import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { checkAll, codesInCatalog, codesInJava, compare } from './catalog-drift.mjs';

test('reads the codes of an enum, ignoring text that only looks like one', () => {
  const java = `
    NOT_FOUND("ASO-001", "Asociación no encontrada", HttpStatus.NOT_FOUND),
    INVALID_RUC(
        "ASO-004", "El RUC no es válido", HttpStatus.BAD_REQUEST);
    // see "A-1" and "ASO-12" and ASO-999 without quotes
  `;
  assert.deepEqual([...codesInJava(java)].sort(), ['ASO-001', 'ASO-004']);
});

test('reads the codes of the first column of a catalog table', () => {
  const md = '| Código | HTTP |\n|---|---|\n| `ASO-001` | 404 |\n| `ASO-004` | 400 |\nTexto con `ASO-777` fuera de la tabla\n';
  assert.deepEqual([...codesInCatalog(md)].sort(), ['ASO-001', 'ASO-004']);
});

test('reports what is missing from the catalog and what the catalog invents', () => {
  const result = compare(new Set(['A-1', 'B-2']), new Set(['B-2', 'C-3']));
  assert.deepEqual(result, { missing: ['A-1'], extra: ['C-3'] });
});

test('checks every service against its own page and reports a missing page', () => {
  const root = mkdtempSync(join(tmpdir(), 'catalog-drift-'));
  try {
    const src = join(root, 'recycler-service', 'src', 'main', 'java', 'x');
    mkdirSync(src, { recursive: true });
    writeFileSync(join(src, 'AssociationErrors.java'), 'NOT_FOUND("ASO-001", "m", S), DUP("ASO-002", "m", S);');
    const topics = join(root, 'portal', 'Writerside', 'topics', 'errores');
    mkdirSync(topics, { recursive: true });
    writeFileSync(join(topics, 'errores-recycler.md'), '| `ASO-001` | 404 |\n| `ASO-009` | 409 |\n');

    const report = checkAll(root);
    const recycler = report.find((r) => r.service === 'recycler');
    assert.deepEqual(recycler.missing, ['ASO-002']);
    assert.deepEqual(recycler.extra, ['ASO-009']);
    assert.equal(report.find((r) => r.service === 'auth').pageMissing, true);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
