import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { EsgCertificate } from '../../core/api/api.types';
import { CompanyPage } from './company.page';

const { reporting } = environment.services;
const base = `${reporting}/tracked-companies/c-1`;

function certificate(periodStart: string, periodEnd: string, kg: number): EsgCertificate {
  return {
    id: `cert-${periodStart}`,
    trackedCompanyId: 'c-1',
    associationId: 'a-1',
    companyName: 'Envases del Pacífico S.A.C.',
    companyRuc: '20512345678',
    periodStart,
    periodEnd,
    kilosTrazados: kg,
    hierarchyCompliancePercent: 90,
    issuedAt: '2025-01-05T15:00:00Z',
    status: 'ISSUED',
      annulledAt: null,
      annulledById: null,
      annulledByEmail: null,
      annulmentReason: null,
  };
}

describe('CompanyPage, Resumen del período', () => {
  let fixture: ComponentFixture<CompanyPage>;
  let backend: HttpTestingController;
  let page: HTMLElement;

  const summaryBlock = () => page.querySelector('[aria-labelledby="summary-title"]') as HTMLElement;
  /**
   * Flush, then run effects so dependent resources start (zoneless). Not
   * whenStable(): open HTTP requests count as pending work and it would hang.
   */
  const answer = async (request: TestRequest, body: object) => {
    request.flush(body);
    await Promise.resolve();
    TestBed.tick();
  };
  const answerHeader = () =>
    answer(backend.expectOne(base), { id: 'c-1', name: 'Envases', ruc: '20512345678', associationId: 'a-1', status: 'ACTIVE' });
  const answerCertificates = (content: EsgCertificate[]) =>
    answer(
      backend.expectOne((r) => r.url === `${base}/certificates`),
      { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 },
    );

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CompanyPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CompanyPage);
    fixture.componentRef.setInput('id', 'c-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await answerHeader();
  });

  afterEach(() => backend.verify());

  it('summarises the latest certified period, asking the API for exactly that period', async () => {
    await answerCertificates([
      certificate('2024-11-01', '2024-11-30', 13420.1),
      certificate('2024-12-01', '2024-12-31', 12480.5),
    ]);

    const request = backend.expectOne((r) => r.url === `${base}/certificate-summary`);
    expect(request.request.params.get('periodStart')).toBe('2024-12-01');
    expect(request.request.params.get('periodEnd')).toBe('2024-12-31');
    await answer(request, {
      trackedCompanyId: 'c-1', periodStart: '2024-12-01', periodEnd: '2024-12-31',
      kilosTrazados: 12480.5, hierarchyCompliancePercent: 87.5,
    });

    const text = summaryBlock().textContent ?? '';
    expect(text).toContain('Último período certificado');
    expect(text).toContain('Diciembre 2024');
    expect(text).toContain('12,480.50 kg');
    expect(text).toContain('87.5 %');
    // The period is a name, not data: plain Plex Sans, not mono.
    expect(summaryBlock().querySelector('dd')?.classList.contains('mono')).toBe(false);
  });

  it('without certificates never calls the summary and says there are none yet', async () => {
    await answerCertificates([]);

    backend.expectNone((r) => r.url === `${base}/certificate-summary`);
    expect(summaryBlock().textContent).toContain('Aún no hay certificados.');
  });

  it('shows a missing SIGERSOL compliance as text, never as 0 %, and a partial period as its range', async () => {
    await answerCertificates([certificate('2024-12-01', '2024-12-15', 6100)]);

    await answer(backend.expectOne((r) => r.url === `${base}/certificate-summary`), {
      trackedCompanyId: 'c-1', periodStart: '2024-12-01', periodEnd: '2024-12-15',
      kilosTrazados: 6100, hierarchyCompliancePercent: null,
    });

    const text = summaryBlock().textContent ?? '';
    expect(text).toContain('01/12/2024 – 15/12/2024');
    expect(text).toContain('Sin registro SIGERSOL');
    expect(text).not.toContain('0 %');
  });

  it('lists an annulled certificate marked Anulado, and keeps it out of the summary and the chart', async () => {
    const annulled: EsgCertificate = {
      ...certificate('2024-12-01', '2024-12-31', 99000),
      status: 'ANNULLED',
      annulledAt: '2025-02-01T10:00:00Z',
      annulledById: 'staff-1',
      annulledByEmail: 'ana@example.com',
      annulmentReason: 'Datos erróneos',
    };
    await answerCertificates([annulled, certificate('2024-11-01', '2024-11-30', 13420.1)]);

    // The latest certified period is November: the annulled December is history.
    const request = backend.expectOne((r) => r.url === `${base}/certificate-summary`);
    expect(request.request.params.get('periodStart')).toBe('2024-11-01');
    await answer(request, {
      trackedCompanyId: 'c-1', periodStart: '2024-11-01', periodEnd: '2024-11-30',
      kilosTrazados: 13420.1, hierarchyCompliancePercent: 87.5,
    });

    const rows = Array.from(page.querySelectorAll('.table-wide tbody tr'));
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Diciembre 2024');
    expect(rows[0].querySelector('.annulled-tag')?.textContent).toContain('Anulado');
    expect(rows[1].querySelector('.annulled-tag')).toBeNull();
    // 99,000 kg would have stretched the chart's axis past 100,000.
    expect(page.querySelector('app-kilos-chart')?.textContent).not.toContain('99,000');
  });
});
