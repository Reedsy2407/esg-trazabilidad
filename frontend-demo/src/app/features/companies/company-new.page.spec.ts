import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { Association, RegisterTrackedCompanyRequest, TrackedCompany } from '../../core/api/api.types';
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

describe('CompanyNewPage', () => {
  let el: HTMLElement;
  let sent: RegisterTrackedCompanyRequest[];
  let answer: Subject<TrackedCompany>;
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
    answer = new Subject<TrackedCompany>();
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
    type('name', 'Envases del Pacífico S.A.C.');
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

  it('spaces and dashes in a pasted RUC are removed; the request is exactly the three fields', () => {
    type('name', '  Envases del Pacífico S.A.C. ');
    type('ruc', '20-512 345.678', true);
    expect((el.querySelector('#ruc') as HTMLInputElement).value).toBe('20512345678');
    choose('a-1');
    submit();
    submit(); // ignored while sending
    expect(sent).toEqual([{ name: 'Envases del Pacífico S.A.C.', ruc: '20512345678', associationId: 'a-1' }]);
    answer.next({ id: 'c-9', name: 'Envases del Pacífico S.A.C.', ruc: '20512345678', associationId: 'a-1', status: 'ACTIVE' });
    expect(navigate).toHaveBeenCalledWith(['/empresas', 'c-9'], { state: { registrada: true } });
  });

  it('RPT-002 names the RUC and links to the list; an unknown outcome never says it failed', () => {
    type('name', 'Envases');
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
