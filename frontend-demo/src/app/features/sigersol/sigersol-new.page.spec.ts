import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { RegisterSigersolSyncRequest, SigersolSync } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { SIGERSOL_UNCERTAIN, SigersolNewPage } from './sigersol-new.page';

describe('SigersolNewPage', () => {
  let el: HTMLElement;
  let sent: RegisterSigersolSyncRequest[];
  let answer: Subject<SigersolSync>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const setup = async (inputs: Record<string, string> = {}) => {
    sent = [];
    answer = new Subject<SigersolSync>();
    TestBed.configureTestingModule({
      imports: [SigersolNewPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            associations: () =>
              of({
                content: [{ id: 'a-1', name: 'Recicla Rímac', ruc: '20601234567', registrationNumber: null, address: null, contactEmail: null, contactPhone: null, status: 'ACTIVE' }],
                page: 0,
                size: 100,
                totalElements: 1,
                totalPages: 1,
              }),
            registerSigersolSync: (request: RegisterSigersolSyncRequest): Observable<SigersolSync> => {
              sent.push(request);
              return answer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SigersolNewPage);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    el = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const set = (id: string, value: string) => {
    const input = el.querySelector(`#${id}`) as HTMLInputElement | HTMLSelectElement;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
    TestBed.tick();
  };
  const submit = () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
  };
  const fill = () => {
    set('association', 'a-1');
    set('periodStart', '2026-10-01');
    set('periodEnd', '2026-10-31');
    set('compliance', '88.5');
  };

  it('says it is a manual entry', async () => {
    await setup();
    expect(el.querySelector('app-manual-tag')?.textContent?.trim()).toBe('Ingreso manual');
  });

  it('prefills association and period from the address', async () => {
    await setup({ asociacion: 'a-1', desde: '2024-12-01', hasta: '2024-12-31' });
    expect((el.querySelector('#association') as HTMLSelectElement).value).toBe('a-1');
    expect((el.querySelector('#periodStart') as HTMLInputElement).value).toBe('2024-12-01');
    expect((el.querySelector('#period-hint') as HTMLElement).textContent).toContain('Diciembre 2024');
  });

  it('checks the columns\' precision and the period before sending anything', async () => {
    await setup();
    fill();
    for (const [value, text] of [
      ['100.01', 'no puede pasar de 100'],
      ['88.555', 'hasta 2 decimales'],
      ['88,5', 'hasta 2 decimales'],
      ['-1', 'hasta 2 decimales'],
    ]) {
      set('compliance', value);
      submit();
      expect(el.querySelector('#compliance-error')?.textContent).toContain(text);
    }
    set('compliance', '100');
    set('kilos', '1234567890123');
    submit();
    expect(el.querySelector('#kilos-error')).not.toBeNull();
    // 12 integer digits and 2 decimals is the column's largest value: accepted.
    set('kilos', '999999999999.99');
    submit();
    expect(el.querySelector('#kilos-error')).toBeNull();
    expect(sent).toHaveLength(1);
    sent.length = 0;
    answer.error(new ApiError(409, 'RPT-006', null, null));
    answer = new Subject<SigersolSync>();
    set('kilos', '');
    set('periodEnd', '2026-09-30');
    submit();
    expect(el.querySelector('#period-error')?.textContent).toContain('no puede ser anterior');
    expect(sent).toEqual([]);
    // The same day as start and end is a valid one-day period.
    set('periodEnd', '2026-10-01');
    submit();
    expect(sent).toHaveLength(1);
  });

  it('sends exactly the request: numbers, optionals as null, trimmed note; then lists the association', async () => {
    await setup();
    fill();
    set('note', '  Reporte de octubre  ');
    submit();
    submit();
    expect(sent).toEqual([
      {
        associationId: 'a-1',
        periodStart: '2026-10-01',
        periodEnd: '2026-10-31',
        hierarchyCompliancePercent: 88.5,
        officialKilosDeclared: null,
        sourceNote: 'Reporte de octubre',
      },
    ]);
    answer.next({} as SigersolSync);
    expect(navigate).toHaveBeenCalledWith(['/sigersol'], { queryParams: { asociacion: 'a-1' }, state: { registrado: true } });
  });

  it('the note can\'t pass 255 characters (longer would come back as a misleading RPT-006)', async () => {
    await setup();
    const note = el.querySelector('#note') as HTMLTextAreaElement;
    expect(note.getAttribute('maxlength')).toBe('255');
    expect(el.querySelector('#note-count')?.textContent?.trim()).toBe('0 de 255 caracteres');
    set('note', 'Reporte de octubre');
    expect(el.querySelector('#note-count')?.textContent?.trim()).toBe('18 de 255 caracteres');
  });

  it('RPT-006 explains the overlap and links to the association\'s records; 5xx is an unknown outcome', async () => {
    await setup();
    fill();
    submit();
    answer.error(new ApiError(409, 'RPT-006', null, null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"] p')?.textContent).toContain('compartir un solo día también cuenta');
    expect(el.querySelector('[role="alert"] a')?.textContent).toBe('Ver los registros de esta asociación');

    answer = new Subject<SigersolSync>();
    submit();
    answer.error(new ApiError(503, null, null, null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"] p')?.textContent?.trim()).toBe(SIGERSOL_UNCERTAIN);
    // It says to check the association's records, and links there.
    expect(el.querySelector('[role="alert"] a')?.textContent).toBe('Ver los registros de esta asociación');

    answer = new Subject<SigersolSync>();
    submit();
    answer.error(new ApiError(0, null, null, null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"] p')?.textContent?.trim()).toBe(SIGERSOL_UNCERTAIN);

    answer = new Subject<SigersolSync>();
    submit();
    answer.error(new ApiError(400, 'VALIDATION_ERROR', null, null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"] p')?.textContent?.trim()).toBe('El servicio rechazó los datos enviados. Revisa los campos.');
    expect(el.querySelector('[role="alert"] a')).toBeNull();
  });
});
