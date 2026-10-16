// Read-only: lists the collection records of one period (default September 2026), by neighbour, with
// the smoke account. Flags the ones that are not the demo seed's (scripts/demo-seed/data.mjs).
//
//   node scripts/prod-list-collections.mjs [from] [to]
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { NEIGHBORS as SEED } from './demo-seed/data.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const [from = '2026-09-01', to = '2026-09-30'] = process.argv.slice(2);
const services = Object.fromEntries(
  readFileSync(join(here, 'services.txt'), 'utf8').split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#')).map((l) => l.split(/\s+/)),
);
const cred = (f) =>
  execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(here, 'smoke-cred.ps1'), '-Mode', 'Get', '-Field', f], { encoding: 'utf8' }).trim();
const login = await (
  await fetch(`${services.auth}/auth/login`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email: cred('Email'), password: cred('Password') }) })
).json();
const get = async (url) => (await fetch(url, { headers: { authorization: `Bearer ${login.accessToken}` } })).json();

const seedKeys = new Set(SEED.flatMap((n) => n.collections.map((c) => `${n.fullName}|${c.collectionDate}|${c.weightKg}`)));
const neighbors = (await get(`${services.collection}/neighbors?size=100`)).content;
let total = 0;
let foreign = 0;
for (const n of neighbors) {
  const records = (await get(`${services.collection}/neighbors/${n.id}/collection-records?from=${from}&to=${to}&size=100`)).content ?? [];
  for (const r of records) {
    const mine = seedKeys.has(`${n.fullName}|${r.collectionDate}|${r.weightKg}`);
    total += r.weightKg;
    if (!mine) foreign += r.weightKg;
    console.log(`${mine ? 'seed   ' : 'OTHER  '} ${r.collectionDate} ${String(r.weightKg).padStart(6)} kg  ${n.fullName} [${n.status}] record=${r.id} assoc=${r.associationId} schedule=${r.scheduleId ?? '-'}`);
  }
}
console.log(`\nperiod ${from}..${to}: total ${Math.round(total * 100) / 100} kg, not from the seed ${Math.round(foreign * 100) / 100} kg`);
