#!/usr/bin/env node
// Copies each service's own already-deterministic OpenAPI export into
// portal/Writerside/specs/, so the portal's committed specs come straight
// from the real controllers (via springdoc's /v3/api-docs), never retyped
// by hand.
//
// Prerequisite: each <service>/target/openapi/<service>.json below must
// already exist, written by that service's own OpenApiExportIT (or, for
// auth-service, OpenApiDocsIT) test:
//
//   mvn -pl <service> -am verify \
//     -Dit.test=OpenApiExportIT,OpenApiDocsIT -DfailIfNoSpecifiedTests=false
//
// (this also runs every unit test in <service> -- surefire and failsafe
// share the skipTests property, so there's no standard flag that skips
// only the former.)
//
// Usage (from anywhere): node portal/scripts/export-openapi.mjs
import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SERVICES = ['recycler-service', 'collection-service', 'reporting-service', 'auth-service'];

export function copySpecs(repoRoot) {
  const specsDir = join(repoRoot, 'portal', 'Writerside', 'specs');
  mkdirSync(specsDir, { recursive: true });

  const copied = [];
  const missing = [];
  for (const service of SERVICES) {
    const source = join(repoRoot, service, 'target', 'openapi', `${service}.json`);
    if (!existsSync(source)) {
      missing.push(source);
      continue;
    }
    const destination = join(specsDir, `${service}.json`);
    copyFileSync(source, destination);
    copied.push(destination);
  }
  return { copied, missing };
}

function main() {
  const here = dirname(fileURLToPath(import.meta.url));
  const repoRoot = resolve(here, '..', '..');
  const { copied, missing } = copySpecs(repoRoot);

  if (missing.length > 0) {
    console.error(`Faltan specs exportadas (corre "mvn verify" en cada servicio primero):`);
    for (const m of missing) console.error(`  - ${m}`);
    process.exitCode = 1;
    return;
  }
  console.log(`${copied.length} specs OpenAPI copiadas a portal/Writerside/specs/`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
