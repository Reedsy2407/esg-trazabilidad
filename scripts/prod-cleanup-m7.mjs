// M7 production clean-up through the public APIs, with the smoke account (a STAFF). Read-only by
// default: it lists what it would do. `--apply` does it.
//
//   node scripts/prod-cleanup-m7.mjs            list companies, certificates and smoke neighbours, and the plan
//   node scripts/prod-cleanup-m7.mjs --apply    annul the certificates of the test companies, deactivate
//                                              those companies and the active smoke neighbours
//
// Credentials: the DPAPI file read by scripts/smoke-cred.ps1, never printed. Only the four known
// services (scripts/services.txt). It never touches a human staff account and never calls SUNAT
// (it registers no company), so it spends no provider lookup.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const apply = process.argv.includes('--apply');
const services = Object.fromEntries(
  readFileSync(join(here, 'services.txt'), 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l !== '' && !l.startsWith('#'))
    .map((l) => l.split(/\s+/)),
);
const ps1 = join(here, 'smoke-cred.ps1');
const cred = (field) =>
  execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1, '-Mode', 'Get', '-Field', field], {
    encoding: 'utf8',
  }).trim();

const call = async (method, url, token, body) => {
  const res = await fetch(url, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(150_000),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* not json */
  }
  return { status: res.status, json };
};

const login = await call('POST', `${services.auth}/auth/login`, null, { email: cred('Email'), password: cred('Password') });
if (login.status !== 200) throw new Error(`login failed (${login.status})`);
const token = login.json.accessToken;
const get = (url) => call('GET', url, token);

const pages = async (url) => {
  const all = [];
  for (let page = 0; ; page += 1) {
    const r = await get(`${url}${url.includes('?') ? '&' : '?'}page=${page}&size=100`);
    if (r.status !== 200) throw new Error(`${url} -> ${r.status}`);
    all.push(...r.json.content);
    if (page + 1 >= r.json.totalPages) return all;
  }
};

const companies = await pages(`${services.reporting}/tracked-companies`);
const neighbors = await pages(`${services.collection}/neighbors`);
console.log(
  `companies: ${companies.length} (${companies.filter((c) => c.status === 'ACTIVE').length} active), ` +
    `neighbours: ${neighbors.length} (${neighbors.filter((n) => n.status === 'ACTIVE').length} active, ${neighbors.filter((n) => n.status !== 'ACTIVE').length} inactive)`,
);

// A company is a test one when its name says so or it is the demo RUC that fails the check digit.
const TEST_NAME = /smoke|sunat|task\s*83|demo|ficticia|prueba|test/i;
const isTestCompany = (c) => TEST_NAME.test(c.name) || c.ruc === '20999000029';

const plan = { annul: [], deactivate: [], neighbors: [] };
for (const c of companies) {
  const certs = await pages(`${services.reporting}/tracked-companies/${c.id}/certificates`);
  const live = certs.filter((x) => x.status === 'ISSUED');
  console.log(`- company ${c.id} ruc=${c.ruc} "${c.name}" ${c.status}/${c.verification} certificates: ${certs.length} (${live.length} issued)`);
  if (isTestCompany(c)) {
    live.forEach((x) => plan.annul.push({ company: c, cert: x }));
    if (c.status === 'ACTIVE') plan.deactivate.push(c);
  }
}
for (const n of neighbors) {
  if (n.fullName.startsWith('smoke-') && n.status === 'ACTIVE') plan.neighbors.push(n);
}
console.log(`\nplan: annul ${plan.annul.length} certificate(s), deactivate ${plan.deactivate.length} company(ies), deactivate ${plan.neighbors.length} smoke neighbour(s)`);
plan.annul.forEach((p) => console.log(`  annul ${p.cert.id} of "${p.company.name}" (${p.cert.periodStart}..${p.cert.periodEnd})`));
plan.deactivate.forEach((c) => console.log(`  deactivate company "${c.name}" (${c.ruc})`));
plan.neighbors.forEach((n) => console.log(`  deactivate neighbour "${n.fullName}"`));

if (!apply) {
  console.log('\nsimulation only: nothing was changed (use --apply).');
  process.exit(0);
}

const reason = 'M7: limpieza de datos de prueba';
let failures = 0;
const step = async (label, method, url, body) => {
  const r = await call(method, url, token, body);
  const ok = r.status >= 200 && r.status < 300;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label} -> ${r.status}${ok ? '' : ` ${r.json?.code ?? ''}`}`);
  if (!ok) failures += 1;
};
for (const { company, cert } of plan.annul) {
  await step(`annul ${cert.id}`, 'POST', `${services.reporting}/tracked-companies/${company.id}/certificates/${cert.id}/annul`, { reason });
}
for (const c of plan.deactivate) {
  await step(`deactivate company ${c.ruc}`, 'PATCH', `${services.reporting}/tracked-companies/${c.id}/deactivate`, { reason });
}
for (const n of plan.neighbors) {
  await step(`deactivate neighbour ${n.fullName}`, 'PATCH', `${services.collection}/neighbors/${n.id}/deactivate`, { reason });
}
console.log(failures === 0 ? '\ndone' : `\n${failures} step(s) failed`);
process.exit(failures === 0 ? 0 : 1);
