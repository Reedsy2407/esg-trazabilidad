import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { Association, CreateAssociationRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { ASSOCIATION_UNCERTAIN, AssociationNewPage } from './association-new.page';

const CREATED: Association = {
  id: 'a-9',
  name: 'Recicladores del Sur',
  ruc: '20131312955',
  registrationNumber: null,
  address: null,
  contactEmail: null,
  contactPhone: null,
  status: 'ACTIVE',
};

describe('AssociationNewPage', () => {
  let el: HTMLElement;
  let sent: CreateAssociationRequest[];
  let answer: Subject<Association>;
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
  const submit = () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
  };
  const alert = () => el.querySelector('[role="alert"]')?.textContent?.replace(/\s+/g, ' ').trim();

  beforeEach(() => {
    sent = [];
    answer = new Subject<Association>();
    TestBed.configureTestingModule({
      imports: [AssociationNewPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            createAssociation: (request: CreateAssociationRequest): Observable<Association> => {
              sent.push(request);
              return answer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    el = TestBed.createComponent(AssociationNewPage).nativeElement as HTMLElement;
    TestBed.tick();
  });

  it('needs a name and an 11-digit RUC before anything is sent', () => {
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#name-error')?.textContent).toContain('Escribe el nombre');
    expect(el.querySelector('#ruc-error')?.textContent).toContain('Escribe el RUC');
    type('name', 'Recicladores del Sur');
    type('ruc', '2013131295');
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#ruc-error')?.textContent).toContain('exactamente 11 dígitos (tiene 10)');
  });

  it('a RUC with a wrong check digit or prefix is refused on the screen, before the request', () => {
    type('name', 'Recicladores del Sur');
    type('ruc', '20131312950');
    submit();
    expect(sent).toEqual([]);
    expect(el.querySelector('#ruc-error')?.textContent).toContain('Este RUC no es válido');
    type('ruc', '30131312955');
    submit();
    expect(sent).toEqual([]);
  });

  it('sends the trimmed name and only the optional fields that were filled', () => {
    type('name', '  Recicladores del Sur  ');
    type('ruc', '20-131 312.955', true);
    type('address', 'Av. Los Olivos 100');
    submit();
    submit(); // ignored while sending
    expect(sent).toEqual([{ name: 'Recicladores del Sur', ruc: '20131312955', address: 'Av. Los Olivos 100' }]);
    answer.next(CREATED);
    TestBed.tick();
    expect(navigate).toHaveBeenCalledWith(['/asociaciones', 'a-9']);
  });

  it('says the RUC is taken (ASO-002) and offers the list', () => {
    type('name', 'Otra');
    type('ruc', '20131312955');
    submit();
    answer.error(new ApiError(409, 'ASO-002', 'x', null));
    TestBed.tick();
    expect(alert()).toContain('Ya hay una asociación registrada con el RUC 20131312955');
    expect(el.querySelector('[role="alert"] a')?.getAttribute('href')).toBe('/asociaciones');
  });

  it('when the backend refuses the RUC (ASO-004) it says so; a gateway error never claims it failed', () => {
    type('name', 'Otra');
    type('ruc', '20131312955');
    submit();
    answer.error(new ApiError(400, 'ASO-004', 'x', null));
    TestBed.tick();
    expect(alert()).toContain('El RUC no es válido');

    const second = new Subject<Association>();
    answer = second;
    type('name', 'Otra otra');
    submit();
    second.error(new ApiError(502, null, 'x', null));
    TestBed.tick();
    expect(alert()).toBe(ASSOCIATION_UNCERTAIN);
  });
});
