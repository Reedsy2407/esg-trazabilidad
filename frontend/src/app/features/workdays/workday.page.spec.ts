import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/interceptors';
import { PHOTO_URL_LIFETIME_MS, WorkdayPage } from './workday.page';

const { collection, recycler } = environment.services;
const WORKDAY_URL = `${collection}/workdays/w-1`;
const RECYCLER_URL = `${recycler}/associations/a-1/recyclers/r-1`;
// Not a real signed URL: a data URI cannot be mistaken for one, and nothing is fetched.
const SIGNED = 'data:image/gif;base64,R0lGODlhAQABAAAAACw=';

const point = (n: number) => ({ deviceSeq: n, recordedAt: `2026-11-13T13:0${n}:00Z`, lat: -12.04 - n / 1000, lng: -77.04 + n / 1000, accuracyM: 8 });
const checkin = (n: number, photo: object, extra: object = {}) => ({
  checkinId: `c-${n}`,
  recordId: `r-${n}`,
  recordedAt: `2026-11-13T13:1${n}:00Z`,
  neighborId: `n-${n}`,
  neighborName: `Vecino ${n}`,
  scheduleId: `s-${n}`,
  weightKg: n * 2.5,
  wasteType: 'PET',
  lat: -12.04,
  lng: -77.04,
  accuracyM: 8,
  photo,
  ...extra,
});
const detail = (extra: object = {}) => ({
  workdayId: 'w-1',
  recyclerId: 'r-1',
  associationId: 'a-1',
  startedAt: '2026-11-13T13:05:00Z',
  endedAt: '2026-11-13T17:30:00Z',
  withGps: true,
  points: [point(1), point(2), point(3)],
  pointsTotal: 3,
  pointsOffset: 0,
  routeSummary: null,
  checkins: [
    checkin(1, { state: 'AVAILABLE', url: SIGNED }),
    checkin(2, { state: 'DELETED', url: null }),
    checkin(3, { state: 'NONE', url: null }, { lat: null, lng: null, accuracyM: null }),
  ],
  ...extra,
});

