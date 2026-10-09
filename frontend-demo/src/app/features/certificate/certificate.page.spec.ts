import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { EsgCertificate } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { CertificateDownloadService, UnexpectedDownloadError } from '../../core/api/certificate-download.service';
import { ApiError } from '../../core/http/api-error';
import { DownloadKind } from '../../shared/download-filename';
import { CertificatePage } from './certificate.page';

const CERT: EsgCertificate = {
  id: '0192f3a8-c000-7000-8000-000000000013',
  trackedCompanyId: 'c-1',
  associationId: 'a-1',
  companyName: 'Envases del Pacífico S.A.C.',
  companyRuc: '20512345678',
  periodStart: '2024-12-01',
  periodEnd: '2024-12-31',
  kilosTrazados: 12480.5,
  hierarchyCompliancePercent: 87.5,
  issuedAt: '2025-01-05T15:00:00Z',
};

describe('CertificatePage', () => {
  let fixture: ComponentFixture<CertificatePage>;
  let page: HTMLElement;
  let certificate$: Observable<EsgCertificate>;
  let calls: { companyId: string; certificateId: string; kind: DownloadKind }[];
  let pending: Subject<string>;

  const button = (name: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes(name)) as HTMLButtonElement;
  const alert = () => page.querySelector('[role="alert"]')?.textContent?.trim();

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(CertificatePage);
    fixture.componentRef.setInput('companyId', 'c-1');
    fixture.componentRef.setInput('certificateId', CERT.id);
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  }

  beforeEach(() => {
    certificate$ = of(CERT);
    calls = [];
    pending = new Subject<string>();
    TestBed.configureTestingModule({
      imports: [CertificatePage],
      providers: [
        provideRouter([]),
        { provide: BackendApi, useValue: { certificate: () => certificate$ } },
        {
          provide: CertificateDownloadService,
          useValue: {
            download: (companyId: string, certificateId: string, kind: DownloadKind) => {
              calls.push({ companyId, certificateId, kind });
              return pending;
            },
          },
        },
      ],
    });
  });

  it('shows the real fields, outside any live region', async () => {
    await render();

    const text = page.querySelector('article')?.textContent ?? '';
    expect(text).toContain('12,480.50 kg');
    expect(text).toContain('01/12/2024 – 31/12/2024');
    expect(text).toContain('05/01/2025');
    expect(text).toContain('87.5 %');
    expect(page.querySelector('code')?.textContent).toBe(CERT.id);
    // The certificate is not inside a live region: it is not read out on arrival.
    expect(page.querySelector('article')?.closest('[aria-live]')).toBeNull();
  });

  it('one download at a time: a second click while one is in flight does nothing, and the buttons come back', async () => {
    await render();
    const pdf = button('Descargar PDF');
    pdf.click();
    TestBed.tick();
    expect(pdf.textContent).toContain('Descargando PDF');
    expect(pdf.disabled).toBe(true);
    expect(button('Descargar CSV').disabled).toBe(true);

    // The guard itself, not just the disabled attribute: force the clicks anyway.
    pdf.disabled = false;
    button('Descargar CSV').disabled = false;
    button('Descargar CSV').click();
    pdf.click();
    TestBed.tick();
    expect(calls).toEqual([{ companyId: 'c-1', certificateId: CERT.id, kind: 'pdf' }]);

    pending.next('certificado.pdf');
    pending.complete();
    TestBed.tick();
    expect(button('Descargar PDF').disabled).toBe(false);
    expect(button('Descargar CSV').disabled).toBe(false);
    expect(alert()).toBeUndefined();
  });

  it('a failed download says why (404 vs. other errors vs. a wrong file) and never stays on "Descargando"', async () => {
    await render();
    const cases: [unknown, DownloadKind, string][] = [
      [new ApiError(404, 'RPT-003', null, null), 'csv', 'el certificado ya no existe'],
      [new ApiError(503, null, null, null), 'pdf', '(HTTP 503)'],
      [new UnexpectedDownloadError('pdf'), 'pdf', 'no devolvió un PDF válido'],
    ];
    for (const [error, kind, expected] of cases) {
      pending = new Subject<string>();
      button(kind === 'pdf' ? 'Descargar PDF' : 'Descargar CSV').click();
      TestBed.tick();
      pending.error(error);
      TestBed.tick();

      expect(alert()).toContain(expected);
      expect(button('Descargar PDF').disabled).toBe(false);
      expect(button('Descargar CSV').disabled).toBe(false);
    }
  });

  it('a new download clears the previous error', async () => {
    await render();
    button('Descargar PDF').click();
    pending.error(new ApiError(0, null, null, null));
    TestBed.tick();
    expect(alert()).toContain('No se pudo conectar');

    pending = new Subject<string>();
    button('Descargar PDF').click();
    TestBed.tick();
    expect(alert()).toBeUndefined();
  });

  it('404 RPT-003 on load shows the typed message and the way back; other errors offer a retry', async () => {
    certificate$ = throwError(() => new ApiError(404, 'RPT-003', null, null));
    await render();
    expect(alert()).toContain('Este certificado no existe o no pertenece a esta empresa.');
    expect(page.querySelector('[role="alert"] a')?.textContent).toContain('Volver a la empresa');

    certificate$ = throwError(() => new ApiError(500, null, null, null));
    await render();
    expect(alert()).not.toContain('no existe');
    expect(page.querySelector('[role="alert"] button')?.textContent).toContain('Volver a intentar');
  });

  it('"Copiar" announces every click, even the same message twice', async () => {
    vi.useFakeTimers();
    try {
      const writeText = vi.fn().mockResolvedValue(undefined);
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
      await render();
      const status = () => page.querySelector('.copy-status')?.textContent?.trim();
      const copy = page.querySelector('button[aria-label="Copiar código del certificado"]') as HTMLButtonElement;

      copy.click();
      await vi.advanceTimersByTimeAsync(100);
      TestBed.tick();
      expect(writeText).toHaveBeenCalledWith(CERT.id);
      expect(status()).toBe('Código copiado.');

      copy.click();
      TestBed.tick();
      expect(status()).toBe(''); // emptied first, so the refill is a new change
      await vi.advanceTimersByTimeAsync(100);
      TestBed.tick();
      expect(status()).toBe('Código copiado.');
    } finally {
      vi.useRealTimers();
    }
  });
});
