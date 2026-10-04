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
