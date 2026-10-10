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
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'staff-1', email, exp })}.signature`;
}

const problem = (route: Route, status: number, code: string, detail: string, headers: Record<string, string> = {}) =>
  route.fulfill({
    status,
    contentType: 'application/problem+json',
    headers,
    body: JSON.stringify({ type: 'about:blank', title: '', status, detail, code }),
  });

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

export async function fakeBackend(page: Page, options: BackendOptions = {}): Promise<void> {
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
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: jwtFor(email) }) });
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
    if (options.associationsStatus !== undefined) {
      return problem(route, options.associationsStatus, 'X-500', 'Error interno');
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOf(ASSOCIATIONS)) });
  });

  await page.route('**/svc/collection/neighbors**', (route) => {
    if (!authorized(route)) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const { pathname } = new URL(route.request().url());
    const [, neighborId, sub] = /\/neighbors(?:\/([^/]+))?(?:\/([^/]+))?$/.exec(pathname) ?? [];
    if (neighborId === undefined) {
      // The API sorts by fullName.
      return json(200, pageOf([...NEIGHBORS].sort((a, b) => a.fullName.localeCompare(b.fullName))));
    }
    const neighbor = NEIGHBORS.find((n) => n.id === neighborId);
    if (neighbor === undefined) {
      return problem(route, 404, 'COL-001', 'Vecino no encontrado');
    }
    if (sub === 'schedules') {
      return json(200, pageOf(SCHEDULES[neighbor.id] ?? []));
    }
    if (sub === 'collection-records' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      options.collectionPosts?.push({ neighborId, body });
      if (options.collectionFailure !== undefined) {
        const { status, code } = options.collectionFailure;
        if (code === null) {
          // A gateway answer (Render's proxy, e.g. 504 while the service wakes): no problem detail.
          return route.fulfill({ status, contentType: 'text/plain', body: 'Gateway Timeout' });
        }
        return problem(route, status, code, 'Rechazado');
      }
      return json(201, {
        id: '0192f3a8-d000-7000-8000-000000000001',
        neighborId,
        scheduleId: body['scheduleId'] ?? null,
        associationId: body['associationId'],
        collectionDate: body['collectionDate'],
        weightKg: body['weightKg'],
      });
    }
    return problem(route, 404, 'NOT-FOUND', 'Ruta no simulada');
  });

  await page.route('**/svc/reporting/tracked-companies**', (route) => {
    const authorization = route.request().headers()['authorization'];
    if (options.rejectTokens || authorization === undefined || !authorization.startsWith('Bearer ')) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    const url = new URL(route.request().url());
    const [, id, sub, certId, file] =
      /\/tracked-companies(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?(?:\/([^/]+))?$/.exec(url.pathname) ?? [];
    if (id === undefined) {
      return json({ content: COMPANIES, page: 0, size: 20, totalElements: COMPANIES.length, totalPages: 1 });
    }
    const company = COMPANIES.find((c) => c.id === id);
    if (company === undefined) {
      return problem(route, 404, 'RPT-001', 'Empresa no encontrada en reporting-service');
    }
    if (sub === undefined) {
      return json(company);
    }
    if (sub === 'certificates' && certId !== undefined) {
      const certificate = [...CERTIFICATES, FOREIGN_CERTIFICATE].find(
        (c) => c.id === certId && c.trackedCompanyId === company.id,
      );
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
    if (sub === 'certificates') {
      if (options.certificatesStatus !== undefined) {
        return problem(route, options.certificatesStatus, 'X-500', 'Error interno');
      }
      const content = company.id === COMPANIES[0].id ? CERTIFICATES : [];
      return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1 });
    }
    if (sub === 'certificate-summary') {
      // A live recomputation of that period: here, the certificate's own figures.
      const periodStart = url.searchParams.get('periodStart');
      const periodEnd = url.searchParams.get('periodEnd');
      const certified = CERTIFICATES.find((c) => c.periodStart === periodStart && c.periodEnd === periodEnd);
      return json({
        trackedCompanyId: company.id,
        periodStart,
        periodEnd,
        kilosTrazados: certified?.kilosTrazados ?? 0,
        hierarchyCompliancePercent: options.summaryComplianceNull ? null : (certified?.hierarchyCompliancePercent ?? null),
      });
    }
    return problem(route, 404, 'NOT-FOUND', 'Ruta no simulada');
  });
}
