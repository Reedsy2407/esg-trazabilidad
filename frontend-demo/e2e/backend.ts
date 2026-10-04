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

export interface BackendOptions {
  /** Answer the login with 429 AUTH-004 and this Retry-After. */
  lockedFor?: number;
  /** Reject every token on protected endpoints (expired session). */
  rejectTokens?: boolean;
}

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

  await page.route('**/svc/reporting/tracked-companies**', (route) => {
    const authorization = route.request().headers()['authorization'];
    if (options.rejectTokens || authorization === undefined || !authorization.startsWith('Bearer ')) {
      return problem(route, 401, 'AUTH-000', 'Token faltante o inválido');
    }
    const url = new URL(route.request().url());
    const id = url.pathname.split('/tracked-companies/')[1];
    if (id !== undefined) {
      const company = COMPANIES.find((c) => c.id === id);
      return company === undefined
        ? problem(route, 404, 'RPT-001', 'Empresa no encontrada en reporting-service')
        : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(company) });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: COMPANIES, page: 0, size: 20, totalElements: COMPANIES.length, totalPages: 1 }),
    });
  });
}
