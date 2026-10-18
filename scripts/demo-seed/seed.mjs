#!/usr/bin/env node
// Loads the approved fictitious demo data into the platform through its public APIs.
//
//   node scripts/demo-seed/seed.mjs                                  simulation: reads only
//   node scripts/demo-seed/seed.mjs --apply                          writes what is missing
//   node scripts/demo-seed/seed.mjs --apply --emit-certificate       also issues the certificate (irreversible)
//   node scripts/demo-seed/seed.mjs ... --extra-association          also a 2nd association, certification "por vencer"
//
// Credentials come only from ESG_STAFF_EMAIL / ESG_STAFF_PASSWORD. Neither the password nor the
// token is ever printed or stored. It talks only to the four known Render services (the same
// bases as the frontend's interceptor) and refuses any other URL. Idempotent: everything is
// looked up first, and what already exists is skipped. See README.md.

import { pathToFileURL } from 'node:url';

import * as DATA from './data.mjs';

/** The only bases this script will ever call: frontend/src/environments/environment.ts. */
export const SERVICE_BASES = Object.freeze({
  auth: 'https://esg-auth-service.onrender.com',
  recycler: 'https://esg-recycler-service.onrender.com',
  collection: 'https://esg-collection-service.onrender.com',
  reporting: 'https://esg-reporting-service.onrender.com',
});
const ALLOWED_BASES = Object.values(SERVICE_BASES);

/** As the interceptor's matchesServiceBase: a bare startsWith would accept "<base>.evil.example". */
export function isAllowedUrl(url) {
  return ALLOWED_BASES.some((base) => url === base || url.startsWith(`${base}/`) || url.startsWith(`${base}?`));
}

const USAGE = `Uso: node scripts/demo-seed/seed.mjs [--apply] [--emit-certificate] [--extra-association]
  (sin opciones)        simulación: solo lecturas, muestra qué crearía y qué ya existe
  --apply               crea lo que falta
  --emit-certificate    con --apply, emite el certificado del 17 al 30 de setiembre de 2026 (IRREVERSIBLE)
  --extra-association   también una 2.ª asociación demo con certificación por vencer
Credenciales: variables de entorno ESG_STAFF_EMAIL y ESG_STAFF_PASSWORD (nunca argumentos).`;

/** A refusal or a conflict: the run stops, saying why. Exit code 2 for a usage error, 1 otherwise. */
class Stop extends Error {
  constructor(message, exitCode = 1) {
    super(message);
    this.exitCode = exitCode;
  }
}
const usageError = (message) => new Stop(message, 2);

class HttpError extends Error {
  constructor(status, code, what) {
    super(`${what}: HTTP ${status}${code ? ` (${code})` : ''}`);
    this.status = status;
    this.code = code;
  }
  /** No answer, a timeout or a 5xx: a write may or may not have happened. */
  get uncertain() {
    return this.status === 0 || this.status === 408 || this.status >= 500;
  }
}

