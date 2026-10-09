import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { inOneHour, testJwt } from '../../testing/jwt';
import { SessionService } from '../auth/session.service';
import { ApiError } from './api-error';
import { errorInterceptor, jwtInterceptor, matchesServiceBase } from './interceptors';

const { auth, reporting } = environment.services;

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: SessionService;
  let router: Router;
  const token = testJwt({ email: 'ana.paredes@asociacion.pe', exp: inOneHour() });

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
    session.start(token);
  });

  afterEach(() => backend.verify());

  it('sends the bearer token to our services', () => {
    http.get(`${reporting}/tracked-companies`).subscribe();

    expect(backend.expectOne(`${reporting}/tracked-companies`).request.headers.get('Authorization')).toBe(
      `Bearer ${token}`,
    );
  });

  it('never sends the token to the login, the public actuator or a foreign host', () => {
    const urls = [`${auth}/auth/login`, `${reporting}/actuator/health/liveness`, 'https://example.org/data'];
    http.post(urls[0], {}).subscribe();
    http.get(urls[1]).subscribe();
    http.get(urls[2]).subscribe();

    for (const url of urls) {
      expect(backend.expectOne(url).request.headers.has('Authorization')).toBe(false);
    }
  });

  it('never sends the token to a look-alike URL that merely starts with a service base', () => {
    const lookAlikes = [`${auth}.otro.com/x`, `${auth}x/auth/me`, `${reporting}-evil/tracked-companies`];
    for (const url of lookAlikes) {
      http.get(url).subscribe();
      expect(backend.expectOne(url).request.headers.has('Authorization')).toBe(false);
    }
  });


  it('on a 401 AUTH-000 from a protected endpoint, clears the session and goes to the login', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let received: unknown;
    http.get(`${reporting}/tracked-companies`).subscribe({ error: (e: unknown) => (received = e) });

    backend
      .expectOne(`${reporting}/tracked-companies`)
      .flush(
        { status: 401, code: 'AUTH-000', detail: 'Token faltante o inválido' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(received).toBeInstanceOf(ApiError);
    expect((received as ApiError).code).toBe('AUTH-000');
    expect(session.token()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion' } });
  });

  it('leaves the 401 of the login itself (wrong credentials) to the form, with no redirect', () => {
    session.clear();
    const navigate = vi.spyOn(router, 'navigate');
    let received: unknown;
    http.post(`${auth}/auth/login`, {}).subscribe({ error: (e: unknown) => (received = e) });

    backend
      .expectOne(`${auth}/auth/login`)
      .flush({ status: 401, code: 'AUTH-001', detail: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });

    expect((received as ApiError).code).toBe('AUTH-001');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('reads the problem detail out of a Blob error body (downloads), so the code survives', async () => {
    const received = new Promise<unknown>((resolve) =>
      http
        .get(`${reporting}/tracked-companies/c-1/certificates/x/pdf`, { responseType: 'blob' })
        .subscribe({ error: resolve }),
    );
    // As reporting-service answers it: application/problem+json, but the client asked for a Blob.
    const problem = { type: 'about:blank', title: 'Not Found', status: 404, detail: 'Certificado no encontrado', code: 'RPT-003' };
    backend
      .expectOne(`${reporting}/tracked-companies/c-1/certificates/x/pdf`)
      .flush(new Blob([JSON.stringify(problem)], { type: 'application/problem+json' }), {
        status: 404,
        statusText: 'Not Found',
        headers: { 'Content-Type': 'application/problem+json' },
      });

    const error = (await received) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.code).toBe('RPT-003');
    expect(error.detail).toBe('Certificado no encontrado');
  });

  it('a Blob 429 keeps Retry-After and its code', async () => {
    const received = new Promise<unknown>((resolve) =>
      http.get(`${reporting}/x/csv`, { responseType: 'blob' }).subscribe({ error: resolve }),
    );
    backend.expectOne(`${reporting}/x/csv`).flush(
      new Blob([JSON.stringify({ status: 429, code: 'RATE-001' })], { type: 'application/problem+json' }),
      { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '12' } },
    );

    const error = (await received) as ApiError;
    expect(error.code).toBe('RATE-001');
    expect(error.retryAfterSeconds).toBe(12);
  });

  it('if the Blob cannot be read, the error still arrives, without a code', async () => {
    const unreadable = new Blob(['{"code":"RPT-003"}'], { type: 'application/problem+json' });
    vi.spyOn(unreadable, 'text').mockRejectedValue(new Error('read failed'));
    const received = new Promise<unknown>((resolve) =>
      http.get(`${reporting}/x/pdf`, { responseType: 'blob' }).subscribe({ error: resolve }),
    );
    backend.expectOne(`${reporting}/x/pdf`).flush(unreadable, { status: 404, statusText: 'Not Found' });

    const error = (await received) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.code).toBeNull();
  });

  it('a 401 Blob ends the session at once, even if the caller leaves before the body is read', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const subscription = http.get(`${reporting}/x/pdf`, { responseType: 'blob' }).subscribe({ error: () => undefined });
    backend.expectOne(`${reporting}/x/pdf`).flush(
      new Blob(['{"code":"AUTH-000"}'], { type: 'application/problem+json' }),
      { status: 401, statusText: 'Unauthorized' },
    );
    subscription.unsubscribe();

    expect(session.token()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion' } });
  });

  it('a Blob error body that is not JSON still becomes an ApiError, without a code', async () => {
    const received = new Promise<unknown>((resolve) =>
      http.get(`${reporting}/x/pdf`, { responseType: 'blob' }).subscribe({ error: resolve }),
    );
    backend
      .expectOne(`${reporting}/x/pdf`)
      .flush(new Blob(['<html>Bad gateway</html>']), { status: 502, statusText: 'Bad Gateway' });

    const error = (await received) as ApiError;
    expect(error.status).toBe(502);
    expect(error.code).toBeNull();
  });

  it('turns a 429 into an ApiError carrying Retry-After', () => {
    let received: unknown;
    http.post(`${auth}/auth/login`, {}).subscribe({ error: (e: unknown) => (received = e) });

    backend
      .expectOne(`${auth}/auth/login`)
      .flush({ status: 429, code: 'AUTH-004' }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '8' } });

    expect((received as ApiError).code).toBe('AUTH-004');
    expect((received as ApiError).retryAfterSeconds).toBe(8);
  });
});

describe('matchesServiceBase (production URLs)', () => {
  const bases = ['https://esg-auth-service.onrender.com', 'https://esg-reporting-service.onrender.com'];

  it('accepts a real service URL', () => {
    expect(matchesServiceBase('https://esg-auth-service.onrender.com/auth/login', bases)).toBe(true);
    expect(matchesServiceBase('https://esg-reporting-service.onrender.com/tracked-companies?page=0&size=20', bases)).toBe(true);
  });

  it('rejects a look-alike host that starts with a service base', () => {
    expect(matchesServiceBase('https://esg-auth-service.onrender.com.otro.com/x', bases)).toBe(false);
    expect(matchesServiceBase('https://esg-auth-service.onrender.comx/auth/login', bases)).toBe(false);
    expect(matchesServiceBase('https://esg-auth-service.onrender.com@evil.example/x', bases)).toBe(false);
  });
});
