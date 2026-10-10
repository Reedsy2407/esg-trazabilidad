import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { CollectionRecord, CollectionSchedule, Page, ScheduleTransition } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { NeighborPage } from './neighbor.page';

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

describe('NeighborPage', () => {
  let el: HTMLElement;
  let transitions: { id: string; transition: ScheduleTransition }[];
  let transitionAnswer: Subject<CollectionSchedule>;
  let recordCalls: { page: number; from: string | null; to: string | null }[];

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (name: string) =>
    [...el.querySelectorAll('button')].find((b) => (b.getAttribute('aria-label') ?? b.textContent?.trim()) === name) as HTMLButtonElement;

  beforeEach(async () => {
    transitions = [];
    recordCalls = [];
    transitionAnswer = new Subject<CollectionSchedule>();
    TestBed.configureTestingModule({
      imports: [NeighborPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            neighbor: () => of({ id: 'n-1', fullName: 'Rosa', phone: null, address: 'Jr. Huallaga', district: null, status: 'ACTIVE' }),
            schedules: () => of(page([schedule('s-th', 'THURSDAY', 'PAUSED'), schedule('s-mo', 'MONDAY', 'ACTIVE')])),
            associations: () => of(page([{ id: 'a-1', name: 'Recicla Rímac' }])),
            collectionRecords: (_: string, p: number, from: string | null, to: string | null): Observable<Page<CollectionRecord>> => {
              recordCalls.push({ page: p, from, to });
              return of(page([record(`r-${p}`, '2026-10-05')], 25, p));
            },
            transitionSchedule: (_: string, id: string, transition: ScheduleTransition) => {
              transitions.push({ id, transition });
              return transitionAnswer;
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(NeighborPage);
    fixture.componentRef.setInput('id', 'n-1');
    el = fixture.nativeElement as HTMLElement;
    await settle();
  });

  it('missing district and phone say "No registrado"; schedules run Monday to Sunday', () => {
    expect([...el.querySelectorAll('.details dd.missing')].map((d) => d.textContent?.trim())).toEqual(['No registrado', 'No registrado']);
    expect([...el.querySelectorAll('.schedules .when')].map((w) => w.textContent?.trim())).toEqual(['Lunes 08:00', 'Jueves 08:00']);
  });

  it('a refused transition says why, per code, and takes the focus', async () => {
    const cases: [ApiError, string][] = [
      [new ApiError(409, 'COL-008', null, null), 'El cronograma ya cambió de estado. Vuelve a cargar los cronogramas para ver el actual.'],
      [new ApiError(409, 'COL-002', null, null), 'No se puede reactivar: ya hay otro cronograma activo el jueves.'],
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
    expect(transitions.map((t) => t.transition)).toEqual(['reactivate', 'reactivate', 'reactivate']);
  });

  it('cancel asks first; only "Sí" sends it', async () => {
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
});
