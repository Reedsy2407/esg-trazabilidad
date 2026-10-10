import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { CreateNeighborRequest, Neighbor } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { NEIGHBOR_UNCERTAIN, NeighborNewPage } from './neighbor-new.page';

describe('NeighborNewPage', () => {
  let el: HTMLElement;
  let sent: CreateNeighborRequest[];
  let answer: Subject<Neighbor>;
  let navigate: ReturnType<typeof vi.fn>;

  const type = (id: string, value: string) => {
    const input = el.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    TestBed.tick();
  };

  beforeEach(() => {
    sent = [];
    answer = new Subject<Neighbor>();
    TestBed.configureTestingModule({
      imports: [NeighborNewPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            createNeighbor: (request: CreateNeighborRequest): Observable<Neighbor> => {
              sent.push(request);
              return answer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as unknown as ReturnType<typeof vi.fn>;
    const fixture = TestBed.createComponent(NeighborNewPage);
    el = fixture.nativeElement as HTMLElement;
    TestBed.tick();
  });

  it('blank required fields are errors and nothing is sent', () => {
    type('fullName', '   ');
    submit();
    expect(sent).toEqual([]);
    expect(el.textContent).toContain('Escribe el nombre del vecino.');
    expect(el.textContent).toContain('Escribe la dirección donde se recoge.');
  });

  it('sends trimmed values, blank optionals as null, once, and opens the new neighbour', () => {
    type('fullName', ' Carmen Flores ');
    type('address', ' Jr. Ancash 812 ');
    type('district', ' Rímac ');
    type('phone', ' ');
    submit();
    submit(); // a second click while sending is ignored
    expect(sent).toEqual([{ fullName: 'Carmen Flores', address: 'Jr. Ancash 812', district: 'Rímac', phone: null }]);
    expect((el.querySelector('button[type="submit"]') as HTMLButtonElement).textContent?.trim()).toBe('Registrando…');
    answer.next({ id: 'n-9', fullName: 'Carmen Flores', address: 'Jr. Ancash 812', district: 'Rímac', phone: null, status: 'ACTIVE' });
    expect(navigate).toHaveBeenCalledWith(['/vecinos', 'n-9'], { state: { registrado: true } });
  });

  it('an unknown outcome never says it failed; a rejection says why', () => {
    type('fullName', 'Carmen');
    type('address', 'Jr. Ancash');
    submit();
    answer.error(new ApiError(504, null, null, null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(NEIGHBOR_UNCERTAIN);

    answer = new Subject<Neighbor>();
    submit();
    answer.error(new ApiError(400, 'VALIDATION_ERROR', 'fullName: no debe estar vacío', null));
    TestBed.tick();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('El servicio rechazó los datos enviados. Revisa los campos.');
  });
});
