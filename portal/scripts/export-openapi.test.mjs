// node --test portal/scripts/export-openapi.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { SERVICES, copySpecs } from './export-openapi.mjs';

function withTempRepo(fn) {
  const repoRoot = mkdtempSync(join(tmpdir(), 'export-openapi-'));
  try {
    fn(repoRoot);
  } finally {
    rmSync(repoRoot, { recursive: true, force: true });
  }
}

test('copySpecs: copies every service export into Writerside/specs/', () => {
  withTempRepo((repoRoot) => {
    for (const service of SERVICES) {
      const dir = join(repoRoot, service, 'target', 'openapi');
      mkdirSync(dir, { recursive: true });
      writeFileSync(join(dir, `${service}.json`), `{"service":"${service}"}\n`);
    }

    const { copied, missing } = copySpecs(repoRoot);

    assert.equal(missing.length, 0);
    assert.equal(copied.length, SERVICES.length);
    for (const service of SERVICES) {
      const destination = join(repoRoot, 'portal', 'Writerside', 'specs', `${service}.json`);
      assert.ok(existsSync(destination));
      assert.equal(readFileSync(destination, 'utf8'), `{"service":"${service}"}\n`);
    }
  });
});

test('copySpecs: reports every missing export instead of copying partially', () => {
  withTempRepo((repoRoot) => {
    const [first, ...rest] = SERVICES;
    const dir = join(repoRoot, first, 'target', 'openapi');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, `${first}.json`), '{}\n');
    // rest are left unwritten on purpose.

    const { copied, missing } = copySpecs(repoRoot);

    assert.equal(copied.length, 1);
    assert.equal(missing.length, rest.length);
    for (const service of rest) {
      assert.ok(missing.some((m) => m.includes(service)));
    }
  });
});
