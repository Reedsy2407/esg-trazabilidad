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
