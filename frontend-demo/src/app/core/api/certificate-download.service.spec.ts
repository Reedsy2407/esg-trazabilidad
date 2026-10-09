import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { downloadErrorText } from '../../features/certificate/certificate.page';
import { inOneHour, testJwt } from '../../testing/jwt';
import { SessionService } from '../auth/session.service';
import { ApiError } from '../http/api-error';
import { errorInterceptor, jwtInterceptor } from '../http/interceptors';
import { CertificateDownloadService, REVOKE_AFTER_MS, UnexpectedDownloadError } from './certificate-download.service';

const { reporting } = environment.services;
const CERT = '0192f3a8-c000-7000-8000-000000000013';
const PDF_URL = `${reporting}/tracked-companies/c-1/certificates/${CERT}/pdf`;

/** An error as reporting-service sends it (RFC 7807 + code); with responseType 'blob' it arrives as a Blob. */
const problem = (status: number, code: string) =>
  new Blob([JSON.stringify({ type: 'about:blank', title: '', status, detail: '', code })], {
    type: 'application/problem+json',
  });
const PROBLEM_OPTIONS = (status: number, statusText: string) => ({
  status,
  statusText,
  headers: { 'Content-Type': 'application/problem+json' },
});

describe('CertificateDownloadService', () => {
  let service: CertificateDownloadService;
  let backend: HttpTestingController;
  let session: SessionService;
  let clicked: { href: string; download: string }[];
  let created: string[];
  let revoked: string[];

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(CertificateDownloadService);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    session.start(testJwt({ email: 'ana@asociacion.pe', exp: inOneHour() }));
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    clicked = [];
    created = [];
    revoked = [];
    let n = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
      const url = `blob:test/${++n}`;
      created.push(url);
      return url;
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url: string) => {
      revoked.push(url);
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push({ href: this.href, download: this.download });
    });
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('asks our service, with the token, and saves the Blob under the sanitized server name', () => {
    let saved: string | undefined;
    service.download('c-1', CERT, 'pdf').subscribe((name) => (saved = name));

    const request = backend.expectOne(PDF_URL);
    expect(request.request.headers.get('Authorization')).toMatch(/^Bearer /);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' }), {
      headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="certificado-${CERT}.pdf"` },
    });

    expect(saved).toBe(`certificado-${CERT}.pdf`);
    expect(clicked).toEqual([{ href: 'blob:test/1', download: `certificado-${CERT}.pdf` }]);
    // The anchor is gone; the object URL lives long enough for every browser to start saving, then is revoked.
    expect(document.querySelector('a[download]')).toBeNull();
    vi.advanceTimersByTime(REVOKE_AFTER_MS - 1);
    expect(revoked).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(revoked).toEqual(created);
  });

  it('never trusts a hostile Content-Disposition, and falls back to certificado-{id} without one', () => {
    service.download('c-1', CERT, 'csv').subscribe();
    backend
      .expectOne(`${reporting}/tracked-companies/c-1/certificates/${CERT}/csv`)
      .flush(new Blob(['a,b']), {
        headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="..\\..\\evil.exe"' },
      });

    service.download('c-1', CERT, 'pdf').subscribe();
    backend.expectOne(PDF_URL).flush(new Blob(['x']), { headers: { 'Content-Type': 'application/pdf' } });

    expect(clicked.map((c) => c.download)).toEqual(['evil.csv', `certificado-${CERT}.pdf`]);
  });

  it('refuses a 200 that is empty or not the file asked for (an HTML fallback page, a missing type)', () => {
    const errors: unknown[] = [];
    const answers: [Blob, Record<string, string>][] = [
      [new Blob([]), { 'Content-Type': 'application/pdf' }],
      [new Blob(['<!doctype html><title>app</title>']), { 'Content-Type': 'text/html' }],
      [new Blob(['%PDF-1.7']), {}],
      [new Blob(['a,b']), { 'Content-Type': 'text/csv' }], // a CSV where a PDF was asked for
    ];
    for (const [body, headers] of answers) {
      service.download('c-1', CERT, 'pdf').subscribe({ error: (e: unknown) => errors.push(e) });
      backend.expectOne(PDF_URL).flush(body, { headers });
    }

    expect(errors).toHaveLength(answers.length);
    expect(errors.every((e) => e instanceof UnexpectedDownloadError)).toBe(true);
    expect(clicked).toEqual([]);
    expect(created).toEqual([]);
  });

  it('on 401 the session ends (errorInterceptor) and nothing is saved', async () => {
    const failed = new Promise<unknown>((resolve) => service.download('c-1', CERT, 'pdf').subscribe({ error: resolve }));
    backend.expectOne(PDF_URL).flush(problem(401, 'AUTH-000'), PROBLEM_OPTIONS(401, 'Unauthorized'));

    const error = (await failed) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('AUTH-000');
    expect(session.token()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion' } });
    expect(clicked).toEqual([]);
    expect(created).toEqual([]);
  });

  it('on 404 (RPT-003, code kept) or a dropped connection it fails without saving anything', async () => {
    const notFound = new Promise<unknown>((resolve) => service.download('c-1', CERT, 'pdf').subscribe({ error: resolve }));
    backend.expectOne(PDF_URL).flush(problem(404, 'RPT-003'), PROBLEM_OPTIONS(404, 'Not Found'));
    const dropped = new Promise<unknown>((resolve) => service.download('c-1', CERT, 'pdf').subscribe({ error: resolve }));
    backend.expectOne(PDF_URL).error(new ProgressEvent('error'), { status: 0 });

    const errors = (await Promise.all([notFound, dropped])) as ApiError[];
    expect(errors.map((e) => [e.status, e.code])).toEqual([
      [404, 'RPT-003'],
      [0, null],
    ]);
    expect(clicked).toEqual([]);
  });
});

describe('downloadErrorText', () => {
  it('says what failed and what to do, per status', () => {
    const e = (status: number, retry: number | null = null) => new ApiError(status, null, null, retry);
    expect(downloadErrorText(e(404), 'pdf')).toContain('el certificado ya no existe');
    expect(downloadErrorText(e(0), 'csv')).toContain('No se pudo conectar');
    expect(downloadErrorText(e(429, 20), 'pdf')).toBe('Demasiadas solicitudes. Vuelve a descargar el PDF en 20 s.');
    expect(downloadErrorText(e(500), 'csv')).toBe('No se pudo descargar el CSV (HTTP 500). Inténtalo de nuevo.');
    expect(downloadErrorText(new UnexpectedDownloadError('pdf'), 'pdf')).toBe(
      'El servicio de reportes no devolvió un PDF válido. Inténtalo de nuevo.',
    );
  });
});
