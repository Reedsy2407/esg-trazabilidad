import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import {
  CollectionRecord,
  CollectionSchedule,
  EditNeighborRequest,
  Neighbor,
  NeighborStatusChange,
  NeighborTransition,
  Page,
  ScheduleTransition,
} from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { pickDistrict } from '../../testing/district-select';
import { COL_011_TEXT, DELETE_UNCERTAIN, NeighborPage, STATUS_UNCERTAIN } from './neighbor.page';

const page = <T,>(content: T[], totalElements = content.length, number = 0): Page<T> => ({
  content,
  page: number,
  size: 20,
  totalElements,
  totalPages: Math.ceil(totalElements / 20),
});
const schedule = (id: string, dayOfWeek: CollectionSchedule['dayOfWeek'], status: CollectionSchedule['status']): CollectionSchedule => ({
  id,
  neighborId: 'n-1',
  dayOfWeek,
  time: '08:00:00',
  status,
});
const record = (id: string, collectionDate: string): CollectionRecord => ({
  id,
  neighborId: 'n-1',
  scheduleId: null,
  associationId: 'a-1',
  collectionDate,
  weightKg: 5,
});

const ROSA: Neighbor = {
  id: 'n-1',
  fullName: 'Rosa',
  phone: null,
  address: 'Jr. Huallaga',
  district: null,
  districtUbigeo: null,
  status: 'ACTIVE',
};