function todayInLima(now) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(now);
}
function addDays(isoDate, days) {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
const dmy = (isoDate) => isoDate.split('-').reverse().join('/');
const cents = (n) => Math.round(Number(n) * 100);
const hhmm = (time) => String(time).slice(0, 5);
const overlaps = (a, b) => a.periodStart <= b.periodEnd && b.periodStart <= a.periodEnd;
const sleepFor = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function parseArgs(argv) {
  const flags = { apply: false, emitCertificate: false, extraAssociation: false, help: false };
  for (const arg of argv) {
    if (arg === '--apply') flags.apply = true;
    else if (arg === '--emit-certificate') flags.emitCertificate = true;
    else if (arg === '--extra-association') flags.extraAssociation = true;
    else if (arg === '--help' || arg === '-h') flags.help = true;
    else if (/pass|token|secret|clave|contrase/i.test(arg)) {
      // Not even echoed back: it may be the password itself.
      throw usageError('Las credenciales no se pasan como argumento: usa ESG_STAFF_EMAIL y ESG_STAFF_PASSWORD.');
    } else throw usageError(`Opción o argumento desconocido (no se muestra: podría ser una credencial).\n${USAGE}`);
  }
  if (flags.emitCertificate && !flags.apply) {
    flags.emitCertificate = false;
    flags.simulateCertificate = true;
  }
  return flags;
}

/**
 * Runs the seed. Everything with side effects is injectable for the tests: `fetchImpl` (never the
 * network in tests), `bases`, `now`, `sleep` and `log` (one line at a time). Returns the exit code.
 */
export async function run({
  argv = [],
  env = {},
  fetchImpl = globalThis.fetch,
  bases = SERVICE_BASES,
  now = new Date(),
  sleep = sleepFor,
  log = console.log,
} = {}) {
  const secrets = [];
  // Every line goes through here: the password and the token are blanked even if a server echoed them.
  const say = (line = '') => {
    let text = String(line);
    for (const secret of secrets) {
      if (secret) text = text.split(secret).join('***');
    }
    log(text);
  };

  try {
    const flags = parseArgs(argv);
    if (flags.help) {
      say(USAGE);
      return 0;
    }
    for (const [service, base] of Object.entries(bases)) {
      if (!ALLOWED_BASES.includes(base)) {
        throw usageError(`Base no permitida para ${service}: ${base}. Este script solo habla con:\n  ${ALLOWED_BASES.join('\n  ')}`);
      }
    }
    for (const key of Object.keys(SERVICE_BASES)) {
      if (!bases[key]) throw usageError(`Falta la base del servicio ${key}.`);
    }

    const { ESG_STAFF_EMAIL: email, ESG_STAFF_PASSWORD: pass } = env;
    if (pass) secrets.push(pass);
    if (!email || !pass) {
      throw usageError('Faltan las variables de entorno ESG_STAFF_EMAIL y/o ESG_STAFF_PASSWORD (ver scripts/demo-seed/README.md).');
    }

    const api = client({ bases, fetchImpl, sleep, say, secrets });
    const seed = new Seeder({ api, flags, say, today: todayInLima(now) });

    say(flags.apply ? '== MODO APLICAR: se crearán los datos que falten ==' : '== SIMULACIÓN: solo lecturas, no se escribe nada (usa --apply para crear) ==');
    await api.wakeUp();
    await api.login(email, pass);
    say('Sesión iniciada.');
    await seed.all();
    say();
    say(seed.summary());
    return 0;
  } catch (error) {
    if (error instanceof Stop || error instanceof HttpError) {
      say(`DETENIDO: ${error.message}`);
      return error instanceof Stop ? error.exitCode : 1;
    }
    // Unexpected: the message only, never the stack with request objects in it.
    say(`ERROR inesperado: ${error instanceof Error ? error.message : String(error)}`);
    return 1;
  }
}

function client({ bases, fetchImpl, sleep, say, secrets }) {
  let bearer = null;

  async function call(service, method, path, { body, query, retries = method === 'GET' ? 3 : 0 } = {}) {
    let url = `${bases[service]}${path}`;
    if (query) {
      const params = new URLSearchParams(Object.entries(query).filter(([, v]) => v !== undefined && v !== null).map(([k, v]) => [k, String(v)]));
      url += `?${params}`;
    }
    if (!isAllowedUrl(url)) {
      throw new Stop(`URL no permitida, no se llama: ${url}`);
    }
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (bearer !== null && !path.startsWith('/auth/login') && !path.startsWith('/actuator/')) {
      headers.Authorization = `Bearer ${bearer}`;
    }
    const what = `${method} ${service}${path}`;
    for (let attempt = 0; ; attempt++) {
      let response;
      try {
        response = await fetchImpl(url, {
          method,
          headers,
          body: body === undefined ? undefined : JSON.stringify(body),
          // Never follow a redirect: it could carry the token somewhere else.
          redirect: 'error',
          signal: AbortSignal.timeout(90_000),
        });
      } catch {
        if (attempt < retries) {
          await sleep(10_000);
          continue;
        }
        throw new HttpError(0, null, `${what} sin respuesta`);
      }
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        // A gateway page, not JSON.
      }
      if (response.ok) return json;
      // Render answers 502/503/504 while a service wakes: reads retry, writes never do.
      if ([502, 503, 504].includes(response.status) && attempt < retries) {
        await sleep(10_000);
        continue;
      }
      throw new HttpError(response.status, json && typeof json.code === 'string' ? json.code : null, what);
    }
  }

  return {
    get: (service, path, query) => call(service, 'GET', path, { query }),
    post: (service, path, body) => call(service, 'POST', path, { body }),

    /** All pages of a list (100 per page, the services' max-page-size). */
    async all(service, path, query = {}) {
      const items = [];
      for (let page = 0; page < 100; page++) {
        const result = await call(service, 'GET', path, { query: { ...query, page, size: 100 } });
        items.push(...result.content);
        if (page + 1 >= result.totalPages) return items;
      }
      throw new Stop(`${service}${path}: más de 100 páginas, algo no cuadra.`);
    },

    /** Free Render instances sleep: wait for each liveness before anything else. */
    async wakeUp() {
      for (const service of Object.keys(SERVICE_BASES)) {
        for (let attempt = 1; ; attempt++) {
          try {
            await call(service, 'GET', '/actuator/health/liveness', { retries: 0 });
            break;
          } catch (error) {
            if (attempt >= 18) throw new Stop(`${service} no despertó en ~3 minutos (${error.message}). Vuelve a intentarlo luego.`);
            if (attempt === 1) say(`Despertando ${service} (Render free tier, puede tardar hasta ~2 min)…`);
            else if (attempt % 3 === 0) say(`  ${service} sigue despertando (${attempt * 10} s)…`);
            await sleep(10_000);
          }
        }
      }
    },

    async login(email, password) {
      try {
        const response = await call('auth', 'POST', '/auth/login', { body: { email, password } });
        if (!response || typeof response.accessToken !== 'string') throw new Stop('El login no devolvió un token.');
        bearer = response.accessToken;
        secrets.push(bearer);
      } catch (error) {
        if (error instanceof HttpError && error.status === 401) {
          throw new Stop('Credenciales rechazadas (AUTH-001). Revisa ESG_STAFF_EMAIL / ESG_STAFF_PASSWORD.');
        }
        throw error;
      }
    },
  };
}

