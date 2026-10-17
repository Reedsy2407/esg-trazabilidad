// node --test scripts/demo-seed/seed.test.mjs
// Unit tests with a fake fetch: an in-memory stand-in for the four services. Nothing here
// touches the network or production.

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { CERTIFICATE, EXTRA_ASSOCIATION } from './data.mjs';
import { SERVICE_BASES, isAllowedUrl, run } from './seed.mjs';

const EMAIL = 'personal.demo@example.test';
const PASSWORD = 'clave-clave-clave';
const TOKEN = 'token-token-token';
const ENV = { ESG_STAFF_EMAIL: EMAIL, ESG_STAFF_PASSWORD: PASSWORD };
const NOW = new Date('2026-10-10T15:00:00Z');

/**
 * The four services in memory, shaped like the real DTOs (PageResponse, problem+json with `code`).
 * `lagKilos`: certificate-summary doesn't see the collections yet (they reach reporting by event).
 */
function fakeBackend({ lagKilos = false, failPost = null } = {}) {
  const db = { associations: [], certifications: [], neighbors: [], schedules: [], records: [], companies: [], syncs: [], certificates: [] };
  const calls = [];
  let seq = 0;
  const id = () => `00000000-0000-7000-8000-${String(++seq).padStart(12, '0')}`;
  const json = (status, body) => new Response(body === undefined ? '' : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  const problem = (status, code) => json(status, { type: 'about:blank', title: '', status, detail: 'x', code });
  const page = (items, url) => {
    const p = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    return json(200, { content: items.slice(p * size, (p + 1) * size), page: p, size, totalElements: items.length, totalPages: Math.max(1, Math.ceil(items.length / size)) });
  };
  const service = (url) => Object.entries(SERVICE_BASES).find(([, base]) => url.origin === base)?.[0];

  async function fetch(input, init = {}) {
    const url = new URL(input);
    const method = init.method ?? 'GET';
    const body = init.body ? JSON.parse(init.body) : undefined;
    calls.push({ method, service: service(url), path: url.pathname, query: url.search, body, init });
    const svc = service(url);
    const p = url.pathname;
    if (!svc) throw new TypeError('fetch failed: unknown host');
    if (p === '/actuator/health/liveness') return json(200, { status: 'UP' });
    if (svc === 'auth' && p === '/auth/login') {
      return body.email === EMAIL && body.password === PASSWORD ? json(200, { accessToken: TOKEN }) : problem(401, 'AUTH-001');
    }
    if (init.headers?.Authorization !== `Bearer ${TOKEN}`) return problem(401, 'AUTH-000');
    if (method === 'POST' && failPost && p.endsWith(failPost.suffix)) return failPost.status >= 500 ? new Response('Bad Gateway', { status: failPost.status }) : problem(failPost.status, failPost.code);
    let m;
    if (svc === 'recycler' && p === '/associations') {
      if (method === 'GET') return page([...db.associations].sort((a, b) => a.name.localeCompare(b.name)), url);
      if (db.associations.some((a) => a.ruc === body.ruc)) return problem(409, 'ASO-002');
      const a = { id: id(), registrationNumber: null, address: null, contactEmail: null, contactPhone: null, status: 'ACTIVE', ...body };
      db.associations.push(a);
      return json(201, a);
    }
    if (svc === 'recycler' && (m = p.match(/^\/associations\/([^/]+)\/certifications$/))) {
      if (method === 'GET') return page(db.certifications.filter((c) => c.associationId === m[1]), url);
      const c = { id: id(), associationId: m[1], ...body, expired: false };
      db.certifications.push(c);
      return json(201, c);
    }
    if (svc === 'collection' && p === '/neighbors') {
      if (method === 'GET') return page(db.neighbors, url);
      const n = { id: id(), status: 'ACTIVE', ...body };
      db.neighbors.push(n);
      return json(201, n);
    }
    if (svc === 'collection' && (m = p.match(/^\/neighbors\/([^/]+)\/schedules$/))) {
      if (method === 'GET') return page(db.schedules.filter((s) => s.neighborId === m[1]), url);
      if (db.schedules.some((s) => s.neighborId === m[1] && s.dayOfWeek === body.dayOfWeek && s.status === 'ACTIVE')) return problem(409, 'COL-002');
      const s = { id: id(), neighborId: m[1], dayOfWeek: body.dayOfWeek, time: `${body.time}:00`, status: 'ACTIVE' };
      db.schedules.push(s);
      return json(201, s);
    }
    if (svc === 'collection' && (m = p.match(/^\/neighbors\/([^/]+)\/collection-records$/))) {
      if (method === 'GET') {
        const from = url.searchParams.get('from');
        const to = url.searchParams.get('to');
        return page(db.records.filter((r) => r.neighborId === m[1] && (!from || r.collectionDate >= from) && (!to || r.collectionDate <= to)), url);
      }
      const r = { id: id(), neighborId: m[1], ...body };
      db.records.push(r);
      return json(201, r);
    }
    if (svc === 'reporting' && p === '/tracked-companies') {
      if (method === 'GET') return page(db.companies, url);
      if (db.companies.some((c) => c.ruc === body.ruc)) return problem(409, 'RPT-002');
      const c = { id: id(), status: 'ACTIVE', ...body };
      db.companies.push(c);
      return json(201, c);
    }
    if (svc === 'reporting' && p === '/sigersol-syncs') {
      if (method === 'GET') return page(db.syncs.filter((s) => !url.searchParams.get('associationId') || s.associationId === url.searchParams.get('associationId')), url);
      if (!['DECLARED_BY_OPERATOR', 'REFERENCED_PROOF'].includes(body.origin)) return problem(400, 'VALIDATION_ERROR');
      const s = { id: id(), declaredAt: NOW.toISOString(), ...body };
      db.syncs.push(s);
      return json(201, s);
    }
    if (svc === 'reporting' && (m = p.match(/^\/tracked-companies\/([^/]+)\/certificate-summary$/))) {
      const company = db.companies.find((c) => c.id === m[1]);
      const start = url.searchParams.get('periodStart');
      const end = url.searchParams.get('periodEnd');
      const kilos = lagKilos ? 0 : db.records.filter((r) => r.associationId === company.associationId && r.collectionDate >= start && r.collectionDate <= end).reduce((sum, r) => sum + Math.round(r.weightKg * 100), 0) / 100;
      const sync = db.syncs.find((s) => s.associationId === company.associationId && s.periodStart <= start && s.periodEnd >= end);
      return json(200, { trackedCompanyId: m[1], periodStart: start, periodEnd: end, kilosTrazados: kilos, hierarchyCompliancePercent: sync ? sync.hierarchyCompliancePercent : null });
    }
    if (svc === 'reporting' && (m = p.match(/^\/tracked-companies\/([^/]+)\/certificates$/))) {
      if (method === 'GET') return page(db.certificates.filter((c) => c.trackedCompanyId === m[1]), url);
      const company = db.companies.find((c) => c.id === m[1]);
      const kilos = db.records.filter((r) => r.associationId === company.associationId && r.collectionDate >= body.periodStart && r.collectionDate <= body.periodEnd).reduce((sum, r) => sum + Math.round(r.weightKg * 100), 0) / 100;
      const c = { id: id(), trackedCompanyId: m[1], associationId: company.associationId, ...body, kilosTrazados: kilos, hierarchyCompliancePercent: 85.5 };
      db.certificates.push(c);
      return json(201, c);
    }
    return problem(404, 'NOT_FOUND');
  }

  return { fetch, db, calls, writes: () => calls.filter((c) => c.method === 'POST' && c.path !== '/auth/login') };
}

async function seed(backend, argv = [], env = ENV, extra = {}) {
  const lines = [];
  const code = await run({ argv, env, fetchImpl: backend.fetch, now: NOW, sleep: async () => {}, log: (l) => lines.push(l), ...extra });
  return { code, out: lines.join('\n') };
}

test('simulation (the default): only reads, says what it would create, writes nothing', async () => {
  const backend = fakeBackend();
  const { code, out } = await seed(backend);
  assert.equal(code, 0);
  assert.deepEqual(backend.writes(), []);
  assert.match(out, /SIMULACIÓN/);
  assert.match(out, /Asociación "Asociación de Recicladores Demo Norte \(ficticia\)" \(RUC 20999000011\): se crearía/);
  assert.match(out, /Vecino Lucía Paz \(demo\), Calle Demo 303, Independencia: se crearía/);
  assert.match(out, /Certificado 17\/09\/2026 al 30\/09\/2026: no se emite\. Solo con --apply --emit-certificate\./);
  assert.match(out, /16 altas pendientes, 0 ya existen\. No se escribió nada\./);
  // Every request carried no body but the login's, and none followed redirects.
  assert.ok(backend.calls.every((c) => c.init.redirect === 'error'));
  assert.ok(backend.calls.every((c) => c.path === '/auth/login' || c.body === undefined));
});

test('--apply creates everything in order, linking each collection to its schedule; no certificate without --emit-certificate', async () => {
  const backend = fakeBackend();
  const { code, out } = await seed(backend, ['--apply']);
  assert.equal(code, 0, out);
  const order = backend.writes().map((w) => `${w.service} ${w.path.replace(/[0-9a-f-]{36}/g, '{id}')}`);
  assert.deepEqual(order, [
    'recycler /associations',
    'recycler /associations/{id}/certifications',
    ...['carmen', 'jorge', 'lucia'].flatMap((_, i) => [
      'collection /neighbors',
      'collection /neighbors/{id}/schedules',
      ...Array([3, 2, 1][i]).fill('collection /neighbors/{id}/collection-records'),
    ]),
    'reporting /tracked-companies',
    'reporting /sigersol-syncs',
  ]);
  const { db } = backend;
  assert.equal(db.syncs.length, 1);
  assert.equal(db.syncs[0].origin, 'DECLARED_BY_OPERATOR');
  assert.equal(db.associations[0].name, 'Asociación de Recicladores Demo Norte (ficticia)');
  assert.deepEqual(db.certifications.map((c) => [c.certificationType, c.issuedDate, c.expirationDate]), [['Registro municipal (demo)', '2026-01-15', '2027-01-15']]);
  assert.equal(db.records.reduce((s, r) => s + Math.round(r.weightKg * 100), 0), 6695);
  for (const r of db.records) {
    const schedule = db.schedules.find((s) => s.id === r.scheduleId);
    assert.ok(schedule, 'every collection is linked to its schedule');
    assert.equal(schedule.neighborId, r.neighborId);
    assert.equal(r.associationId, db.associations[0].id);
  }
  assert.deepEqual(db.companies.map((c) => [c.name, c.ruc, c.associationId]), [['Empresa Demo Envases S.A.C. (ficticia)', '20492966658', db.associations[0].id]]);
  assert.deepEqual(db.syncs.map((s) => [s.periodStart, s.periodEnd, s.hierarchyCompliancePercent, s.officialKilosDeclared, s.sourceNote]), [['2026-09-01', '2026-09-30', 85.5, 70, 'Dato de demostración, no oficial']]);
  assert.deepEqual(db.certificates, []);
  assert.match(out, /Listo: 16 creados, 0 ya existían\./);
});

test('a second run does not duplicate anything', async () => {
  const backend = fakeBackend();
  await seed(backend, ['--apply']);
  const before = backend.writes().length;
  const { code, out } = await seed(backend, ['--apply']);
  assert.equal(code, 0, out);
  assert.equal(backend.writes().length, before);
  assert.match(out, /Listo: 0 creados, 16 ya existían\./);
  // And the simulation after it says nothing is pending.
  const sim = await seed(backend);
  assert.match(sim.out, /0 altas pendientes, 16 ya existen/);
});

test('--apply --emit-certificate issues September once, only when the draft is 35.3 kg and 85.5 %', async () => {
  const backend = fakeBackend();
  await seed(backend, ['--apply']);
  const sim = await seed(backend, ['--emit-certificate']);
  assert.match(sim.out, /se emitiría con 35\.3 kg y 85\.5 %/);
  assert.deepEqual(backend.db.certificates, []);

  const { code, out } = await seed(backend, ['--apply', '--emit-certificate']);
  assert.equal(code, 0, out);
  assert.deepEqual(backend.db.certificates.map((c) => [c.periodStart, c.periodEnd, c.kilosTrazados]), [[CERTIFICATE.periodStart, CERTIFICATE.periodEnd, 35.3]]);
  const again = await seed(backend, ['--apply', '--emit-certificate']);
  assert.equal(again.code, 0);
  assert.equal(backend.db.certificates.length, 1);
  assert.match(again.out, /Certificado 17\/09\/2026 al 30\/09\/2026: ya existe/);
});

test('the certificate is refused while the kilos have not reached reporting yet', async () => {
  const backend = fakeBackend({ lagKilos: true });
  const { code, out } = await seed(backend, ['--apply', '--emit-certificate']);
  assert.equal(code, 1);
  assert.match(out, /DETENIDO: no se emite\. .*el borrador da 0 kg y 85\.5 %/);
  assert.equal(backend.writes().filter((w) => w.path.endsWith('/certificates')).length, 0);
});

test('an INACTIVE company gets no certificate (the frontend rule)', async () => {
  const backend = fakeBackend();
  await seed(backend, ['--apply']);
  backend.db.companies[0].status = 'INACTIVE';
  const { code, out } = await seed(backend, ['--apply', '--emit-certificate']);
  assert.equal(code, 1);
  assert.match(out, /La empresa está inactiva/);
  assert.deepEqual(backend.db.certificates, []);
});

test('refuses any base other than the four known services, before any request', async () => {
  for (const auth of ['https://evil.example', 'https://esg-auth-service.onrender.com.evil.example', 'http://localhost:8081']) {
    const backend = fakeBackend();
    const { code, out } = await seed(backend, ['--apply'], ENV, { bases: { ...SERVICE_BASES, auth } });
    assert.equal(code, 2);
    assert.match(out, /Base no permitida para auth/);
    assert.deepEqual(backend.calls, []);
  }
  assert.equal(isAllowedUrl('https://esg-auth-service.onrender.com/auth/login'), true);
  assert.equal(isAllowedUrl('https://esg-auth-service.onrender.com.evil.example/auth/login'), false);
  assert.equal(isAllowedUrl('https://esg-auth-service.onrender.com@evil.example/'), false);
});

test('the password and the token never appear in any output, whatever happens', async () => {
  const outputs = [];
  // A full run, a failed login, an uncertain write, a server that echoes the token, and both as arguments.
  outputs.push((await seed(fakeBackend(), ['--apply', '--emit-certificate'])).out);
  outputs.push((await seed(fakeBackend(), [], { ...ENV, ESG_STAFF_PASSWORD: `${PASSWORD}-mala` })).out);
  outputs.push((await seed(fakeBackend({ failPost: { suffix: '/neighbors', status: 502 } }), ['--apply'])).out);
  const echoing = fakeBackend({ failPost: { suffix: '/tracked-companies', status: 409, code: `RPT-002 ${TOKEN} ${PASSWORD}` } });
  outputs.push((await seed(echoing, ['--apply'])).out);
  outputs.push((await seed(fakeBackend(), [`--password=${PASSWORD}`])).out);
  outputs.push((await seed(fakeBackend(), [PASSWORD])).out);
  outputs.push((await seed(fakeBackend(), ['-Xq7wz'], { ...ENV, ESG_STAFF_PASSWORD: '-Xq7wz' })).out);
  assert.ok(!outputs.at(-1).includes('-Xq7wz'), outputs.at(-1));
  for (const out of outputs) {
    assert.ok(!out.includes(PASSWORD), out);
    assert.ok(!out.includes(TOKEN), out);
  }
  assert.match(outputs[1], /Credenciales rechazadas/);
  assert.match(outputs[2], /no se pudo confirmar si se creó/);
  assert.match(outputs[4], /Las credenciales no se pasan como argumento/);
});

test('without the environment variables it stops before any request', async () => {
  const backend = fakeBackend();
  const { code, out } = await seed(backend, [], { ESG_STAFF_EMAIL: EMAIL });
  assert.equal(code, 2);
  assert.match(out, /Faltan las variables de entorno/);
  assert.deepEqual(backend.calls, []);
});

test('--extra-association: a second association whose certification is "por vencer" (< 30 days)', async () => {
  const backend = fakeBackend();
  const { code, out } = await seed(backend, ['--apply', '--extra-association']);
  assert.equal(code, 0, out);
  const extra = backend.db.associations.find((a) => a.ruc === EXTRA_ASSOCIATION.ruc);
  assert.equal(extra.name, 'Asociación de Recicladores Demo Sur (ficticia)');
  const cert = backend.db.certifications.find((c) => c.associationId === extra.id);
  assert.equal(cert.expirationDate, '2026-10-30');
  assert.equal(cert.issuedDate, '2025-10-30');
  // Idempotent too, even on another day.
  await seed(backend, ['--apply', '--extra-association'], ENV, { now: new Date('2026-10-12T15:00:00Z') });
  assert.equal(backend.db.certifications.filter((c) => c.associationId === extra.id).length, 1);
  // Off by default.
  const plain = fakeBackend();
  await seed(plain, ['--apply']);
  assert.equal(plain.db.associations.length, 1);
});
