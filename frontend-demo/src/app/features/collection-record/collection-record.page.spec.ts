import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import {
  Association,
  CollectionRecord,
  CollectionSchedule,
  CreateCollectionRecordRequest,
  Neighbor,
  Page,
} from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { todayInLima } from '../../shared/format';
import { CollectionRecordPage, UNCERTAIN_OUTCOME, scheduleLabel, submitErrorText } from './collection-record.page';

const page = <T,>(content: T[], totalElements = content.length): Page<T> => ({
  content,
  page: 0,
  size: 100,
  totalElements,
  totalPages: 1,
});

const NEIGHBORS: Neighbor[] = [
  { id: 'n-1', fullName: 'Rosa Quispe', phone: '987654321', address: 'Jr. Huallaga 120', district: 'Cercado de Lima', status: 'ACTIVE' },
  { id: 'n-2', fullName: 'Luis Ramos', phone: '912345678', address: 'Av. Perú 455', district: 'San Martín de Porres', status: 'INACTIVE' },
];
const ASSOCIATIONS: Association[] = [
  {
    id: 'a-1', name: 'Asociación Recicla Rímac', ruc: '20601234567', registrationNumber: 'REG-001',
    address: 'Av. Amancaes 300', contactEmail: 'contacto@reciclarimac.pe', contactPhone: '014567890', status: 'ACTIVE',
  },
  {
    id: 'a-2', name: 'Recicladores Unidos de Comas', ruc: '20609876543', registrationNumber: 'REG-002',
    address: 'Av. Túpac Amaru 1200', contactEmail: 'info@ruc.pe', contactPhone: '015551234', status: 'SUSPENDED',
  },
];
const SCHEDULES: Record<string, CollectionSchedule[]> = {
  // As the API sends them: dayOfWeek sorted as text (THURSDAY < WEDNESDAY), not by weekday.
  'n-1': [
    { id: 's-2', neighborId: 'n-1', dayOfWeek: 'THURSDAY', time: '15:30', status: 'PAUSED' },
    { id: 's-3', neighborId: 'n-1', dayOfWeek: 'WEDNESDAY', time: '07:00:00', status: 'ACTIVE' },
    { id: 's-1', neighborId: 'n-1', dayOfWeek: 'MONDAY', time: '08:00:00', status: 'ACTIVE' },
  ],
  'n-2': [],
};

/** Today in Lima as the preview prints it (dd/MM/yyyy). */
const todayInLimaText = () => todayInLima().split('-').reverse().join('/');

