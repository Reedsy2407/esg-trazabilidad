import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { EsgCertificate } from '../../core/api/api.types';
import { errorInterceptor } from '../../core/http/interceptors';
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
    answer(backend.expectOne(base), {
      id: 'c-1',
      name: 'Envases',
      ruc: '20512345678',
      associationId: 'a-1',
      status: 'ACTIVE',
      verification: 'VERIFIED',
      sunatEstado: 'ACTIVO',
      sunatCondicion: 'HABIDO',
      sunatCheckedAt: '2026-10-01T15:00:00Z',
      contactName: 'Rosa Quispe',
      contactEmail: null,
      contactPhone: null,
      warning: null,
    });
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

  it('shows what SUNAT answered and the contact, with dashes for what is missing', async () => {
    await answerCertificates([]);
    const block = page.querySelector('[aria-labelledby="sunat-title"]') as HTMLElement;
    const lines = Object.fromEntries(
      [...block.querySelectorAll('dl > div')].map((line) => [
        line.querySelector('dt')?.textContent?.trim(),
        line.querySelector('dd')?.textContent?.trim(),
      ]),
    );
    expect(lines['Estado en SUNAT']).toBe('ACTIVO');
    expect(lines['Condición']).toBe('HABIDO');
    expect(lines['Contacto']).toBe('Rosa Quispe');
    expect(lines['Correo']).toBe('—');
    expect(block.querySelector('.warning')).toBeNull();
  });

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

