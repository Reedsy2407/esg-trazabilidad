import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { CertificateSummary, EsgCertificate, IssueCertificateRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { todayInLima } from '../../shared/format';
import { CertificateIssuePage, ISSUE_UNCERTAIN } from './certificate-issue.page';
import { previousMonth } from './issue-period';

const COMPANY = { id: 'c-1', name: 'Envases del Pacífico S.A.C.', ruc: '20512345678', associationId: 'a-1', status: 'ACTIVE' };
const EXISTING: EsgCertificate = {
  id: 'cert-dec',
  trackedCompanyId: 'c-1',
  associationId: 'a-1',
  companyName: COMPANY.name,
  companyRuc: COMPANY.ruc,
  periodStart: '2024-12-01',
  periodEnd: '2024-12-31',
  kilosTrazados: 100,
  hierarchyCompliancePercent: 90,
  issuedAt: '2025-01-05T15:00:00Z',
};

describe('CertificateIssuePage', () => {
  let el: HTMLElement;
  let summary: Partial<CertificateSummary>;
  let issued: IssueCertificateRequest[];
  let answer: Subject<EsgCertificate>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let summaryCalls: number;
  let page: CertificateIssuePage;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (text: RegExp) => [...el.querySelectorAll('button')].find((b) => text.test(b.textContent ?? '')) as HTMLButtonElement | undefined;
  const setInput = async (id: string, value: string) => {
    const input = el.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  };
  const review = async () => {
    button(/Revisar el borrador/)!.click();
    await settle();
  };
  const confirm = async () => {
    const box = el.querySelector('.check input') as HTMLInputElement;
    box.checked = true;
    box.dispatchEvent(new Event('change'));
    await settle();
  };

  beforeEach(async () => {
    summary = { kilosTrazados: 8.25, hierarchyCompliancePercent: 91.25 };
    issued = [];
    summaryCalls = 0;
    answer = new Subject<EsgCertificate>();
    TestBed.configureTestingModule({
      imports: [CertificateIssuePage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            trackedCompany: () => of(COMPANY),
            association: () => of({ id: 'a-1', name: 'Asociación Recicla Rímac' }),
            certificates: () => of({ content: [EXISTING], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
            certificateSummary: (_: string, periodStart: string, periodEnd: string) => {
              summaryCalls += 1;
              return of({ trackedCompanyId: 'c-1', periodStart, periodEnd, ...summary });
            },
            issueCertificate: (_: string, request: IssueCertificateRequest): Observable<EsgCertificate> => {
              issued.push(request);
              return answer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CertificateIssuePage);
    page = fixture.componentInstance;
    fixture.componentRef.setInput('id', 'c-1');
    el = fixture.nativeElement as HTMLElement;
    await settle();
  });

  it('starts on the last ended month; the current month is refused, saying from when', async () => {
    expect((el.querySelector('#month') as HTMLInputElement).value).toBe(previousMonth(todayInLima()));
    await setInput('month', todayInLima().slice(0, 7));
    await review();
    expect(el.querySelector('#period-error')?.textContent).toMatch(/Este período aún no termina\. Podrás emitirlo desde el 01\/\d{2}\/\d{4}\./);
    expect(el.querySelector('.draft')).toBeNull();
  });

  it('the draft: dashed, named a draft, with the figures; issue only after the checkbox, once', async () => {
    await setInput('month', '2026-09');
    await review();
    const draft = el.querySelector('.draft') as HTMLElement;
    expect(draft.textContent).toContain('Borrador · aún no emitido');
    expect(draft.textContent).toContain('Setiembre 2026');
    expect(draft.textContent).toContain('8.25 kg');
    expect(draft.textContent).toContain('Asociación Recicla Rímac');
    expect(el.textContent).toContain('Emitir es definitivo');
    expect(el.textContent).toContain('Las cifras se recalculan al emitir');

    const issue = button(/Emitir certificado de Setiembre 2026/)!;
    expect(issue.disabled).toBe(true);
    // Not only the disabled button: issue() itself refuses without the checkbox.
    (page as unknown as { issue(): void }).issue();
    expect(issued).toEqual([]);

    await confirm();
    expect(issue.disabled).toBe(false);
    issue.click();
    issue.click();
    await settle();
    expect(issued).toEqual([{ periodStart: '2026-09-01', periodEnd: '2026-09-30' }]);
    expect(button(/Emitiendo…/)?.disabled).toBe(true);
    answer.next({ ...EXISTING, id: 'cert-new' });
    answer.complete();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/empresas', 'c-1', 'certificados', 'cert-new'], { state: { emitido: true } });
    // Between the 201 and the certificate page opening, nothing can be sent again or changed.
    expect(button(/Emitiendo…/)?.disabled).toBe(true);
    expect((el.querySelector('.check input') as HTMLInputElement).disabled).toBe(true);
    expect((el.querySelector('#month') as HTMLInputElement).disabled).toBe(true);
    (page as unknown as { issue(): void }).issue();
    expect(issued).toHaveLength(1);
  });

  it('issued but its page fails to open: says so and links to it', async () => {
    navigate.mockResolvedValue(false);
    await setInput('month', '2026-09');
    await review();
    await confirm();
    button(/Emitir certificado de/)!.click();
    answer.next({ ...EXISTING, id: 'cert-new' });
    answer.complete();
    await settle();
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('se emitió, pero su página no se pudo abrir');
    expect(el.querySelector('[role="alert"] a')?.getAttribute('href')).toBe('/empresas/c-1/certificados/cert-new');
  });

  it('reviewing the same period again asks for the figures again', async () => {
    await setInput('month', '2026-09');
    await review();
    expect(summaryCalls).toBe(1);
    await review();
    expect(summaryCalls).toBe(2);
  });

  it('0 kg, no SIGERSOL coverage or a listed overlap: no checkbox, no button, and why', async () => {
    summary = { kilosTrazados: 0, hierarchyCompliancePercent: 90 };
    await setInput('month', '2026-08');
    await review();
    expect(el.textContent).toContain('No hay recojos de la asociación registrados en Agosto 2026 (0 kg)');
    expect(button(/Emitir certificado/)).toBeUndefined();

    summary = { kilosTrazados: 50, hierarchyCompliancePercent: null };
    await setInput('month', '2026-07');
    await review();
    expect(el.textContent).toContain('No hay un registro SIGERSOL de la asociación que cubra todo Julio 2026');
    const link = el.querySelector('a[href*="/sigersol/nuevo"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/sigersol/nuevo?asociacion=a-1&desde=2026-07-01&hasta=2026-07-31');
    expect(el.querySelector('.check')).toBeNull();

    summary = { kilosTrazados: 50, hierarchyCompliancePercent: 90 };
    await setInput('month', '2024-12');
    await review();
    expect(el.textContent).toContain('ya tiene el certificado de Diciembre 2024');
    expect(el.querySelector('.check')).toBeNull();
  });

  it('a free range: backwards is refused before asking anything', async () => {
    (el.querySelector('input[value="rango"]') as HTMLInputElement).dispatchEvent(new Event('change'));
    await settle();
    await setInput('from', '2026-09-20');
    await setInput('to', '2026-09-10');
    await review();
    expect(el.querySelector('#period-error')?.textContent).toContain('«hasta» no puede ser anterior a «desde»');
    await setInput('to', '2026-09-25');
    await review();
    expect(el.querySelector('.draft')?.textContent).toContain('20/09/2026 – 25/09/2026');
  });

  it('changing the period discards the draft and the confirmation', async () => {
    await setInput('month', '2026-09');
    await review();
    await confirm();
    await setInput('month', '2026-08');
    expect(el.querySelector('.draft')).toBeNull();
    await review();
    expect((el.querySelector('.check input') as HTMLInputElement).checked).toBe(false);
  });

  it('RPT-004, RPT-005, RPT-009 and an unknown outcome each say what to do, and ask to confirm again', async () => {
    await setInput('month', '2026-09');
    await review();
    const cases: [ApiError, RegExp, string | null][] = [
      [new ApiError(409, 'RPT-004', null, null), /ya tiene un certificado que se superpone con Setiembre 2026/, 'Ver los certificados de la empresa'],
      [new ApiError(409, 'RPT-005', null, null), /No hay un registro SIGERSOL de la asociación que cubra todo Setiembre 2026/, 'Registrar el dato SIGERSOL de este período'],
      // The screen refuses a backwards period first; this is the backend's own refusal if one got through.
      [new ApiError(400, 'RPT-009', null, null), /rechazó Setiembre 2026: la fecha final es anterior a la inicial/, null],
      [new ApiError(504, null, null, null), new RegExp(ISSUE_UNCERTAIN.slice(0, 40)), 'Ver los certificados de la empresa'],
    ];
    for (const [error, text, link] of cases) {
      await confirm();
      button(/Emitir certificado de/)!.click();
      answer.error(error);
      answer = new Subject<EsgCertificate>();
      await settle();
      const block = el.querySelector('.issue-problem') as HTMLElement;
      expect(block.textContent).toMatch(text);
      expect(block.querySelector('a')?.textContent?.trim() ?? null).toBe(link);
      expect((el.querySelector('.check input') as HTMLInputElement).checked).toBe(false);
    }
    expect(issued).toHaveLength(4);
  });
});