describe('NeighborPage', () => {
  let el: HTMLElement;
  let transitions: { id: string; transition: ScheduleTransition }[];
  let transitionAnswer: Subject<CollectionSchedule>;
  let recordCalls: { page: number; from: string | null; to: string | null }[];
  let current: Neighbor;
  let schedules: CollectionSchedule[];
  let recordsTotal: number;
  let history: NeighborStatusChange[];
  let edits: EditNeighborRequest[];
  let editAnswer: Subject<Neighbor>;
  let statusCalls: { transition: NeighborTransition; reason: string }[];
  let statusAnswer: Subject<Neighbor>;
  let deletes: string[];
  let deleteAnswer: Subject<void>;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (name: string) =>
    [...el.querySelectorAll('button')].find((b) => (b.getAttribute('aria-label') ?? b.textContent?.trim()) === name) as HTMLButtonElement;

  const create = async (neighbor: Neighbor = ROSA) => {
    current = neighbor;
    const fixture = TestBed.createComponent(NeighborPage);
    fixture.componentRef.setInput('id', 'n-1');
    el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    await settle();
  };
  const type = (selector: string, value: string) => {
    const field = el.querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    TestBed.tick();
  };
  const check = (root: Element) => {
    const box = root.querySelector('input[type="checkbox"]') as HTMLInputElement;
    box.checked = true;
    box.dispatchEvent(new Event('change'));
    TestBed.tick();
  };
  const submitPanel = async () => {
    (el.querySelector('app-reason-confirm form') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
    await settle();
  };

  afterEach(() => el?.remove());

  beforeEach(() => {
    transitions = [];
    recordCalls = [];
    schedules = [schedule('s-th', 'THURSDAY', 'PAUSED'), schedule('s-mo', 'MONDAY', 'ACTIVE')];
    recordsTotal = 25;
    history = [];
    edits = [];
    statusCalls = [];
    deletes = [];
    editAnswer = new Subject<Neighbor>();
    statusAnswer = new Subject<Neighbor>();
    deleteAnswer = new Subject<void>();
    transitionAnswer = new Subject<CollectionSchedule>();
    TestBed.configureTestingModule({
      imports: [NeighborPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            neighbor: () => of(current),
            schedules: () => of(page([...schedules])),
            neighborStatusChanges: () => of(history),
            editNeighbor: (_: string, request: EditNeighborRequest) => {
              edits.push(request);
              return editAnswer;
            },
            changeNeighborStatus: (_: string, transition: NeighborTransition, reason: string) => {
              statusCalls.push({ transition, reason });
              return statusAnswer;
            },
            deleteNeighbor: (_: string, reason: string) => {
              deletes.push(reason);
              return deleteAnswer;
            },
            associations: () => of(page([{ id: 'a-1', name: 'Recicla Rímac' }])),
            collectionRecords: (_: string, p: number, from: string | null, to: string | null): Observable<Page<CollectionRecord>> => {
              recordCalls.push({ page: p, from, to });
              return of(page(recordsTotal === 0 ? [] : [record(`r-${p}`, '2026-10-05')], recordsTotal, p));
            },
            transitionSchedule: (_: string, id: string, transition: ScheduleTransition) => {
              transitions.push({ id, transition });
              return transitionAnswer;
            },
          },
        },
      ],
    });
  });

  it('missing district and phone say "No registrado"; schedules run Monday to Sunday', async () => {
    await create();
    expect([...el.querySelectorAll('.details dd.missing')].map((d) => d.textContent?.trim())).toEqual(['No registrado', 'No registrado']);
    expect([...el.querySelectorAll('.schedules .when')].map((w) => w.textContent?.trim())).toEqual(['Lunes 08:00', 'Jueves 08:00']);
  });

  it('a refused transition says why, per code, and takes the focus', async () => {
    await create();
    const cases: [ApiError, string][] = [
      [new ApiError(409, 'COL-008', null, null), 'El cronograma ya cambió de estado. Vuelve a cargar los cronogramas para ver el actual.'],
      [new ApiError(409, 'COL-002', null, null), 'No se puede reactivar: ya hay otro cronograma activo el jueves.'],
      [new ApiError(409, 'COL-013', null, null), 'El vecino está inactivo: reactiva primero al vecino y luego sus cronogramas.'],
      [new ApiError(503, null, null, null), 'No pudimos confirmar si el cambio se aplicó. Vuelve a cargar los cronogramas para ver su estado.'],
    ];
    for (const [error, text] of cases) {
      button('Reactivar cronograma del jueves 08:00').click();
      TestBed.tick();
      transitionAnswer.error(error);
      transitionAnswer = new Subject<CollectionSchedule>();
      await settle();
      await new Promise((resolve) => setTimeout(resolve));
      const problem = el.querySelector('.row-problem') as HTMLElement;
      expect(problem.textContent?.trim()).toBe(text);
      expect(document.activeElement).toBe(problem);
    }
    expect(transitions.map((t) => t.transition)).toEqual(['reactivate', 'reactivate', 'reactivate', 'reactivate']);
  });

  it('cancel asks first; only "Sí" sends it', async () => {
    await create();
    button('Cancelar cronograma del lunes 08:00').click();
    await settle();
    expect(el.querySelector('.confirm')?.textContent).toContain('Es definitivo');
    expect(transitions).toEqual([]);
    button('No, mantenerlo').click();
    await settle();
    expect(el.querySelector('.confirm')).toBeNull();
    button('Cancelar cronograma del lunes 08:00').click();
    await settle();
    button('Sí, cancelar cronograma').click();
    expect(transitions).toEqual([{ id: 's-mo', transition: 'cancel' }]);
  });

  it('records: pages and a date range (from after to is refused, nothing sent)', async () => {
    await create();
    expect(recordCalls).toEqual([{ page: 0, from: null, to: null }]);
    expect(el.querySelector('section[aria-labelledby="records-title"] .count')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      '1–1 de 25 recojos',
    );
    button('Siguiente').click();
    await settle();
    expect(recordCalls.at(-1)).toEqual({ page: 1, from: null, to: null });

    const set = (id: string, value: string) => {
      const input = el.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('from', '2026-10-06');
    set('to', '2026-10-01');
    (el.querySelector('form.range') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#range-error')?.textContent).toContain('«desde» debe ser anterior');
    expect(el.querySelector('#from')?.getAttribute('aria-invalid')).toBe('true');
    expect(recordCalls).toHaveLength(2);

    set('to', '2026-10-31');
    (el.querySelector('form.range') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    expect(recordCalls.at(-1)).toEqual({ page: 0, from: '2026-10-06', to: '2026-10-31' });
  });

  it('edit: prefilled, the district from the list, phone cleared as null; the saved data replaces the view', async () => {
    await create({ ...ROSA, phone: '987654321', district: 'Comas', districtUbigeo: '150110' });
    button('Editar datos').click();
    await settle();
    expect((el.querySelector('#edit-fullName') as HTMLInputElement).value).toBe('Rosa');
    expect((el.querySelector('#edit-district') as HTMLInputElement).value).toBe('Comas');
    type('#edit-fullName', '  Rosa Quispe ');
    type('#edit-phone', ' ');
    pickDistrict(el, 'edit-district', 'breña');
    TestBed.tick();
    (el.querySelector('form.edit') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    expect(edits).toEqual([{ fullName: 'Rosa Quispe', address: 'Jr. Huallaga', districtUbigeo: '150105', phone: null }]);
    editAnswer.next({ ...ROSA, fullName: 'Rosa Quispe', district: 'Breña', districtUbigeo: '150105' });
    await settle();
    expect(el.querySelector('form.edit')).toBeNull();
    expect(el.querySelector('h1')?.textContent).toBe('Rosa Quispe');
    expect(el.querySelector('.details')?.textContent).toContain('Breña');
    expect(el.textContent).toContain('Datos del vecino guardados.');
  });

  it('edit: an unknown outcome never claims it failed; COL-010 says the district is outside the list', async () => {
    await create({ ...ROSA, districtUbigeo: '150110', district: 'Comas' });
    button('Editar datos').click();
    await settle();
    (el.querySelector('form.edit') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    editAnswer.error(new ApiError(502, null, null, null));
    await settle();
    expect(el.querySelector('form.edit [role="alert"]')?.textContent?.trim()).toContain('No pudimos confirmar si los cambios se guardaron');
    editAnswer = new Subject<Neighbor>();
    (el.querySelector('form.edit') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    editAnswer.error(new ApiError(400, 'COL-010', null, null));
    await settle();
    expect(el.querySelector('form.edit [role="alert"]')?.textContent?.trim()).toBe(
      'El distrito no está en la lista de Lima Metropolitana y Callao.',
    );
  });

  it('a pre-v3 neighbour without a district code says so', async () => {
    await create({ ...ROSA, district: 'Surco', districtUbigeo: null });
    expect(el.querySelector('.details')?.textContent).toContain('Surco (sin código: elige el distrito al editar)');
  });

  it('deactivate: needs a reason and the checkbox; sends the trimmed reason and shows the new state', async () => {
    await create();
    button('Desactivar vecino').click();
    await settle();
    await new Promise((resolve) => setTimeout(resolve));
    const panel = el.querySelector('app-reason-confirm') as HTMLElement;
    expect(document.activeElement).toBe(panel.querySelector('textarea'));
    const submitButton = () => panel.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submitButton().disabled).toBe(true);
    expect(submitButton().textContent?.trim()).toBe('Desactivar a Rosa');
    check(panel);
    await submitPanel();
    expect(statusCalls).toEqual([]);
    expect(panel.textContent).toContain('Escribe el motivo.');
    type('app-reason-confirm textarea', '  Se mudó  ');
    await submitPanel();
    expect(statusCalls).toEqual([{ transition: 'deactivate', reason: 'Se mudó' }]);
    history = [
      { id: 'h1', fromStatus: 'ACTIVE', toStatus: 'INACTIVE', reason: 'Se mudó', actorId: 'u', actorEmail: 'ana@esg.pe', changedAt: '2026-10-10T15:04:00Z' },
    ];
    schedules = [schedule('s-th', 'THURSDAY', 'PAUSED'), schedule('s-mo', 'MONDAY', 'PAUSED')];
    statusAnswer.next({ ...ROSA, status: 'INACTIVE' });
    await settle();
    expect(el.querySelector('.meta')?.textContent).toBe('Inactivo');
    expect(el.textContent).toContain('Vecino desactivado. Sus cronogramas activos quedaron pausados.');
    expect(el.querySelector('.history')?.textContent).toContain('Desactivado');
    expect(el.querySelector('.history')?.textContent).toContain('10/10/2026 10:04'); // Lima time
    expect(el.querySelector('.history')?.textContent).toContain('ana@esg.pe');
    expect(el.querySelector('.history')?.textContent).toContain('Motivo: Se mudó');
    // Inactive: no collection link, no new schedule, no schedule reactivation.
    expect(el.textContent).not.toContain('Registrar recojo de este vecino');
    expect(el.querySelector('form.add')).toBeNull();
    expect(el.textContent).toContain('reactiva primero al vecino');
    expect(button('Reactivar cronograma del jueves 08:00')).toBeUndefined();
  });

  it('reactivate: then its paused schedules are offered one by one (D8)', async () => {
    await create({ ...ROSA, status: 'INACTIVE' });
    schedules = [schedule('s-th', 'THURSDAY', 'PAUSED'), schedule('s-mo', 'MONDAY', 'PAUSED')];
    button('Reactivar vecino').click();
    await settle();
    type('app-reason-confirm textarea', 'Volvió');
    check(el.querySelector('app-reason-confirm') as HTMLElement);
    await submitPanel();
    expect(statusCalls).toEqual([{ transition: 'activate', reason: 'Volvió' }]);
    statusAnswer.next({ ...ROSA, status: 'ACTIVE' });
    await settle();
    const callout = el.querySelector('.paused-callout') as HTMLElement;
    expect(callout.textContent).toContain('Cronogramas pausados de este vecino');
    expect([...callout.querySelectorAll('.when')].map((w) => w.textContent?.trim())).toEqual(['Lunes 08:00', 'Jueves 08:00']);
    (callout.querySelector('button') as HTMLButtonElement).click();
    expect(transitions).toEqual([{ id: 's-mo', transition: 'reactivate' }]);
  });

  it('a status change: unknown outcome, COL-012, and "No, volver" sends nothing', async () => {
    await create();
    button('Desactivar vecino').click();
    await settle();
    type('app-reason-confirm textarea', 'x');
    check(el.querySelector('app-reason-confirm') as HTMLElement);
    await submitPanel();
    statusAnswer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('app-reason-confirm [role="alert"]')?.textContent?.trim()).toBe(STATUS_UNCERTAIN);
    statusAnswer = new Subject<Neighbor>();
    await submitPanel();
    statusAnswer.error(new ApiError(409, 'COL-012', null, null));
    await settle();
    expect(el.querySelector('app-reason-confirm [role="alert"]')?.textContent?.trim()).toBe(
      'El vecino ya estaba en ese estado. Vuelve a cargar la página para ver el actual.',
    );
    button('No, volver').click();
    await settle();
    expect(el.querySelector('app-reason-confirm')).toBeNull();
    expect(statusCalls).toHaveLength(2);
  });

  it('delete: offered only without collections; navigates to the list; COL-011 and unknown outcomes explained', async () => {
    await create();
    expect(button('Eliminar vecino')).toBeUndefined(); // 25 collections
    expect(el.textContent).toContain('tiene recojos registrados: no se puede eliminar, solo desactivar');

    el.remove();
    TestBed.resetTestingModule();
    recordsTotal = 0;
    TestBed.configureTestingModule({
      imports: [NeighborPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            neighbor: () => of(ROSA),
            schedules: () => of(page([])),
            neighborStatusChanges: () => of([]),
            associations: () => of(page([])),
            collectionRecords: () => of(page([])),
            deleteNeighbor: (_: string, reason: string) => {
              deletes.push(reason);
              return deleteAnswer;
            },
          },
        },
      ],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await create();
    button('Eliminar vecino').click();
    await settle();
    type('app-reason-confirm textarea', 'Registrado dos veces');
    check(el.querySelector('app-reason-confirm') as HTMLElement);
    await submitPanel();
    deleteAnswer.error(new ApiError(409, 'COL-011', null, null));
    await settle();
    expect(el.querySelector('app-reason-confirm [role="alert"]')?.textContent?.trim()).toBe(COL_011_TEXT);
    deleteAnswer = new Subject<void>();
    await submitPanel();
    deleteAnswer.error(new ApiError(0, null, null, null));
    await settle();
    expect(el.querySelector('app-reason-confirm [role="alert"]')?.textContent?.trim()).toBe(DELETE_UNCERTAIN);
    deleteAnswer = new Subject<void>();
    await submitPanel();
    deleteAnswer.next();
    deleteAnswer.complete();
    await settle();
    expect(deletes).toEqual(['Registrado dos veces', 'Registrado dos veces', 'Registrado dos veces']);
    expect(navigate).toHaveBeenCalledWith(['/vecinos'], { state: { eliminado: 'Rosa' } });
  });
});