class Seeder {
  constructor({ api, flags, say, today }) {
    this.api = api;
    this.flags = flags;
    this.say = say;
    this.today = today;
    this.created = 0;
    this.existing = 0;
    this.pending = 0;
  }

  /** Writes it, or says it would. Returns the created object, or null in a simulation. */
  async create(label, write) {
    if (!this.flags.apply) {
      this.pending++;
      this.say(`  + ${label}: se crearía`);
      return null;
    }
    try {
      const result = await write();
      this.created++;
      this.say(`  + ${label}: creado (id ${result.id})`);
      return result;
    } catch (error) {
      if (error instanceof HttpError && error.uncertain) {
        throw new Stop(`${label}: no se pudo confirmar si se creó (${error.message}). Vuelve a correr la simulación: si se creó, saldrá como "ya existe".`);
      }
      throw error;
    }
  }

  skip(label, id, note = '') {
    this.existing++;
    this.say(`  = ${label}: ya existe (id ${id})${note ? `. ${note}` : ''}`);
  }

  wouldFollow(label) {
    this.pending++;
    this.say(`  + ${label}: se crearía (depende de un alta anterior)`);
  }

  async all() {
    const association = await this.association(DATA.ASSOCIATION, '0. Asociación demo');
    await this.certification(association, DATA.CERTIFICATION, (c) => c.issuedDate === DATA.CERTIFICATION.issuedDate && c.expirationDate === DATA.CERTIFICATION.expirationDate);

    this.say('1–3. Vecinos, cronogramas y recojos de setiembre de 2026');
    const neighbors = await this.api.all('collection', '/neighbors');
    for (const demo of DATA.NEIGHBORS) {
      await this.neighbor(demo, neighbors, association);
    }

    const company = await this.company(association);
    await this.sigersol(association);
    await this.certificate(company);

    if (this.flags.extraAssociation) {
      const extra = await this.association(DATA.EXTRA_ASSOCIATION, 'Extra. Segunda asociación demo (certificación por vencer)');
      const expirationDate = addDays(this.today, DATA.EXTRA_EXPIRES_IN_DAYS);
      await this.certification(
        extra,
        { certificationType: DATA.EXTRA_CERTIFICATION_TYPE, issuedDate: addDays(expirationDate, -365), expirationDate },
        () => true,
      );
    }
  }

  async association(demo, title) {
    this.say(title);
    const label = `Asociación "${demo.name}" (RUC ${demo.ruc})`;
    const found = (await this.api.all('recycler', '/associations')).find((a) => a.ruc === demo.ruc);
    if (found) {
      const notes = [];
      if (found.name !== demo.name) notes.push(`Se usa la existente, llamada "${found.name}"`);
      if (found.status !== 'ACTIVE') notes.push(`Ojo: está ${found.status}`);
      this.skip(label, found.id, notes.join('. '));
      return found;
    }
    return this.create(label, () => this.api.post('recycler', '/associations', { name: demo.name, ruc: demo.ruc }));
  }