describe('WorkdayPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let fixture: ComponentFixture<WorkdayPage>;

  const settle = async () => {
    await Promise.resolve();
    TestBed.tick();
  };
  const flush = async (req: TestRequest, body: object) => {
    req.flush(body);
    await settle();
  };

  async function create(body: object = detail()) {
    TestBed.configureTestingModule({
      imports: [WorkdayPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkdayPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    fixture.componentRef.setInput('workdayId', 'w-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne((r) => r.url === WORKDAY_URL), body);
    await flush(backend.expectOne(RECYCLER_URL), { id: 'r-1', fullName: 'Teodora Condori Apaza', associationId: 'a-1', status: 'ACTIVE' });
  }

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('shows the facts, draws the route as SVG without any map, and lists the check-ins in order', async () => {
    await create();
    expect(page.querySelector('h1')?.textContent).toContain('13/11/2026');
    expect(page.textContent).toContain('Teodora Condori Apaza');
    expect(page.querySelector('.summary')?.textContent).toContain('15.00 kg');
    const svg = page.querySelector('svg[role=img]')!;
    expect(svg.getAttribute('aria-label')).toContain('3 puntos');
    expect(svg.querySelectorAll('.stop')).toHaveLength(2); // the check-in without location is not drawn
    expect(page.querySelector('iframe, img[src*="tile"], [class*="leaflet"]')).toBeNull();
    const rows = [...page.querySelectorAll('table tbody tr')].map((r) => r.textContent!.replace(/\s+/g, ' ').trim());
    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Vecino 1');
    expect(rows[0]).toContain('Plástico PET');
    expect(rows[0]).toContain('Sí, ±8 m');
    expect(rows[2]).toContain('No');
  });

  it('draws the photo as an image only: the signed URL is never text and never a link', async () => {
    await create();
    expect(page.querySelectorAll('img')).toHaveLength(2); // table and narrow-screen list
    expect(page.querySelector('img')?.getAttribute('src')).toBe(SIGNED);
    expect(page.textContent).not.toContain(SIGNED);
    expect(page.querySelector(`a[href*="data:image"]`)).toBeNull();
    expect(page.textContent).toContain('Borrada por la retención');
    expect(page.textContent).toContain('Sin foto');
  });

  it('on the narrow-screen list every check-in labels its photo field, with or without a photo', async () => {
    await create();
    const cards = Array.from(page.querySelectorAll('ul.entries > li'));
    expect(cards.length).toBeGreaterThan(0);
    for (const card of cards) {
      const labels = Array.from(card.querySelectorAll('dt')).map((dt) => dt.textContent?.trim());
      expect(labels).toContain('Foto');
    }
    const states = cards.map((card) => card.querySelector('.photo-state')?.textContent?.trim()).filter(Boolean);
    // The state text sits in the value of the "Foto" row, not loose under the list of facts.
    for (const card of cards) {
      const state = card.querySelector('.photo-state');
      if (state) expect(state.closest('div')?.querySelector('dt')?.textContent?.trim()).toBe('Foto');
    }
    expect(states).toContain('Borrada por la retención');
    expect(states).toContain('Sin foto');
  });

  it('a photo can be enlarged and reduced from the keyboard-reachable button', async () => {
    await create();
    const thumb = page.querySelector<HTMLButtonElement>('button.thumb')!;
    expect(thumb.getAttribute('aria-label')).toBe('Ampliar la foto del check-in 1');
    thumb.click();
    TestBed.tick();
    expect(page.querySelector('button.thumb')!.getAttribute('aria-pressed')).toBe('true');
    expect(page.querySelector('button.thumb')!.getAttribute('aria-label')).toBe('Reducir la foto del check-in 1');
  });

  it('the photos are dropped when their URLs expire, and "Recargar fotos" asks the service again', async () => {
    vi.useFakeTimers();
    await create();
    expect(page.querySelector('img')).not.toBeNull();
    vi.advanceTimersByTime(PHOTO_URL_LIFETIME_MS + 1);
    TestBed.tick();
    expect(page.querySelector('img')).toBeNull();
    expect(page.textContent).toContain('Foto vencida');
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Recargar fotos');

    [...page.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Recargar fotos')!.click();
    TestBed.tick();
    // While the new answer is on its way the old (expired) URLs are not drawn again.
    expect(page.querySelector('img')).toBeNull();
    await flush(backend.expectOne((r) => r.url === WORKDAY_URL), detail());
    expect(page.querySelector('img')).not.toBeNull();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('an image that fails to load is reported and can be renewed', async () => {
    await create();
    page.querySelector('img')!.dispatchEvent(new Event('error'));
    TestBed.tick();
    expect(page.textContent).toContain('Foto vencida');
    expect(page.querySelector('[role=alert]')?.textContent).toContain('no se pudo cargar');
  });

  it('after the retention the route is the summary polyline and the page says the exact points are gone', async () => {
    await create(
      detail({
        points: [],
        pointsTotal: 0,
        routeSummary: { pointCount: 118, distanceM: 3180.5, firstAt: '2026-07-20T13:01:00Z', lastAt: '2026-07-20T15:08:00Z', polyline: [[-12.04, -77.04], [-12.05, -77.03], [-12.06, -77.05]] },
      }),
    );
    expect(page.querySelector('svg[role=img]')?.getAttribute('aria-label')).toContain('118 puntos, 3.18 km (resumen simplificado)');
    expect(page.textContent).toContain('ya pasaron su plazo de retención');
    expect(page.querySelector('.summary')?.textContent).toContain('3.18 km');
  });

  it('a workday without location says there is no route, and one without check-ins says so', async () => {
    await create(detail({ withGps: false, points: [], pointsTotal: 0, checkins: [] }));
    expect(page.querySelector('svg[role=img]')).toBeNull();
    expect(page.textContent).toContain('sin ubicación');
    expect(page.textContent).toContain('todavía no tiene check-ins');
    expect([...page.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Recargar fotos')).toBe(false);
  });

  it('asks for the remaining windows of points when the service serves them in pieces', async () => {
    TestBed.configureTestingModule({
      imports: [WorkdayPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkdayPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    fixture.componentRef.setInput('workdayId', 'w-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne((r) => r.url === WORKDAY_URL && r.params.get('pointsOffset') === '0'), detail({ points: [point(1), point(2)], pointsTotal: 3 }));
    await flush(backend.expectOne((r) => r.url === WORKDAY_URL && r.params.get('pointsOffset') === '2'), detail({ points: [point(3)], pointsTotal: 3, pointsOffset: 2 }));
    backend.expectOne(RECYCLER_URL).flush({ id: 'r-1', fullName: 'T', associationId: 'a-1', status: 'ACTIVE' });
    await settle();
    expect(page.querySelector('svg[role=img]')?.getAttribute('aria-label')).toContain('3 puntos');
  });

  it('an unknown workday is an alert with a retry', async () => {
    TestBed.configureTestingModule({
      imports: [WorkdayPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkdayPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    fixture.componentRef.setInput('workdayId', 'w-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    backend.expectOne((r) => r.url === WORKDAY_URL).flush({ code: 'COL-016', detail: 'x', status: 404, title: 'x' }, { status: 404, statusText: 'Not Found' });
    backend.expectOne(RECYCLER_URL).flush({ id: 'r-1', fullName: 'T', associationId: 'a-1', status: 'ACTIVE' });
    await settle();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('No se encontró la jornada');
    expect([...page.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Volver a intentar')).toBe(true);
  });
});
