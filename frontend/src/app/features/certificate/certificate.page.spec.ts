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
  status: 'ISSUED',
    annulledAt: null,
    annulledById: null,
    annulledByEmail: null,
    annulmentReason: null,
};

const ANNULLED: EsgCertificate = {
  ...CERT,
  status: 'ANNULLED',
  annulledAt: '2025-02-10T15:00:00Z',
  annulledById: 'staff-1',
  annulledByEmail: 'ana@example.com',
  annulmentReason: 'Se emitió con kilos erróneos',
};

describe('CertificatePage', () => {
  let fixture: ComponentFixture<CertificatePage>;
  let page: HTMLElement;
  let certificate$: Observable<EsgCertificate>;
  let calls: { companyId: string; certificateId: string; kind: DownloadKind }[];
  let pending: Subject<string>;
  let annulCalls: { reason: string }[];
  let annul$: Observable<EsgCertificate>;

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
    annulCalls = [];
    annul$ = of(ANNULLED);
    TestBed.configureTestingModule({
      imports: [CertificatePage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            certificate: () => certificate$,
            annulCertificate: (_companyId: string, _certificateId: string, reason: string) => {
              annulCalls.push({ reason });
              return annul$;
            },
          },
        },
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
      [new ApiError(503, null, null, null), 'pdf', 'no está disponible en este momento'],
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

  describe('annulment', () => {
    const type = (selector: string, value: string) => {
      const field = page.querySelector(selector) as HTMLTextAreaElement | HTMLInputElement;
      field.value = value;
      field.dispatchEvent(new Event(field.type === 'checkbox' ? 'change' : 'input'));
      if (field.type === 'checkbox') {
        (field as HTMLInputElement).checked = true;
        field.dispatchEvent(new Event('change'));
      }
      TestBed.tick();
    };
    const submit = () => {
      (page.querySelector('form.reason') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
      TestBed.tick();
    };
    const open = () => {
      button('Anular certificado').click();
      TestBed.tick();
    };

    it('an issued certificate shows no stamp and offers to annul it', async () => {
      await render();

      expect(page.querySelector('.stamp')).toBeNull();
      expect(button('Anular certificado')).toBeTruthy();
    });

    it('asks for the reason and an explicit confirmation before sending anything', async () => {
      await render();
      open();

      expect(page.querySelector('form.reason')).toBeTruthy();
      expect((page.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);

      type('#annul-reason', '   ');
      type('#annul-title ~ label.check input', 'on');
      submit();
      expect(annulCalls).toEqual([]);
      expect(page.querySelector('.field-error')?.textContent).toContain('Escribe el motivo');
    });

    it('sends the trimmed reason and then shows ANULADO with when, who and why, keeping the numbers', async () => {
      await render();
      open();
      type('#annul-reason', '  Se emitió con kilos erróneos ');
      type('.check input', 'on');
      submit();

      expect(annulCalls).toEqual([{ reason: 'Se emitió con kilos erróneos' }]);
      expect(page.querySelector('.stamp')?.textContent).toContain('Anulado');
      const annulment = page.querySelector('.annulment')?.textContent ?? '';
      expect(annulment).toContain('10/02/2025');
      expect(annulment).toContain('ana@example.com');
      expect(annulment).toContain('Se emitió con kilos erróneos');
      expect(page.querySelector('article')?.textContent).toContain('12,480.50 kg');
      expect(page.querySelector('button[data-panel="annul"]')).toBeNull();
      expect(page.querySelector('.annul [role="status"]')?.textContent).toContain('Certificado anulado.');
    });

    it('an already annulled certificate shows the stamp from the start and offers no annulment', async () => {
      certificate$ = of(ANNULLED);
      await render();

      expect(page.querySelector('.stamp')).toBeTruthy();
      expect(page.querySelector('button[data-panel="annul"]')).toBeNull();
    });

    it('says so on RPT-019 and keeps the panel open; an unknown outcome is not reported as done', async () => {
      await render();
      open();
      type('#annul-reason', 'Motivo');
      type('.check input', 'on');

      annul$ = throwError(() => new ApiError(409, 'RPT-019', null, null));
      submit();
      expect(alert()).toContain('ya estaba anulado');
      expect(page.querySelector('.stamp')).toBeNull();

      annul$ = throwError(() => new ApiError(503, null, null, null));
      submit();
      expect(alert()).toContain('No pudimos confirmar si el certificado se anuló');
      expect(page.querySelector('.stamp')).toBeNull();
    });
  });
});