  async certification(association, demo, matches) {
    const label = `Certificación "${demo.certificationType}" ${dmy(demo.issuedDate)} al ${dmy(demo.expirationDate)}`;
    if (!association) return this.wouldFollow(label);
    const found = (await this.api.all('recycler', `/associations/${association.id}/certifications`)).find(
      (c) => c.certificationType === demo.certificationType && matches(c),
    );
    if (found) return this.skip(`Certificación "${found.certificationType}" ${dmy(found.issuedDate)} al ${dmy(found.expirationDate)}`, found.id);
    return this.create(label, () => this.api.post('recycler', `/associations/${association.id}/certifications`, demo));
  }

  async neighbor(demo, neighbors, association) {
    const label = `Vecino ${demo.fullName}, ${demo.address}, ${demo.district}`;
    let neighbor = neighbors.find((n) => n.fullName === demo.fullName && n.address === demo.address && n.district === demo.district) ?? null;
    if (neighbor) this.skip(label, neighbor.id);
    else neighbor = await this.create(label, () => this.api.post('collection', '/neighbors', { fullName: demo.fullName, address: demo.address, phone: null, district: demo.district }));

    const { dayOfWeek, time } = demo.schedule;
    const scheduleLabel = `  Cronograma ${dayOfWeek} ${time}`;
    let schedule = null;
    if (!neighbor) this.wouldFollow(scheduleLabel);
    else {
      const schedules = await this.api.all('collection', `/neighbors/${neighbor.id}/schedules`);
      const active = schedules.filter((s) => s.status === 'ACTIVE' && s.dayOfWeek === dayOfWeek);
      schedule = active.find((s) => hhmm(s.time) === time) ?? null;
      if (schedule) this.skip(scheduleLabel, schedule.id);
      else if (active.length > 0) {
        throw new Stop(`${demo.fullName} ya tiene un cronograma ACTIVO el ${dayOfWeek} a otra hora (${hhmm(active[0].time)}). No se toca: revísalo desde la pantalla del vecino.`);
      } else schedule = await this.create(scheduleLabel, () => this.api.post('collection', `/neighbors/${neighbor.id}/schedules`, { dayOfWeek, time }));
    }

    const records = neighbor && association
      ? await this.api.all('collection', `/neighbors/${neighbor.id}/collection-records`, { from: DATA.SIGERSOL.periodStart, to: DATA.SIGERSOL.periodEnd })
      : [];
    for (const c of demo.collections) {
      const recordLabel = `  Recojo ${dmy(c.collectionDate)}, ${c.weightKg.toFixed(2)} kg`;
      if (!neighbor || !association) {
        this.wouldFollow(recordLabel);
        continue;
      }
      const found = records.find((r) => r.collectionDate === c.collectionDate && cents(r.weightKg) === cents(c.weightKg) && r.associationId === association.id);
      if (found) {
        this.skip(recordLabel, found.id);
        continue;
      }
      await this.create(recordLabel, () =>
        this.api.post('collection', `/neighbors/${neighbor.id}/collection-records`, {
          scheduleId: schedule ? schedule.id : null,
          associationId: association.id,
          collectionDate: c.collectionDate,
          weightKg: c.weightKg,
        }),
      );
    }
  }

  async company(association) {
    this.say('4. Empresa demo');
    const demo = DATA.COMPANY;
    const label = `Empresa "${demo.name}" (RUC ${demo.ruc})`;
    const found = (await this.api.all('reporting', '/tracked-companies')).find((c) => c.ruc === demo.ruc);
    if (found) {
      if (association && found.associationId !== association.id) {
        throw new Stop(`${label} ya existe pero con otra asociación (${found.associationId}). No se toca.`);
      }
      this.skip(label, found.id, found.status !== 'ACTIVE' ? `Ojo: está ${found.status}` : '');
      return found;
    }
    if (!association) {
      this.wouldFollow(label);
      return null;
    }
    return this.create(label, () => this.api.post('reporting', '/tracked-companies', { name: demo.name, ruc: demo.ruc, associationId: association.id }));
  }

