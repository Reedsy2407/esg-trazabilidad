#!/usr/bin/env node
// Compares the error codes each service declares in its *Errors.java enums with
// the codes the portal's catalog pages list (Writerside/topics/errores/
// errores-<servicio>.md). It only informs: portal.yml runs it with
// continue-on-error, like openapi-drift, so a drift never blocks the deploy.
//
// Two kinds of drift, both reported:
//   - in the code but not in the catalog: the page is incomplete;
//   - in the catalog but not in the code: the page documents a code that no
//     longer exists.
//
// Usage (from anywhere): node portal/scripts/catalog-drift.mjs
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Codes are "AAA-000" (AUTH-001, RPT-021...). The common ones that are not in a
// service enum (the kernel's validation and conflict codes, the 503) are checked
// against errores.md separately.
const CODE = /"([A-Z]{3,4}-\d{3})"/g;
const CATALOG_CODE = /^\|\s*`([A-Z]{3,4}-\d{3})`/gm;

export const SERVICES = {
  auth: ['auth-service/src/main/java'],
  recycler: ['recycler-service/src/main/java'],
  collection: ['collection-service/src/main/java'],
  reporting: ['reporting-service/src/main/java'],
};

/** The codes found in an *Errors.java file. */
export function codesInJava(text) {
  return new Set([...text.matchAll(CODE)].map((m) => m[1]));
}

/** The codes in the first column of a catalog page's tables. */
export function codesInCatalog(text) {
  return new Set([...text.matchAll(CATALOG_CODE)].map((m) => m[1]));
}

function* walk(dir) {
  if (!existsSync(dir)) return;
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* walk(path);
    else yield path;
  }
}

/** Codes declared by the *Errors.java enums under the given source roots. */
export function declaredCodes(repoRoot, roots) {
  const found = new Set();
  for (const root of roots) {
    for (const file of walk(join(repoRoot, root))) {
      if (!file.endsWith('Errors.java')) continue;
      for (const code of codesInJava(readFileSync(file, 'utf8'))) found.add(code);
    }
  }
  return found;
}

/** { missing, extra } between what the code declares and what the catalog lists. */
export function compare(declared, documented) {
  return {
    missing: [...declared].filter((c) => !documented.has(c)).sort(),
    extra: [...documented].filter((c) => !declared.has(c)).sort(),
  };
}

export function checkAll(repoRoot) {
  const topics = join(repoRoot, 'portal', 'Writerside', 'topics', 'errores');
  const report = [];
  for (const [service, roots] of Object.entries(SERVICES)) {
    const page = join(topics, `errores-${service}.md`);
    const documented = existsSync(page) ? codesInCatalog(readFileSync(page, 'utf8')) : new Set();
    const result = compare(declaredCodes(repoRoot, roots), documented);
    report.push({ service, ...result, pageMissing: !existsSync(page) });
  }
  return report;
}

function main() {
  const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
  const report = checkAll(repoRoot);
  let drift = false;
  for (const r of report) {
    if (r.pageMissing) {
      console.log(`${r.service}: falta la página errores-${r.service}.md`);
      drift = true;
    }
    if (r.missing.length) {
      console.log(`${r.service}: en el código y no en el catálogo: ${r.missing.join(', ')}`);
      drift = true;
    }
    if (r.extra.length) {
      console.log(`${r.service}: en el catálogo y no en el código: ${r.extra.join(', ')}`);
      drift = true;
    }
  }
  if (drift) {
    console.log("::warning::El catálogo de errores del portal está desfasado del código. Actualiza portal/Writerside/topics/errores/errores-<servicio>.md.");
    process.exit(1);
  }
  console.log('No hay desfase: el catálogo de errores coincide con los *Errors.java.');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
