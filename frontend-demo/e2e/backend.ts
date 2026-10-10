import { Page, Route } from '@playwright/test';

/**
 * Stands in for the four Render services at the network level. Response
 * bodies copy the real DTO shapes field by field (LoginResponse,
 * PageResponse<TrackedCompanyResponse>, RFC 7807 problems with `code`); the
 * company data is invented but plausible for Lima.
 */
export const COMPANIES = [
  {
    id: '0192f3a8-1c2d-7e4f-8a9b-0c1d2e3f4a5b',
    name: 'Envases del Pacífico S.A.C.',
    ruc: '20512345678',
    associationId: '0192f3a8-0000-7000-8000-000000000001',
    status: 'ACTIVE',
  },
  {
    id: '0192f3a8-2c2d-7e4f-8a9b-0c1d2e3f4a5c',
    name: 'Reciclajes Industriales Huachipa S.A.',
    ruc: '20603829104',
    associationId: '0192f3a8-0000-7000-8000-000000000002',
    status: 'INACTIVE',
  },
] as const;

export const PASSWORD = 'clave-correcta';
export const EMAIL = 'ana.paredes@asociacion.pe';

export function jwtFor(email: string, expiresInSeconds = 3600): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  // As auth-service's JwtIssuer: sub, email, iat and exp (one hour after iat there).
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + expiresInSeconds;
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'staff-1', email, iat, exp })}.signature`;
}

const problem = (route: Route, status: number, code: string, detail: string, headers: Record<string, string> = {}) =>
  route.fulfill({
    status,
    contentType: 'application/problem+json',
    headers,
    body: JSON.stringify({ type: 'about:blank', title: '', status, detail, code }),
  });

/**
 * A 5xx as the real platform sends it: GlobalExceptionHandler puts no `code` on an unhandled
 * 500, and Render's proxy answers a waking service with a bare 502/503/504.
 */
const gatewayError = (route: Route, status: number) =>
  route.fulfill({ status, contentType: 'text/plain', body: 'Service Unavailable' });

/** 14 whole-month certificates for COMPANIES[0] (Nov 2023 - Dec 2024), shaped like EsgCertificateResponse. */
export const CERTIFICATES = Array.from({ length: 14 }, (_, i) => {
  const year = 2023 + Math.floor((10 + i) / 12);
  const month = ((10 + i) % 12) + 1;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const mm = String(month).padStart(2, '0');
  const issued = new Date(Date.UTC(year, month, 5, 15, 0, 0)); // the 5th of the next month
  return {
    id: `0192f3a8-c000-7000-8000-${String(i).padStart(12, '0')}`,
    trackedCompanyId: COMPANIES[0].id,
    associationId: COMPANIES[0].associationId,
    companyName: COMPANIES[0].name,
    companyRuc: COMPANIES[0].ruc,
    periodStart: `${year}-${mm}-01`,
    periodEnd: `${year}-${mm}-${lastDay}`,
    kilosTrazados: [8420.25, 9105.5, 7988, 10230.75, 11002.4, 9876.1, 12480.5, 13050, 11870.3, 12210.8, 14105.6, 12990.45, 13420.1, 12480.5][i],
    hierarchyCompliancePercent: [82.5, 84, 79.5, 86.2, 88, 85.5, 87.5, 90.1, 89.3, 91, 92.4, 90.8, 93.2, 87.5][i],
    issuedAt: issued.toISOString(),
  };
}).reverse(); // the API lists newest issued first

/** A certificate that exists, but under COMPANIES[1]: asking for it under COMPANIES[0] is a 404. */
export const FOREIGN_CERTIFICATE = {
  ...CERTIFICATES[0],
  id: '0192f3a8-c000-7000-8000-0000000000ff',
  trackedCompanyId: COMPANIES[1].id,
  associationId: COMPANIES[1].associationId,
  companyName: COMPANIES[1].name,
  companyRuc: COMPANIES[1].ruc,
};

export interface BackendOptions {
  /** Answer the login with 429 AUTH-004 and this Retry-After. */
  lockedFor?: number;
  /** Reject every token on protected endpoints (expired session). */
  rejectTokens?: boolean;
  /** GET .../certificates fails with this status (partial failure of the company page). */
  certificatesStatus?: number;
  /** The summary answers hierarchyCompliancePercent: null (no SIGERSOL sync covers the period). */
  summaryComplianceNull?: boolean;
  /** Content-Disposition sent with PDF/CSV downloads (default: the backend's own form). */
  downloadDisposition?: string | null;
  /** PDF/CSV downloads fail with this HTTP status, or 'network' to drop the connection. */
  downloadFailure?: number | 'network';
  /** A 200 download answered as the SPA's HTML page (a proxy fallback), not the file. */
  downloadAsHtml?: boolean;
  /** POST .../collection-records answers this problem instead of 201. */
  collectionFailure?: { status: number; code: string | null };
  /** GET /associations fails with this status. */
  associationsStatus?: number;
  /** GET /associations/{id}/certifications fails with this status. */
  certificationsStatus?: number;
  /** How many associations GET /associations lists (default ASSOCIATIONS; more are generated, for paging). */
  associationCount?: number;
  /** Every request that reached the catch-all (no fake route): the test asserts it stays empty. */
  unmocked?: string[];
  /**
   * Answers to successive GET /tracked-companies calls, in order: 'empty' (no companies yet),
   * 'full' (COMPANIES) or 'fail' (503); after the list runs out, 'full'.
   */
  companyLists?: ('empty' | 'full' | 'fail')[];
  /** Delay before answering GET /tracked-companies, to observe the loading state. */
  companyListDelayMs?: number;
  /** How many neighbours GET /neighbors knows (default NEIGHBORS; more are generated, for paging). */
  neighborCount?: number;
  /** GET /neighbors, .../schedules or .../collection-records fail with this status (a bare 5xx). */
  neighborsStatus?: number;
  schedulesStatus?: number;
  recordsStatus?: number;
  /** POST /neighbors answers this instead of 201 (code null: a bare gateway answer). */
  neighborFailure?: { status: number; code: string | null };
  /** POST /tracked-companies answers this instead of 201 (code null: a bare gateway answer). */
  companyFailure?: { status: number; code: string | null };
  /** GET /sigersol-syncs fails with this status (a bare 5xx). */
  sigersolStatus?: number;
  /** POST /sigersol-syncs answers this instead of 201 (code null: a bare gateway answer). */
  sigersolFailure?: { status: number; code: string | null };
  /** POST .../certificates answers this instead of 201 (code null: a bare gateway answer). */
  issueFailure?: { status: number; code: string | null };
  /** Lifetime of the tokens the login issues (exp - iat); auth-service's is one hour. */
  tokenLifetimeSeconds?: number;
  /** GET /auth/me fails with this status (a bare 5xx). */
  meStatus?: number;
  /**
   * Answers to successive POST /auth/staff-users, in order: a status for a bare gateway failure
   * (nothing created), 'applied' (created, but the answer is a 504), or 'pass' (the normal answer).
   */
  staffFailures?: (number | 'applied' | 'pass')[];
  /** Every write (POST/PATCH) the fake services received, in order. */
  writes?: { method: string; path: string; body: unknown }[];
  /** Every POST body received by collection-records, for the test to inspect. */
  collectionPosts?: { neighborId: string; body: unknown }[];
}

/** collection-service neighbors (NeighborResponse), sorted by name as the API does. */
export const NEIGHBORS = [
  { id: '0192f3a8-a000-7000-8000-000000000002', fullName: 'Luis Ramos Huamán', phone: '912345678', address: 'Av. Perú 455', district: 'San Martín de Porres', status: 'INACTIVE' },
  { id: '0192f3a8-a000-7000-8000-000000000001', fullName: 'Rosa Quispe Mamani', phone: '987654321', address: 'Jr. Huallaga 120', district: 'Cercado de Lima', status: 'ACTIVE' },
] as const;

/** recycler-service associations (AssociationResponse). */
export const ASSOCIATIONS = [
  { id: COMPANIES[0].associationId, name: 'Asociación Recicla Rímac', ruc: '20601234567', registrationNumber: 'REG-2021-0145', address: 'Av. Amancaes 300, Rímac', contactEmail: 'contacto@reciclarimac.pe', contactPhone: '014567890', status: 'ACTIVE' },
  { id: COMPANIES[1].associationId, name: 'Recicladores Unidos de Comas', ruc: '20609876543', registrationNumber: 'REG-2019-0087', address: 'Av. Túpac Amaru 1200, Comas', contactEmail: 'info@recicladorescomas.pe', contactPhone: '015551234', status: 'SUSPENDED' },
] as const;

/** ASSOCIATIONS, plus generated ones up to `count` (for paging), sorted by name as the API does. */
export function allAssociations(count: number = ASSOCIATIONS.length) {
  const extra = Array.from({ length: Math.max(0, count - ASSOCIATIONS.length) }, (_, i) => ({
    id: `0192f3a8-0000-7000-8000-${String(100 + i).padStart(12, '0')}`,
    name: `Asociación de Recicladores Lima ${String(i + 1).padStart(2, '0')}`,
    ruc: `206${String(10000000 + i).padStart(8, '0')}`,
    registrationNumber: null,
    address: null,
    contactEmail: null,
    contactPhone: null,
    status: 'ACTIVE',
  }));
  return [...ASSOCIATIONS, ...extra].sort((a, b) => a.name.localeCompare(b.name));
}

/** A LocalDate `days` from today in Lima (the browser runs with timezoneId America/Lima). */
export function limaDate(days: number): string {
  const now = new Date(Date.now() - 5 * 3_600_000); // Lima is UTC-5, no DST
  now.setUTCDate(now.getUTCDate() + days);
  return now.toISOString().slice(0, 10);
}

/**
 * recycler-service certifications (CertificationResponse) of ASSOCIATIONS[0], soonest expiration
 * first as the API sorts them: one expired (the backend's `expired`), one expiring in 12 days,
 * one valid for a year.
 */
export const CERTIFICATIONS = [
  { id: '0192f3a8-e000-7000-8000-000000000001', associationId: ASSOCIATIONS[0].id, certificationType: 'Registro municipal de recicladores', issuedDate: limaDate(-400), expirationDate: limaDate(-35), expired: true },
  { id: '0192f3a8-e000-7000-8000-000000000002', associationId: ASSOCIATIONS[0].id, certificationType: 'Autorización de segregación en fuente', issuedDate: limaDate(-353), expirationDate: limaDate(12), expired: false },
  { id: '0192f3a8-e000-7000-8000-000000000003', associationId: ASSOCIATIONS[0].id, certificationType: 'Formalización MINAM', issuedDate: limaDate(-30), expirationDate: limaDate(335), expired: false },
];

/**
 * collection-service schedules (CollectionScheduleResponse) per neighbor, in
 * the API's real order: dayOfWeek is stored as text and sorted
 * alphabetically (MONDAY, THURSDAY, WEDNESDAY). The page re-sorts by weekday.
 */
export const SCHEDULES: Record<string, { id: string; neighborId: string; dayOfWeek: string; time: string; status: string }[]> = {
  [NEIGHBORS[1].id]: [
    { id: '0192f3a8-b000-7000-8000-000000000001', neighborId: NEIGHBORS[1].id, dayOfWeek: 'MONDAY', time: '08:00:00', status: 'ACTIVE' },
    { id: '0192f3a8-b000-7000-8000-000000000002', neighborId: NEIGHBORS[1].id, dayOfWeek: 'THURSDAY', time: '15:30:00', status: 'PAUSED' },
    { id: '0192f3a8-b000-7000-8000-000000000003', neighborId: NEIGHBORS[1].id, dayOfWeek: 'WEDNESDAY', time: '07:00:00', status: 'ACTIVE' },
  ],
};

interface NeighborRow {
  id: string;
  fullName: string;
  phone: string | null;
  address: string;
  district: string | null;
  status: string;
}
interface ScheduleRow {
  id: string;
  neighborId: string;
  dayOfWeek: string;
  time: string;
  status: string;
}
interface RecordRow {
  id: string;
  neighborId: string;
  scheduleId: string | null;
  associationId: string;
  collectionDate: string;
  weightKg: number;
}

/** Generated neighbours up to `count` in all (for paging), each in a district of Lima. */
function extraNeighbors(count: number = NEIGHBORS.length): NeighborRow[] {
  const districts = ['Comas', 'Rímac', 'Los Olivos', 'Independencia'];
  return Array.from({ length: Math.max(0, count - NEIGHBORS.length) }, (_, i) => ({
    id: `0192f3a8-a000-7000-8000-${String(100 + i).padStart(12, '0')}`,
    fullName: `Vecino de prueba ${String(i + 1).padStart(2, '0')}`,
    phone: null,
    address: `Calle ${i + 1}`,
    district: districts[i % districts.length],
    status: 'ACTIVE',
  }));
}

/** Rosa's past collections (CollectionRecordResponse). */
export const RECORDS: readonly RecordRow[] = [
  { id: '0192f3a8-d000-7000-8000-000000000101', neighborId: NEIGHBORS[1].id, scheduleId: SCHEDULES[NEIGHBORS[1].id][0].id, associationId: ASSOCIATIONS[0].id, collectionDate: '2026-10-05', weightKg: 12.5 },
  { id: '0192f3a8-d000-7000-8000-000000000102', neighborId: NEIGHBORS[1].id, scheduleId: null, associationId: ASSOCIATIONS[0].id, collectionDate: '2026-09-28', weightKg: 8.25 },
  { id: '0192f3a8-d000-7000-8000-000000000103', neighborId: NEIGHBORS[1].id, scheduleId: null, associationId: ASSOCIATIONS[1].id, collectionDate: '2026-08-14', weightKg: 20 },
];

interface SyncRow {
  id: string;
  associationId: string;
  periodStart: string;
  periodEnd: string;
  hierarchyCompliancePercent: number;
  officialKilosDeclared: number | null;
  declaredAt: string;
  sourceNote: string | null;
}

/** SIGERSOL records (SigersolSyncResponse) of ASSOCIATIONS[0], typed in by hand. */
export const SIGERSOL_SYNCS: readonly SyncRow[] = [
  { id: '0192f3a8-f000-7000-8000-000000000001', associationId: ASSOCIATIONS[0].id, periodStart: '2024-12-01', periodEnd: '2024-12-31', hierarchyCompliancePercent: 87.5, officialKilosDeclared: 12500, declaredAt: '2025-01-03T14:00:00Z', sourceNote: 'Reporte anual SIGERSOL, copiado por A. Paredes' },
  { id: '0192f3a8-f000-7000-8000-000000000002', associationId: ASSOCIATIONS[0].id, periodStart: '2026-09-01', periodEnd: '2026-09-30', hierarchyCompliancePercent: 91.25, officialKilosDeclared: null, declaredAt: '2026-10-02T15:30:00Z', sourceNote: null },
];

export async function fakeBackend(page: Page, options: BackendOptions = {}): Promise<void> {
  // Registered first, so it only answers what no route below handles. ng serve proxies /svc/* to
  // the real services: an unmocked call must never reach production, least of all a write.
  await page.route('**/svc/**', (route) => {
    options.unmocked?.push(`${route.request().method()} ${route.request().url()}`);
    return route.fulfill({ status: 599, contentType: 'text/plain', body: 'Ruta no simulada en el backend falso' });
  });
  await page.route('**/svc/*/actuator/health/liveness', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }),
  );

  await page.route('**/svc/auth/auth/login', async (route) => {
    if (options.lockedFor !== undefined) {
      return problem(route, 429, 'AUTH-004', 'Demasiados intentos de inicio de sesión, intenta más tarde', {
        'Retry-After': String(options.lockedFor),
      });
    }
    const body: unknown = route.request().postDataJSON();
    const { email, password } = body as { email: string; password: string };
    if (password !== PASSWORD) {
      return problem(route, 401, 'AUTH-001', 'Credenciales inválidas');
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: jwtFor(email, options.tokenLifetimeSeconds) }) });
  });

  // auth-service staff accounts, with state. Emails are stored lower-cased, as the real service does.
  const staff: { id: string; email: string; fullName: string; active: boolean; createdAt: string }[] = [
    { id: 'staff-1', email: EMAIL, fullName: 'Ana Paredes Quispe', active: true, createdAt: '2026-08-14T15:20:00Z' },
  ];
  await page.route('**/svc/auth/auth/me', (route) => {
    const authorization = route.request().headers()['authorization'];
    if (options.rejectTokens || authorization === undefined || !authorization.startsWith('Bearer ')) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    if (options.meStatus !== undefined) {
      return gatewayError(route, options.meStatus);
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(staff[0]) });
  });
  await page.route('**/svc/auth/auth/staff-users', (route) => {
    const authorization = route.request().headers()['authorization'];
    if (options.rejectTokens || authorization === undefined || !authorization.startsWith('Bearer ')) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const body = route.request().postDataJSON() as { email?: string; password?: string; fullName?: string };
    options.writes?.push({ method: 'POST', path: new URL(route.request().url()).pathname, body });
    const failure = options.staffFailures?.shift();
    if (failure !== undefined && failure !== 'pass') {
      // 'applied': the account IS created, but the answer never arrives (a gateway timeout).
      if (failure === 'applied' && body.email && body.password && body.fullName) {
        staff.push({ id: `staff-${staff.length + 1}`, email: body.email.toLowerCase(), fullName: body.fullName, active: true, createdAt: new Date().toISOString() });
        return gatewayError(route, 504);
      }
      return gatewayError(route, failure === 'applied' ? 504 : failure);
    }
    if (!body.email || !body.password?.trim() || !body.fullName?.trim()) {
      return problem(route, 400, 'VALIDATION_ERROR', 'email, password o fullName vacíos');
    }
    // @Email accepts "a@b"; the domain's own regex (a dot in the domain) then throws: a bare 500.
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email)) {
      return gatewayError(route, 500);
    }
    if (staff.some((u) => u.email === body.email!.toLowerCase())) {
      return problem(route, 409, 'AUTH-002', 'Ya existe una cuenta de staff con ese email');
    }
    const created = { id: `staff-${staff.length + 1}`, email: body.email.toLowerCase(), fullName: body.fullName, active: true, createdAt: new Date().toISOString() };
    staff.push(created);
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) });
  });

  const authorized = (route: Route) => {
    const authorization = route.request().headers()['authorization'];
    return !options.rejectTokens && authorization !== undefined && authorization.startsWith('Bearer ');
  };
  const pageOf = <T,>(content: readonly T[]) => ({
    content,
    page: 0,
    size: 100, // the services' max-page-size
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
  });

  await page.route('**/svc/recycler/associations**', (route) => {
    if (!authorized(route)) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const url = new URL(route.request().url());
    const [, id, sub] = /\/associations(?:\/([^/]+))?(?:\/([^/]+))?$/.exec(url.pathname) ?? [];
    if (id === undefined) {
      if (options.associationsStatus !== undefined) {
        return gatewayError(route, options.associationsStatus);
      }
      const status = url.searchParams.get('status');
      if (status !== null && status !== 'ACTIVE' && status !== 'SUSPENDED') {
        return problem(route, 400, 'VALIDATION_ERROR', `status: valor inválido '${status}' para el tipo AssociationStatus`);
      }
      const all = allAssociations(options.associationCount).filter((a) => status === null || a.status === status);
      const size = Math.min(Number(url.searchParams.get('size') ?? 20), 100);
      const number = Number(url.searchParams.get('page') ?? 0);
      return json(200, {
        content: all.slice(number * size, number * size + size),
        page: number,
        size,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      });
    }
    if (!/^[0-9a-f-]{36}$/.test(id)) {
      return problem(route, 400, 'VALIDATION_ERROR', `id: valor inválido '${id}' para el tipo UUID`);
    }
    const association = allAssociations(options.associationCount).find((a) => a.id === id);
    if (sub === 'certifications') {
      // The backend doesn't check the association here: an unknown one lists nothing.
      if (options.certificationsStatus !== undefined) {
        return gatewayError(route, options.certificationsStatus);
      }
      return json(200, pageOf(CERTIFICATIONS.filter((c) => c.associationId === id)));
    }
    if (association === undefined) {
      return problem(route, 404, 'ASO-001', 'Asociación no encontrada');
    }
    if (sub === undefined) {
      return json(200, association);
    }
    return problem(route, 404, 'NOT-FOUND', 'Ruta no simulada');
  });

  // collection-service with state, per page: neighbours created and schedules changed by a test
  // are what the next GET answers, as the real service would.
  const neighbors: NeighborRow[] = [...NEIGHBORS.map((n) => ({ ...n })), ...extraNeighbors(options.neighborCount)];
  const schedules: ScheduleRow[] = Object.values(SCHEDULES)
    .flat()
    .map((sc) => ({ ...sc }));
  const records: RecordRow[] = RECORDS.map((r) => ({ ...r }));
  let created = 0;

  await page.route('**/svc/collection/neighbors**', (route) => {
    if (!authorized(route)) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const request = route.request();
    const method = request.method();
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const url = new URL(request.url());
    const [, neighborId, sub, subId, action] =
      /\/neighbors(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?$/.exec(url.pathname) ?? [];
    const paged = <T,>(all: T[]) => {
      const size = Math.min(Number(url.searchParams.get('size') ?? 20), 100);
      const number = Number(url.searchParams.get('page') ?? 0);
      return {
        content: all.slice(number * size, number * size + size),
        page: number,
        size,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      };
    };

    if (neighborId === undefined) {
      if (method === 'POST') {
        const body = request.postDataJSON() as Record<string, unknown>;
        options.writes?.push({ method, path: url.pathname, body });
        if (options.neighborFailure !== undefined) {
          return options.neighborFailure.code === null
            ? gatewayError(route, options.neighborFailure.status)
            : problem(route, options.neighborFailure.status, options.neighborFailure.code, 'Rechazado');
        }
        const fullName = body['fullName'];
        const address = body['address'];
        if (typeof fullName !== 'string' || fullName.trim() === '' || typeof address !== 'string' || address.trim() === '') {
          return problem(route, 400, 'VALIDATION_ERROR', 'fullName: no debe estar vacío');
        }
        created += 1;
        const neighbor: NeighborRow = {
          id: `0192f3a8-a000-7000-8000-${String(900 + created).padStart(12, '0')}`,
          fullName,
          phone: (body['phone'] as string | null) ?? null,
          address,
          district: (body['district'] as string | null) ?? null,
          status: 'ACTIVE',
        };
        neighbors.push(neighbor);
        return json(201, neighbor);
      }
      if (options.neighborsStatus !== undefined) {
        return gatewayError(route, options.neighborsStatus);
      }
      const status = url.searchParams.get('status');
      const district = url.searchParams.get('district');
      // The API sorts by fullName; district is an exact, case-sensitive match.
      const all = neighbors
        .filter((n) => (status === null || n.status === status) && (district === null || n.district === district))
        .sort((a, b) => a.fullName.localeCompare(b.fullName));
      return json(200, paged(all));
    }

    const neighbor = neighbors.find((n) => n.id === neighborId);
    if (sub === undefined) {
      return neighbor === undefined ? problem(route, 404, 'COL-001', 'Vecino no encontrado') : json(200, neighbor);
    }

    if (sub === 'schedules') {
      const own = schedules.filter((sc) => sc.neighborId === neighborId);
      if (subId === undefined && method === 'GET') {
        if (options.schedulesStatus !== undefined) {
          return gatewayError(route, options.schedulesStatus);
        }
        // Stored as text: the API sorts dayOfWeek alphabetically.
        return json(200, paged([...own].sort((a, b) => a.dayOfWeek.localeCompare(b.dayOfWeek))));
      }
      if (subId === undefined && method === 'POST') {
        const body = request.postDataJSON() as { dayOfWeek: string; time: string };
        options.writes?.push({ method, path: url.pathname, body });
        if (neighbor === undefined) {
          return problem(route, 404, 'COL-001', 'Vecino no encontrado');
        }
        if (own.some((sc) => sc.status === 'ACTIVE' && sc.dayOfWeek === body.dayOfWeek)) {
          return problem(route, 409, 'COL-002', 'Conflicto de horario de recojo');
        }
        const schedule: ScheduleRow = {
          id: `0192f3a8-b000-7000-8000-${String(900 + schedules.length).padStart(12, '0')}`,
          neighborId,
          dayOfWeek: body.dayOfWeek,
          time: `${body.time}:00`,
          status: 'ACTIVE',
        };
        schedules.push(schedule);
        return json(201, schedule);
      }
      const schedule = own.find((sc) => sc.id === subId);
      if (method === 'PATCH' && action !== undefined) {
        options.writes?.push({ method, path: url.pathname, body: null });
        if (schedule === undefined) {
          return problem(route, 404, 'COL-006', 'Programación de recojo no encontrada');
        }
        const allowed: Record<string, string[]> = { pause: ['ACTIVE'], cancel: ['ACTIVE', 'PAUSED'], reactivate: ['PAUSED'] };
        if (!allowed[action]?.includes(schedule.status)) {
          return problem(route, 409, 'COL-008', 'Transición de estado no permitida para esta programación');
        }
        if (
          action === 'reactivate' &&
          own.some((sc) => sc.id !== schedule.id && sc.status === 'ACTIVE' && sc.dayOfWeek === schedule.dayOfWeek)
        ) {
          return problem(route, 409, 'COL-002', 'Conflicto de horario de recojo');
        }
        schedule.status = action === 'pause' ? 'PAUSED' : action === 'cancel' ? 'CANCELLED' : 'ACTIVE';
        return json(200, schedule);
      }
    }

    if (sub === 'collection-records' && method === 'GET') {
      if (options.recordsStatus !== undefined) {
        return gatewayError(route, options.recordsStatus);
      }
      const from = url.searchParams.get('from');
      const to = url.searchParams.get('to');
      const all = records
        .filter(
          (r) =>
            r.neighborId === neighborId && (from === null || r.collectionDate >= from) && (to === null || r.collectionDate <= to),
        )
        .sort((a, b) => b.collectionDate.localeCompare(a.collectionDate));
      return json(200, paged(all));
    }

    if (sub === 'collection-records' && method === 'POST') {
      if (neighbor === undefined) {
        return problem(route, 404, 'COL-001', 'Vecino no encontrado');
      }
      const body = request.postDataJSON() as Record<string, unknown>;
      options.collectionPosts?.push({ neighborId, body });
      options.writes?.push({ method, path: url.pathname, body });
      if (options.collectionFailure !== undefined) {
        const { status, code } = options.collectionFailure;
        if (code === null) {
          // A gateway answer (Render's proxy, e.g. 504 while the service wakes): no problem detail.
          return route.fulfill({ status, contentType: 'text/plain', body: 'Gateway Timeout' });
        }
        return problem(route, status, code, 'Rechazado');
      }
      const record: RecordRow = {
        id: `0192f3a8-d000-7000-8000-${String(records.length + 1).padStart(12, '0')}`,
        neighborId,
        scheduleId: (body['scheduleId'] as string | null) ?? null,
        associationId: body['associationId'] as string,
        collectionDate: body['collectionDate'] as string,
        weightKg: body['weightKg'] as number,
      };
      records.push(record);
      return json(201, record);
    }
    return problem(route, 404, 'NOT-FOUND', 'Ruta no simulada');
  });

  // reporting-service SIGERSOL records, with state: one registered by a test is listed afterwards.
  const syncs: SyncRow[] = SIGERSOL_SYNCS.map((s) => ({ ...s }));

  await page.route('**/svc/reporting/sigersol-syncs**', (route) => {
    if (!authorized(route)) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const request = route.request();
    const url = new URL(request.url());
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      options.writes?.push({ method: 'POST', path: url.pathname, body });
      if (options.sigersolFailure !== undefined) {
        return options.sigersolFailure.code === null
          ? gatewayError(route, options.sigersolFailure.status)
          : problem(route, options.sigersolFailure.status, options.sigersolFailure.code, 'Rechazado');
      }
      const { associationId, periodStart, periodEnd, hierarchyCompliancePercent } = body as Record<string, string & number>;
      if (!associationId || !periodStart || !periodEnd || typeof hierarchyCompliancePercent !== 'number') {
        return problem(route, 400, 'VALIDATION_ERROR', 'Faltan campos obligatorios');
      }
      // Bean validation (@DecimalMin/@DecimalMax/@PositiveOrZero) answers first, as VALIDATION_ERROR.
      const kilos = body['officialKilosDeclared'];
      if (hierarchyCompliancePercent < 0 || hierarchyCompliancePercent > 100 || (typeof kilos === 'number' && kilos < 0)) {
        return problem(route, 400, 'VALIDATION_ERROR', 'hierarchyCompliancePercent: fuera de rango');
      }
      // Then, as the real service: the overlap check (a shared day counts) runs before the domain's.
      if (syncs.some((s) => s.associationId === associationId && s.periodStart <= periodEnd && s.periodEnd >= periodStart)) {
        return problem(route, 409, 'RPT-006', 'Ya existe un registro SIGERSOL para esa asociación que se superpone con ese periodo');
      }
      if (periodEnd < periodStart) {
        return problem(route, 400, 'RPT-008', 'Los datos del registro SIGERSOL no son válidos');
      }
      const sync: SyncRow = {
        id: `0192f3a8-f000-7000-8000-${String(syncs.length + 1).padStart(12, '0')}`,
        associationId,
        periodStart,
        periodEnd,
        hierarchyCompliancePercent,
        officialKilosDeclared: (body['officialKilosDeclared'] as number | null) ?? null,
        declaredAt: new Date().toISOString(),
        sourceNote: (body['sourceNote'] as string | null) ?? null,
      };
      syncs.push(sync);
      return json(201, sync);
    }
    if (options.sigersolStatus !== undefined) {
      return gatewayError(route, options.sigersolStatus);
    }
    const associationId = url.searchParams.get('associationId');
    const size = Math.min(Number(url.searchParams.get('size') ?? 20), 100);
    const number = Number(url.searchParams.get('page') ?? 0);
    const all = syncs
      .filter((s) => associationId === null || s.associationId === associationId)
      .sort((a, b) => b.periodStart.localeCompare(a.periodStart));
    return json(200, {
      content: all.slice(number * size, number * size + size),
      page: number,
      size,
      totalElements: all.length,
      totalPages: Math.ceil(all.length / size),
    });
  });

  // Certificates with state: one issued by a test is listed and opens afterwards.
  const certificates: (typeof CERTIFICATES)[number][] = [...CERTIFICATES, FOREIGN_CERTIFICATE].map((c) => ({ ...c }));
  /**
   * What reporting-service computes for a period: kilos from the association's traced collections
   * (here, the fake collection-service's records, by association and date, both days included) and
   * the compliance of the one SIGERSOL record covering the whole period, if any.
   */
  const recompute = (associationId: string, periodStart: string, periodEnd: string) => {
    const kilos = records
      .filter((r) => r.associationId === associationId && r.collectionDate >= periodStart && r.collectionDate <= periodEnd)
      .reduce((sum, r) => sum + r.weightKg, 0);
    const covering = syncs.find((s) => s.associationId === associationId && s.periodStart <= periodStart && s.periodEnd >= periodEnd);
    return { kilos: Math.round(kilos * 100) / 100, compliance: covering?.hierarchyCompliancePercent ?? null };
  };

  // Tracked companies with state: one registered by a test is listed and opens afterwards.
  const companies: { id: string; name: string; ruc: string; associationId: string; status: string }[] = COMPANIES.map((c) => ({ ...c }));

  await page.route('**/svc/reporting/tracked-companies**', async (route) => {
    const authorization = route.request().headers()['authorization'];
    if (options.rejectTokens || authorization === undefined || !authorization.startsWith('Bearer ')) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    const url = new URL(route.request().url());
    const [, id, sub, certId, file] =
      /\/tracked-companies(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?$/.exec(url.pathname) ?? [];
    if (id === undefined && route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      options.writes?.push({ method: 'POST', path: url.pathname, body });
      if (options.companyFailure !== undefined) {
        return options.companyFailure.code === null
          ? gatewayError(route, options.companyFailure.status)
          : problem(route, options.companyFailure.status, options.companyFailure.code, 'Rechazado');
      }
      const { name, ruc, associationId } = body as { name?: unknown; ruc?: unknown; associationId?: unknown };
      if (typeof name !== 'string' || name.trim() === '' || typeof ruc !== 'string' || !/^\d{11}$/.test(ruc) || typeof associationId !== 'string') {
        return problem(route, 400, 'VALIDATION_ERROR', 'name, ruc o associationId no válidos');
      }
      if (companies.some((c) => c.ruc === ruc)) {
        return problem(route, 409, 'RPT-002', 'Ya existe una empresa rastreada con ese RUC');
      }
      // Like the real service: associationId is not checked against recycler-service.
      const company = { id: `0192f3a8-9000-7000-8000-${String(companies.length + 1).padStart(12, '0')}`, name, ruc, associationId, status: 'ACTIVE' };
      companies.push(company);
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(company) });
    }
    if (id === undefined) {
      const mode = options.companyLists?.shift() ?? 'full';
      if (options.companyListDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, options.companyListDelayMs));
      }
      if (mode === 'fail') {
        return problem(route, 503, 'X-503', 'Servicio no disponible');
      }
      const content = mode === 'empty' ? [] : [...companies].sort((a, b) => a.name.localeCompare(b.name));
      return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1 });
    }
    const company = companies.find((c) => c.id === id);
    if (company === undefined) {
      return problem(route, 404, 'RPT-001', 'Empresa no encontrada en reporting-service');
    }
    if (sub === undefined) {
      return json(company);
    }
    if (sub === 'certificates' && certId !== undefined) {
      const certificate = certificates.find((c) => c.id === certId && c.trackedCompanyId === company.id);
      if (certificate === undefined) {
        return problem(route, 404, 'RPT-003', 'Certificado no encontrado');
      }
      if (file === undefined) {
        return json(certificate);
      }
      if (options.downloadFailure === 'network') {
        return route.abort('failed');
      }
      if (options.downloadFailure === 401) {
        return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
      }
      if (options.downloadFailure !== undefined) {
        return problem(route, options.downloadFailure, 'X', 'Fallo de descarga');
      }
      if (options.downloadAsHtml) {
        return route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>app</title>' });
      }
      const disposition =
        options.downloadDisposition === undefined
          ? `attachment; filename="certificado-${certificate.id}.${file}"`
          : options.downloadDisposition;
      return route.fulfill({
        status: 200,
        contentType: file === 'pdf' ? 'application/pdf' : 'text/csv',
        headers: disposition === null ? {} : { 'Content-Disposition': disposition },
        body: file === 'pdf' ? '%PDF-1.7 test' : 'Empresa,x\nFecha de recoleccion,Peso (kg)\n',
      });
    }
    if (sub === 'certificates' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { periodStart?: string; periodEnd?: string };
      options.writes?.push({ method: 'POST', path: url.pathname, body });
      if (options.issueFailure !== undefined) {
        return options.issueFailure.code === null
          ? gatewayError(route, options.issueFailure.status)
          : problem(route, options.issueFailure.status, options.issueFailure.code, 'Rechazado');
      }
      const { periodStart, periodEnd } = body;
      if (!periodStart || !periodEnd) {
        return problem(route, 400, 'VALIDATION_ERROR', 'periodStart: no debe ser nulo');
      }
      // The real order: overlap with this company's certificates (a shared day counts), then coverage.
      if (certificates.some((c) => c.trackedCompanyId === company.id && c.periodStart <= periodEnd && c.periodEnd >= periodStart)) {
        return problem(route, 409, 'RPT-004', 'Ya existe un certificado emitido que se superpone con ese periodo');
      }
      const figures = recompute(company.associationId, periodStart, periodEnd);
      if (figures.compliance === null) {
        return problem(route, 409, 'RPT-005', 'No hay datos oficiales de SIGERSOL cargados para esa asociación y periodo');
      }
      const issued = {
        id: `0192f3a8-c000-7000-8000-${String(900 + certificates.length).padStart(12, '0')}`,
        trackedCompanyId: company.id,
        associationId: company.associationId,
        companyName: company.name,
        companyRuc: company.ruc,
        periodStart,
        periodEnd,
        kilosTrazados: figures.kilos,
        hierarchyCompliancePercent: figures.compliance,
        issuedAt: new Date().toISOString(),
      };
      certificates.unshift(issued);
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(issued) });
    }
    if (sub === 'certificates') {
      if (options.certificatesStatus !== undefined) {
        return gatewayError(route, options.certificatesStatus);
      }
      // FOREIGN_CERTIFICATE exists only to be asked for under the wrong company: never listed.
      const content = certificates.filter((c) => c.trackedCompanyId === company.id && c.id !== FOREIGN_CERTIFICATE.id);
      return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1 });
    }
    if (sub === 'certificate-summary') {
      // A live recomputation of that period: here, the certificate's own figures.
      const periodStart = url.searchParams.get('periodStart');
      const periodEnd = url.searchParams.get('periodEnd');
      const certified = CERTIFICATES.find(
        (c) => c.trackedCompanyId === company.id && c.periodStart === periodStart && c.periodEnd === periodEnd,
      );
      const figures =
        certified !== undefined
          ? { kilos: certified.kilosTrazados, compliance: certified.hierarchyCompliancePercent }
          : recompute(company.associationId, periodStart ?? '', periodEnd ?? '');
      return json({
        trackedCompanyId: company.id,
        periodStart,
        periodEnd,
        kilosTrazados: figures.kilos,
        hierarchyCompliancePercent: options.summaryComplianceNull ? null : figures.compliance,
      });
    }
    return problem(route, 404, 'NOT-FOUND', 'Ruta no simulada');
  });
}