  async sigersol(association) {
    this.say('5. SIGERSOL (ingreso manual)');
    const demo = DATA.SIGERSOL;
    const label = `SIGERSOL ${dmy(demo.periodStart)} al ${dmy(demo.periodEnd)}: ${demo.hierarchyCompliancePercent.toFixed(2)} %, ${demo.officialKilosDeclared.toFixed(2)} kg declarados`;
    if (!association) return this.wouldFollow(label);
    const syncs = await this.api.all('reporting', '/sigersol-syncs', { associationId: association.id });
    const same = syncs.find((s) => s.periodStart === demo.periodStart && s.periodEnd === demo.periodEnd);
    if (same) {
      const differs = cents(same.hierarchyCompliancePercent) !== cents(demo.hierarchyCompliancePercent);
      return this.skip(label, same.id, differs ? `Ojo: registrado con ${same.hierarchyCompliancePercent} %` : '');
    }
    const clash = syncs.find((s) => overlaps(s, demo));
    if (clash) {
      throw new Stop(`La asociación ya tiene un registro SIGERSOL del ${dmy(clash.periodStart)} al ${dmy(clash.periodEnd)} que se superpone con setiembre (RPT-006). No se toca.`);
    }
    return this.create(label, () => this.api.post('reporting', '/sigersol-syncs', { associationId: association.id, ...demo }));
  }

  async certificate(company) {
    this.say('6. Certificado del 17 al 30 de setiembre de 2026 (IRREVERSIBLE)');
    const demo = DATA.CERTIFICATE;
    const period = { periodStart: demo.periodStart, periodEnd: demo.periodEnd };
    const label = `Certificado ${dmy(demo.periodStart)} al ${dmy(demo.periodEnd)}`;
    if (!this.flags.emitCertificate && !this.flags.simulateCertificate) {
      this.say(`  · ${label}: no se emite. Solo con --apply --emit-certificate.`);
      return;
    }
    if (!company) return this.wouldFollow(label);
    const certificates = await this.api.all('reporting', `/tracked-companies/${company.id}/certificates`);
    const same = certificates.find((c) => c.periodStart === demo.periodStart && c.periodEnd === demo.periodEnd);
    if (same) return this.skip(label, same.id, `${same.kilosTrazados} kg, ${same.hierarchyCompliancePercent} %`);
    const clash = certificates.find((c) => overlaps(c, period));
    if (clash) throw new Stop(`La empresa ya tiene un certificado del ${dmy(clash.periodStart)} al ${dmy(clash.periodEnd)} que se superpone (RPT-004).`);
    // The frontend's rules, kept here too.
    if (company.status === 'INACTIVE') throw new Stop('La empresa está inactiva: no se emiten certificados (regla del frontend).');
    if (demo.periodEnd >= this.today) throw new Stop(`El período aún no termina en Lima (hoy ${dmy(this.today)}).`);

    const summary = await this.api.get('reporting', `/tracked-companies/${company.id}/certificate-summary`, period);
    const ok = cents(summary.kilosTrazados) === cents(demo.expectedKilos) && summary.hierarchyCompliancePercent !== null && cents(summary.hierarchyCompliancePercent) === cents(demo.expectedCompliancePercent);
    const figures = `${summary.kilosTrazados} kg y ${summary.hierarchyCompliancePercent ?? 'sin SIGERSOL'} % (se esperan ${demo.expectedKilos} kg y ${demo.expectedCompliancePercent} %)`;
    if (!ok) {
      // Kilos reach reporting-service by event, a few seconds after each collection.
      const why = `${label}: el borrador da ${figures}. Si acabas de crear los recojos, espera un minuto y vuelve a correr.`;
      if (!this.flags.apply) {
        this.pending++;
        this.say(`  ! ${why}`);
        return;
      }
      throw new Stop(`no se emite. ${why}`);
    }
    if (!this.flags.apply) {
      this.pending++;
      this.say(`  + ${label}: se emitiría con ${summary.kilosTrazados} kg y ${summary.hierarchyCompliancePercent} %`);
      return;
    }
    await this.create(label, () => this.api.post('reporting', `/tracked-companies/${company.id}/certificates`, period));
  }

  summary() {
    if (this.flags.apply) return `Listo: ${this.created} creados, ${this.existing} ya existían.`;
    return `Simulación terminada: ${this.pending} altas pendientes, ${this.existing} ya existen. No se escribió nada.`;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await run({ argv: process.argv.slice(2), env: process.env });
}