describe('CollectionRecordPage', () => {
  let fixture: ComponentFixture<CollectionRecordPage>;
  let el: HTMLElement;
  let posts: { neighborId: string; body: CreateCollectionRecordRequest }[];
  let answer: Subject<CollectionRecord>;

  const select = (id: string) => el.querySelector(`#${id}`) as HTMLSelectElement;
  const input = (id: string) => el.querySelector(`#${id}`) as HTMLInputElement;
  const submitButton = () => el.querySelector('button[type="submit"]') as HTMLButtonElement;
  const alertText = () => el.querySelector('[role="alert"]')?.textContent?.trim();
  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const choose = async (id: string, value: string) => {
    const s = select(id);
    s.value = value;
    s.dispatchEvent(new Event('change'));
    await settle();
  };
  const type = async (id: string, value: string) => {
    const i = input(id);
    i.value = value;
    i.dispatchEvent(new Event('input'));
    i.dispatchEvent(new Event('blur'));
    await settle();
  };
  const fillValid = async () => {
    await choose('neighbor', 'n-1');
    await choose('association', 'a-1');
    await type('weight', '12.50');
  };

  beforeEach(async () => {
    posts = [];
    answer = new Subject<CollectionRecord>();
    TestBed.configureTestingModule({
      imports: [CollectionRecordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            neighbors: () => of(page(NEIGHBORS)),
            associations: () => of(page(ASSOCIATIONS)),
            schedules: (neighborId: string) => of(page(SCHEDULES[neighborId] ?? [])),
            createCollectionRecord: (neighborId: string, body: CreateCollectionRecordRequest): Observable<CollectionRecord> => {
              posts.push({ neighborId, body });
              return answer;
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(CollectionRecordPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  });

  it('lists neighbors and associations with their state, and today in Lima as the default date', () => {
    const neighbors = Array.from(select('neighbor').options).map((o) => o.text.trim());
    expect(neighbors).toEqual(['Elige un vecino', 'Rosa Quispe · Cercado de Lima', 'Luis Ramos · San Martín de Porres (inactivo)']);
    const associations = Array.from(select('association').options).map((o) => o.text.trim());
    expect(associations).toEqual(['Elige una asociación', 'Asociación Recicla Rímac', 'Recicladores Unidos de Comas (suspendida)']);
    expect(input('date').value).toBe(todayInLima());
  });

  it('loads the chosen neighbor\'s schedules, Monday to Sunday, and drops the schedule when the neighbor changes', async () => {
    await choose('neighbor', 'n-1');
    expect(Array.from(select('schedule').options).map((o) => o.text.trim())).toEqual([
      'Sin cronograma',
      'Lunes 08:00',
      'Miércoles 07:00',
      'Jueves 15:30 (pausado)',
    ]);
    await choose('schedule', 's-2');
    await choose('neighbor', 'n-2');
    expect(select('schedule').value).toBe('');
    expect(el.textContent).toContain('Este vecino no tiene cronogramas.');
  });

  it('says what is missing or wrong, field by field, focuses the first one, and sends nothing', async () => {
    vi.useFakeTimers();
    try {
      submitButton().click();
      await settle();
      vi.runAllTimers();
      expect(document.activeElement).toBe(select('neighbor'));
    } finally {
      vi.useRealTimers();
    }
    expect(el.textContent).toContain('Elige el vecino del recojo.');
    expect(el.textContent).toContain('Elige la asociación que hizo el recojo.');
    expect(el.textContent).toContain('Escribe el peso en kilos.');
    expect(input('weight').getAttribute('aria-invalid')).toBe('true');
    expect(input('weight').getAttribute('aria-describedby')).toBe('weight-hint weight-error');

    for (const [value, message] of [
      ['0', 'El peso debe ser mayor que cero.'],
      ['0.00', 'El peso debe ser mayor que cero.'],
      ['-3', 'Escribe solo números'],
      ['12,5', 'Escribe solo números'],
      ['1.234', 'Escribe solo números'],
      ['123456789', 'Escribe solo números'],
      ['1e3', 'Escribe solo números'],
    ]) {
      await type('weight', value);
      expect(el.querySelector('#weight-error')?.textContent, value).toContain(message);
    }
    expect(posts).toEqual([]);
  });

  it('sends exactly the request fields: weight as a number, no schedule as null', async () => {
    await fillValid();
    await type('date', '2026-10-05');
    submitButton().click();
    await settle();

    expect(posts).toEqual([
      {
        neighborId: 'n-1',
        body: { associationId: 'a-1', collectionDate: '2026-10-05', weightKg: 12.5, scheduleId: null },
      },
    ]);
  });

  it('one submission at a time, then a confirmation that takes the focus, and "Registrar otro recojo" keeps the round', async () => {
    await fillValid();
    await choose('schedule', 's-1');
    submitButton().click();
    await settle();
    expect(submitButton().disabled).toBe(true);
    expect(submitButton().textContent).toContain('Registrando');
    submitButton().disabled = false;
    submitButton().click();
    await settle();
    expect(posts).toHaveLength(1);
    expect(posts[0].body.scheduleId).toBe('s-1');

    vi.useFakeTimers();
    try {
      answer.next({ id: 'r-1', neighborId: 'n-1', scheduleId: 's-1', associationId: 'a-1', collectionDate: '2026-10-05', weightKg: 12.5 });
      answer.complete();
      await settle();
      vi.runAllTimers();
      const status = el.querySelector('[role="status"]')?.textContent ?? '';
      expect(status).toContain('Recojo registrado');
      expect(status).toContain('12.50 kg');
      expect(status).toContain('05/10/2026');
      expect(status).toContain('Rosa Quispe');
      expect(document.activeElement?.textContent).toContain('Recojo registrado');

      (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Registrar otro')) as HTMLButtonElement).click();
      await settle();
      vi.runAllTimers();
      expect(select('neighbor').value).toBe('n-1');
      expect(select('association').value).toBe('a-1');
      expect(input('weight').value).toBe('');
      expect(select('schedule').value).toBe('');
      expect(document.activeElement).toBe(input('weight'));
      expect(el.textContent).not.toContain('Escribe el peso en kilos.'); // a fresh form, not an error
    } finally {
      vi.useRealTimers();
    }
  });

  it('the preview ticket follows the form as it is filled, from the real fields only, and is hidden from screen readers', async () => {
    const preview = () => el.querySelector('aside.preview') as HTMLElement;
    // [Vecino, Asociación, Fecha, Cronograma] then the kilos line.
    const values = () => [...Array.from(preview().querySelectorAll('dd'), (d) => d.textContent!.trim()), preview().querySelector('.preview-kilos')!.textContent!.trim()];
    expect(preview().getAttribute('aria-hidden')).toBe('true');
    expect(values()).toEqual(['—', '—', todayInLimaText(), 'Sin cronograma', '— kg']);

    await fillValid();
    await type('date', '2026-10-05');
    await choose('schedule', 's-1');
    expect(values()).toEqual(['Rosa Quispe · Cercado de Lima', 'Asociación Recicla Rímac', '05/10/2026', 'Lunes 08:00', '12.50 kg']);

    await type('weight', '12,5'); // invalid: the preview shows no number rather than a wrong one
    expect(values()[4]).toBe('— kg');
    await choose('schedule', '');
    expect(values()[3]).toBe('Sin cronograma');
  });

  it('a rejected submission says why and lets the user try again', async () => {
    await fillValid();
    submitButton().click();
    await settle();
    answer.error(new ApiError(409, 'COL-009', null, null));
    await settle();

    expect(alertText()).toBe('La asociación tiene una certificación vencida y no puede registrar recojos.');
    expect(submitButton().disabled).toBe(false);
    expect(select('neighbor').value).toBe('n-1'); // nothing typed is lost

    // Once the user changes something, the old rejection no longer applies.
    await choose('association', 'a-2');
    expect(alertText()).toBeUndefined();
  });

  it('a 504 (outcome unknown) warns against duplicating and keeps every field as typed', async () => {
    await fillValid();
    await type('date', '2026-10-05');
    await choose('schedule', 's-1');
    submitButton().click();
    await settle();
    answer.error(new ApiError(504, null, null, null));
    await settle();

    expect(alertText()).toBe(UNCERTAIN_OUTCOME);
    expect([select('neighbor').value, select('association').value, input('date').value, input('weight').value, select('schedule').value]).toEqual([
      'n-1',
      'a-1',
      '2026-10-05',
      '12.50',
      's-1',
    ]);
    expect(submitButton().disabled).toBe(false);
  });
});

describe('CollectionRecordPage with more neighbors than one page', () => {
  it('says the list is cut, and ties that hint to the select', async () => {
    TestBed.configureTestingModule({
      imports: [CollectionRecordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            neighbors: () => of(page(NEIGHBORS, 340)),
            associations: () => of(page(ASSOCIATIONS)),
            schedules: () => of(page([])),
            createCollectionRecord: () => of(),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(CollectionRecordPage);
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#neighbor-more')?.textContent).toContain('Se muestran los primeros 2 vecinos');
    expect(el.querySelector('#neighbor')?.getAttribute('aria-describedby')).toBe('neighbor-more');
    expect(el.querySelector('#association')?.hasAttribute('aria-describedby')).toBe(false);
  });
});

describe('submitErrorText', () => {
  it('maps every documented code, then the status', () => {
    const e = (status: number, code: string | null = null, retry: number | null = null) => new ApiError(status, code, null, retry);
    expect(submitErrorText(e(404, 'COL-001'))).toContain('El vecino elegido ya no existe');
    expect(submitErrorText(e(404, 'COL-006'))).toContain('El cronograma elegido ya no existe');
    expect(submitErrorText(e(409, 'COL-009'))).toContain('certificación vencida');
    expect(submitErrorText(e(400, 'VALIDATION_ERROR'))).toContain('Revisa la fecha y el peso');
    expect(submitErrorText(e(429, null, 9))).toBe('Demasiadas solicitudes. Vuelve a intentarlo en 9 s.');
    expect(submitErrorText(e(418))).toBe('No se pudo registrar el recojo (HTTP 418). Inténtalo de nuevo.');
  });

  it('with no answer, a 408 or any 5xx it never claims the record was not saved: the POST may have gone through', () => {
    const e = (status: number, code: string | null = null) => new ApiError(status, code, null, null);
    // A 5xx carrying a known code (none do today) and a non-ApiError are just as uncertain.
    for (const error of [e(0), e(408), e(500, 'X-500'), e(502), e(503), e(504), e(500, 'COL-009'), new Error('boom')]) {
      const message = submitErrorText(error);
      expect(message, String(error)).toBe(UNCERTAIN_OUTCOME);
      expect(message).not.toMatch(/no se registr/i);
    }
    // Known 4xx answers keep their specific messages.
    expect(submitErrorText(e(409, 'COL-009'))).toContain('certificación vencida');
    expect(submitErrorText(e(404))).not.toBe(UNCERTAIN_OUTCOME);
  });
});

describe('scheduleLabel', () => {
  it('day in Spanish, HH:mm, and the state unless active', () => {
    const s = (dayOfWeek: CollectionSchedule['dayOfWeek'], time: string, status: CollectionSchedule['status']) =>
      scheduleLabel({ id: 'x', neighborId: 'n', dayOfWeek, time, status });
    expect(s('WEDNESDAY', '07:15:00', 'ACTIVE')).toBe('Miércoles 07:15');
    expect(s('SATURDAY', '09:00', 'CANCELLED')).toBe('Sábado 09:00 (cancelado)');
  });
});

describe('todayInLima', () => {
  it('is the Lima calendar date, not UTC', () => {
    expect(todayInLima(new Date('2026-10-09T04:30:00Z'))).toBe('2026-10-08'); // 23:30 in Lima
    expect(todayInLima(new Date('2026-10-09T05:00:00Z'))).toBe('2026-10-09');
  });
});
