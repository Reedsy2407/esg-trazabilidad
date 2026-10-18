import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import {
  Association,
  RegisterTrackedCompanyRequest,
  RegisterUnverifiedTrackedCompanyRequest,
  TrackedCompany,
} from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { COMPANY_UNCERTAIN, CompanyNewPage } from './company-new.page';

const association = (id: string, status: Association['status'] = 'ACTIVE'): Association => ({
  id,
  name: `Asociación ${id}`,
  ruc: '20601234567',
  registrationNumber: null,
  address: null,
  contactEmail: null,
  contactPhone: null,
  status,
});

const COMPANY: TrackedCompany = {
  id: 'c-9',
  name: 'ENVASES DEL PACÍFICO S.A.C.',
  ruc: '20512345678',
  associationId: 'a-1',
  status: 'ACTIVE',
  verification: 'VERIFIED',
  sunatEstado: 'ACTIVO',
  sunatCondicion: 'HABIDO',
  sunatCheckedAt: '2026-10-11T15:00:00Z',
  contactName: null,
  contactEmail: null,
  contactPhone: null,
  warning: null,
};

describe('CompanyNewPage', () => {
  let el: HTMLElement;
  let sent: RegisterTrackedCompanyRequest[];
  let answer: Subject<TrackedCompany>;
  let sentUnverified: RegisterUnverifiedTrackedCompanyRequest[];
  let fallbackAnswer: Subject<TrackedCompany>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const type = (id: string, value: string, blur = false) => {
    const input = el.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    if (blur) {
      input.dispatchEvent(new Event('blur'));
    }
    TestBed.tick();
  };
  const choose = (value: string) => {
    const select = el.querySelector('#association') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    TestBed.tick();
  };
  const submit = () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
  };
  const alert = () => el.querySelector('[role="alert"]')?.textContent?.replace(/\s+/g, ' ').trim();

  beforeEach(async () => {
    sent = [];
    sentUnverified = [];
    answer = new Subject<TrackedCompany>();
    fallbackAnswer = new Subject<TrackedCompany>();
    TestBed.configureTestingModule({
      imports: [CompanyNewPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            associations: () =>
              of({ content: [association('a-1'), association('a-2', 'SUSPENDED')], page: 0, size: 100, totalElements: 2, totalPages: 1 }),
            registerTrackedCompany: (request: RegisterTrackedCompanyRequest): Observable<TrackedCompany> => {
              sent.push(request);
              return answer;
            },
            registerUnverifiedTrackedCompany: (request: RegisterUnverifiedTrackedCompanyRequest): Observable<TrackedCompany> => {
              sentUnverified.push(request);
              return fallbackAnswer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    el = TestBed.createComponent(CompanyNewPage).nativeElement as HTMLElement;
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  });

  it('offers the listed associations, suspended ones marked', () => {
    const options = [...el.querySelectorAll('#association option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['Elige una asociación', 'Asociación a-1', 'Asociación a-2 (suspendida)']);
  });

  it('the RUC must be exactly 11 digits; nothing is sent until it is', () => {
    choose('a-1');
    type('ruc', '2051234567');
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#ruc-error')?.textContent).toContain('exactamente 11 dígitos (tiene 10)');
    type('ruc', '205123456789');
    submit();
    expect(el.querySelector('#ruc-error')?.textContent).toContain('(tiene 12)');
    type('ruc', '2051234567A');
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#ruc-error')?.textContent?.trim()).toBe('El RUC lleva solo números: quita las letras u otros signos.');
  });

  it('spaces and dashes in a pasted RUC are removed; the request has no legal name and no empty contact', () => {
    type('ruc', '20-512 345.678', true);
    expect((el.querySelector('#ruc') as HTMLInputElement).value).toBe('20512345678');
    choose('a-1');
    submit();
    submit(); // ignored while sending
    expect(sent).toEqual([{ ruc: '20512345678', associationId: 'a-1' }]);
    expect('name' in sent[0]).toBe(false);
    answer.next(COMPANY);
    expect(navigate).toHaveBeenCalledWith(['/empresas', 'c-9'], { state: { registrada: true } });
  });

  it('sends the contact only for the parts that were filled, trimmed; a bad email blocks the send', () => {
    type('ruc', '20512345678');
    choose('a-1');
    type('contactName', '  Rosa Quispe ');
    type('contactEmail', 'rosa@');
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#contactEmail-error')?.textContent).toContain('El correo no es válido');
    type('contactEmail', ' rosa@example.org ');
    type('contactPhone', '999111222');
    submit();
    expect(sent).toEqual([
      { ruc: '20512345678', associationId: 'a-1', contactName: 'Rosa Quispe', contactEmail: 'rosa@example.org', contactPhone: '999111222' },
    ]);
  });

  it('says plainly what SUNAT answered, and that nothing was saved when it did not answer (RPT-011)', () => {
    type('ruc', '20512345678');
    choose('a-1');
    submit();
    answer.error(new ApiError(503, 'RPT-011', 'No se pudo consultar SUNAT', null));
    TestBed.tick();
    expect(alert()).toBe('No se pudo consultar SUNAT en este momento. Vuelve a intentarlo en un rato.');

    for (const [status, code, text] of [
      [400, 'RPT-010', 'El RUC no es válido: revisa los 11 dígitos.'],
      [422, 'RPT-012', 'SUNAT no tiene registrado ese RUC.'],
      [422, 'RPT-013', 'El RUC está en baja definitiva en SUNAT: no se puede registrar la empresa.'],
    ] as const) {
      answer = new Subject<TrackedCompany>();
      submit();
      answer.error(new ApiError(status, code, 'x', null));
      TestBed.tick();
      expect(alert()).toBe(text);
    }
  });

  describe('the fallback when SUNAT does not answer', () => {
    const sunatDown = () => {
      type('ruc', '20512345678');
      choose('a-1');
      submit();
      answer.error(new ApiError(503, 'RPT-011', 'No se pudo consultar SUNAT', null));
      TestBed.tick();
    };
    const fallbackButton = () =>
      [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Registrar como no verificada'));

    it('is not offered until SUNAT failed, and nothing is sent without a typed name', () => {
      expect(fallbackButton()).toBeUndefined();
      sunatDown();
      expect(fallbackButton()).toBeDefined();
      fallbackButton()!.click();
      TestBed.tick();
      expect(sentUnverified).toEqual([]);
      expect(el.querySelector('#name-error')?.textContent).toContain('Escribe la razón social');
    });

    it('registers the company unverified with the typed name and opens it', () => {
      sunatDown();
      type('name', '  Reciclajes Andinos  ');
      fallbackButton()!.click();
      TestBed.tick();
      expect(sentUnverified).toEqual([{ ruc: '20512345678', associationId: 'a-1', name: 'Reciclajes Andinos' }]);
      fallbackAnswer.next({ ...COMPANY, verification: 'UNVERIFIED', sunatEstado: null, sunatCondicion: null, sunatCheckedAt: null });
      expect(navigate).toHaveBeenCalledWith(['/empresas', 'c-9'], { state: { registrada: true } });
    });

    it('withdraws the offer when SUNAT answers after all (RPT-014)', () => {
      sunatDown();
      type('name', 'Reciclajes Andinos');
      fallbackButton()!.click();
      TestBed.tick();
      fallbackAnswer.error(new ApiError(409, 'RPT-014', 'x', null));
      TestBed.tick();
      expect(alert()).toContain('SUNAT sí respondió');
      expect(fallbackButton()).toBeUndefined();
    });
  });

  it('RPT-002 names the RUC and links to the list; an unknown outcome never says it failed', () => {
    type('ruc', '20512345678');
    choose('a-1');
    submit();
    answer.error(new ApiError(409, 'RPT-002', 'Ya existe una empresa rastreada con ese RUC', null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"] p')?.textContent?.trim()).toBe(
      'Ya hay una empresa registrada con el RUC 20512345678. No se puede registrar dos veces: búscala en la lista de empresas.',
    );
    expect(el.querySelector('[role="alert"] a')?.textContent).toBe('Ver la lista de empresas');

    answer = new Subject<TrackedCompany>();
    submit();
    answer.error(new ApiError(502, null, null, null));
    TestBed.tick();
    expect(alert()).toBe(COMPANY_UNCERTAIN);
    expect(el.querySelector('[role="alert"] a')).toBeNull();
  });
});