describe('CompanyPage, ciclo de vida', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;

  const active = {
    id: 'c-1', name: 'Envases', ruc: '20512345678', associationId: 'a-1', status: 'ACTIVE',
    verification: 'VERIFIED', sunatEstado: 'ACTIVO', sunatCondicion: 'HABIDO', sunatCheckedAt: '2026-10-01T15:00:00Z',
    contactName: 'Rosa Quispe', contactEmail: 'rosa@example.org', contactPhone: null, warning: null,
  };
  const flush = async (request: TestRequest, body: object | null, init?: { status: number; statusText: string }) => {
    request.flush(body, init);
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (text: string) => [...page.querySelectorAll('button')].find((b) => b.textContent?.includes(text));
  const confirmWith = (reason: string) => {
    const textarea = page.querySelector('app-reason-confirm textarea') as HTMLTextAreaElement;
    textarea.value = reason;
    textarea.dispatchEvent(new Event('input'));
    const check = page.querySelector('app-reason-confirm input[type="checkbox"]') as HTMLInputElement;
    check.checked = true;
    check.dispatchEvent(new Event('change'));
    TestBed.tick();
    (page.querySelector('app-reason-confirm form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CompanyPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CompanyPage);
    fixture.componentRef.setInput('id', 'c-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne(base), active);
    await flush(backend.expectOne((r) => r.url === `${base}/certificates`), {
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
  });

  afterEach(() => backend.verify());

  it('deactivating asks for a reason, sends exactly it, then shows the company as inactive', async () => {
    button('Desactivar empresa')!.click();
    TestBed.tick();
    confirmWith('  Cerró el local ');
    const request = backend.expectOne({ method: 'PATCH', url: `${base}/deactivate` });
    expect(request.request.body).toEqual({ reason: 'Cerró el local' });
    await flush(request, { ...active, status: 'INACTIVE' });
    await flush(backend.expectOne(base), { ...active, status: 'INACTIVE' });

    expect(page.textContent).toContain('Empresa desactivada.');
    expect(button('Reactivar empresa')).toBeDefined();
    expect(button('Desactivar empresa')).toBeUndefined();
  });

  it('RPT-018 says the company was already in that state', async () => {
    button('Desactivar empresa')!.click();
    TestBed.tick();
    confirmWith('otra vez');
    await flush(
      backend.expectOne({ method: 'PATCH', url: `${base}/deactivate` }),
      { code: 'RPT-018', detail: 'x' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(page.querySelector('app-reason-confirm [role="alert"]')?.textContent).toContain('ya estaba en ese estado');
  });

  it('deleting sends the reason and leaves for the list; RPT-017 explains why it was refused', async () => {
    button('Eliminar empresa')!.click();
    TestBed.tick();
    confirmWith('Duplicada');
    await flush(
      backend.expectOne({ method: 'DELETE', url: base }),
      { code: 'RPT-017', detail: 'x' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(page.querySelector('app-reason-confirm [role="alert"]')?.textContent).toContain('aunque estén anulados');
    expect(navigate).not.toHaveBeenCalled();

    (page.querySelector('app-reason-confirm form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
    const retry = backend.expectOne({ method: 'DELETE', url: base });
    expect(retry.request.body).toEqual({ reason: 'Duplicada' });
    await flush(retry, null);
    expect(navigate).toHaveBeenCalledWith(['/empresas']);
  });

  it('edits the contact: the form starts from the stored values and a blank field is sent blank', async () => {
    button('Editar contacto')!.click();
    TestBed.tick();
    expect((page.querySelector('#contactName') as HTMLInputElement).value).toBe('Rosa Quispe');
    const email = page.querySelector('#contactEmail') as HTMLInputElement;
    email.value = '';
    email.dispatchEvent(new Event('input'));
    (page.querySelector('.edit-contact') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();

    const request = backend.expectOne({ method: 'PATCH', url: base });
    expect(request.request.body).toEqual({ contactName: 'Rosa Quispe', contactEmail: '', contactPhone: '' });
    await flush(request, { ...active, contactEmail: null });
    await flush(backend.expectOne(base), { ...active, contactEmail: null });
    expect(page.textContent).toContain('Contacto guardado.');
    expect(page.querySelector('.edit-contact')).toBeNull();
  });
});

describe('CompanyPage, empresa no verificada', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;

  const unverified = {
    id: 'c-1', name: 'Escrita a mano', ruc: '20512345678', associationId: 'a-1', status: 'ACTIVE',
    verification: 'UNVERIFIED', sunatEstado: null, sunatCondicion: null, sunatCheckedAt: null,
    contactName: null, contactEmail: null, contactPhone: null, warning: null,
  };
  const flush = async (request: TestRequest, body: object | null, init?: { status: number; statusText: string }) => {
    request.flush(body, init);
    await Promise.resolve();
    TestBed.tick();
  };
  const verifyButton = () =>
    [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Verificar con SUNAT'));

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CompanyPage],
      // errorInterceptor turns the problem detail into the ApiError the page branches on.
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CompanyPage);
    fixture.componentRef.setInput('id', 'c-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne(base), unverified);
    await flush(backend.expectOne((r) => r.url === `${base}/certificates`), {
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
  });

  afterEach(() => backend.verify());

  it('is marked No verificada, explains why, and offers Verificar instead of Emitir', () => {
    expect(page.querySelector('.unverified-tag')?.textContent).toContain('No verificada');
    expect(page.querySelector('.sunat .warning')?.textContent).toContain('no se ha comprobado en SUNAT');
    expect(verifyButton()).toBeDefined();
    expect(page.textContent).not.toContain('Emitir certificado');
  });

  it('Verificar con SUNAT posts the refresh and reloads the company, now verified', async () => {
    verifyButton()!.click();
    TestBed.tick();
    const refresh = backend.expectOne({ method: 'POST', url: `${base}/sunat-refresh` });
    await flush(refresh, { ...unverified, name: 'RAZON SUNAT', verification: 'VERIFIED' });
    await flush(backend.expectOne(base), { ...unverified, name: 'RAZON SUNAT', verification: 'VERIFIED', sunatEstado: 'ACTIVO' });

    expect(page.querySelector('h1')?.textContent).toContain('RAZON SUNAT');
    expect(page.querySelector('.unverified-tag')).toBeNull();
    expect(page.textContent).toContain('Empresa verificada con SUNAT.');
    expect(page.textContent).toContain('Emitir certificado');
  });

  it('when SUNAT does not answer it says the company stays unverified', async () => {
    verifyButton()!.click();
    TestBed.tick();
    await flush(
      backend.expectOne({ method: 'POST', url: `${base}/sunat-refresh` }),
      { code: 'RPT-011', detail: 'x' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(page.querySelector('.sunat [role="alert"]')?.textContent).toContain('sigue sin verificar');
    expect(page.querySelector('.unverified-tag')).not.toBeNull();
  });
});
