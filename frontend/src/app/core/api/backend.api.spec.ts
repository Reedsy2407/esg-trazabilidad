import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { BackendApi } from './backend.api';

const { reporting } = environment.services;

describe('BackendApi (reporting endpoints of the company page)', () => {
  let api: BackendApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BackendApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists certificates of one company, first page of 20', () => {
    api.certificates('c-1').subscribe();

    const request = backend.expectOne((r) => r.url === `${reporting}/tracked-companies/c-1/certificates`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
  });

  it('asks the summary for an explicit LocalDate period', () => {
    api.certificateSummary('c-1', '2026-10-01', '2026-10-04').subscribe();

    const request = backend.expectOne((r) => r.url === `${reporting}/tracked-companies/c-1/certificate-summary`);
    expect(request.request.params.get('periodStart')).toBe('2026-10-01');
    expect(request.request.params.get('periodEnd')).toBe('2026-10-04');
  });

  it('escapes the company id in the path', () => {
    api.certificates('a/b').subscribe();

    backend.expectOne((r) => r.url === `${reporting}/tracked-companies/a%2Fb/certificates`);
  });
});

describe('BackendApi (password recovery, public)', () => {
  let api: BackendApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(BackendApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('asks for a code with only the email', () => {
    api.requestPasswordReset('ana@asociacion.pe').subscribe();

    const request = backend.expectOne(`${environment.services.auth}/auth/password-reset/request`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@asociacion.pe' });
  });

  it('trades the code for a reset token with the email and the code only', () => {
    let token = '';
    api.verifyPasswordReset({ email: 'ana@asociacion.pe', code: '04271983' }).subscribe((r) => (token = r.resetToken));

    const request = backend.expectOne(`${environment.services.auth}/auth/password-reset/verify`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@asociacion.pe', code: '04271983' });
    request.flush({ resetToken: 'abc.def' });
    expect(token).toBe('abc.def');
  });

  it('sets the new password with the reset token, without the email or the code', () => {
    api.confirmPasswordReset({ resetToken: 'abc.def', newPassword: 'una frase larga que solo yo uso' }).subscribe();

    const request = backend.expectOne(`${environment.services.auth}/auth/password-reset/confirm`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ resetToken: 'abc.def', newPassword: 'una frase larga que solo yo uso' });
  });
});
