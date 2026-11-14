import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SessionService } from '../../core/auth/session.service';
import { errorInterceptor } from '../../core/http/interceptors';
import { inOneHour, testJwt } from '../../testing/jwt';
import { RecyclerWorkdaysPage } from './recycler-workdays.page';

const { collection, recycler } = environment.services;
const RECYCLER_URL = `${recycler}/associations/a-1/recyclers/r-1`;
const WORKDAYS_URL = `${collection}/recyclers/r-1/workdays`;
const RETENTION_URL = `${collection}/retention/run`;

const workday = (id: string, startedAt: string, extra: object = {}) => ({
  workdayId: id,
  startedAt,
  endedAt: '2026-11-13T17:30:00Z',
  status: 'CLOSED',
  withGps: true,
  checkinCount: 4,
  totalKg: 18.45,
  pointCount: 40,
  ...extra,
});
const pageOf = (content: object[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });

describe('RecyclerWorkdaysPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let fixture: ComponentFixture<RecyclerWorkdaysPage>;

  const settle = async () => {
    await Promise.resolve();
    TestBed.tick();
  };
  const flush = async (req: TestRequest, body: object) => {
    req.flush(body);
    await settle();
  };
  const buttonByText = (text: string) =>
    [...page.querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(text)) as HTMLButtonElement | undefined;

  async function create(role: 'ADMIN' | 'STAFF', rows: object[]) {
    TestBed.configureTestingModule({
      imports: [RecyclerWorkdaysPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: inOneHour(), role }));
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecyclerWorkdaysPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne(RECYCLER_URL), { id: 'r-1', fullName: 'Teodora Condori Apaza', associationId: 'a-1', status: 'ACTIVE' });
    await flush(backend.expectOne((r) => r.url === WORKDAYS_URL), pageOf(rows));
  }

  afterEach(() => backend.verify());

  it('lists the workdays with the Lima date, the hours, the check-ins, the weight and the route', async () => {
    await create('STAFF', [workday('w-1', '2026-11-13T13:05:00Z'), workday('w-2', '2026-11-14T12:50:00Z', { endedAt: null, status: 'OPEN', withGps: false, checkinCount: 0, totalKg: 0, pointCount: 0 })]);
    expect(page.querySelector('h1')?.textContent).toContain('Teodora Condori Apaza');
    const rows = [...page.querySelectorAll('table tbody tr')].map((r) => r.textContent!.replace(/\s+/g, ' ').trim());
    expect(rows[0]).toContain('13/11/2026');
    expect(rows[0]).toContain('08:05–12:30');
    expect(rows[0]).toContain('18.45 kg');
    expect(rows[0]).toContain('40 puntos');
    expect(rows[1]).toContain('en curso');
    expect(rows[1]).toContain('Sin ubicación');
  });

  it('filters by Lima calendar days, sent to the service as UTC instants', async () => {
    await create('STAFF', [workday('w-1', '2026-11-13T13:05:00Z')]);
    const from = page.querySelector<HTMLInputElement>('#from')!;
    from.value = '2026-11-13';
    from.dispatchEvent(new Event('change'));
    await settle();
    const request = backend.expectOne((r) => r.url === WORKDAYS_URL);
    expect(request.request.params.get('from')).toBe('2026-11-13T05:00:00.000Z');
    expect(request.request.params.get('to')).toBeNull();
    request.flush(pageOf([]));
    await settle();
    expect(page.textContent).toContain('Ninguna jornada empezó en esas fechas');
    expect(buttonByText('Quitar filtro')).toBeDefined();
  });

  it('says so when the recycler has no workdays yet', async () => {
    await create('STAFF', []);
    expect(page.textContent).toContain('todavía no ha abierto ninguna jornada');
  });

  it('only an ADMIN sees the retention', async () => {
    await create('STAFF', []);
    expect(buttonByText('Ejecutar retención')).toBeUndefined();
    expect(page.textContent).not.toContain('Retención de datos');
  });

  it('the retention is confirmed first, with the safe way out as the filled button, and shows the counts the service answered', async () => {
    await create('ADMIN', []);
    buttonByText('Ejecutar retención')!.click();
    TestBed.tick();
    backend.expectNone(RETENTION_URL);
    expect(buttonByText('No, volver')!.classList).toContain('btn-primary');
    expect(buttonByText('Sí, ejecutar la retención')!.classList).not.toContain('btn-primary');

    buttonByText('No, volver')!.click();
    TestBed.tick();
    backend.expectNone(RETENTION_URL);
    expect(page.querySelector('[data-confirm]')).toBeNull();

    buttonByText('Ejecutar retención')!.click();
    TestBed.tick();
    buttonByText('Sí, ejecutar la retención')!.click();
    TestBed.tick();
    const run = backend.expectOne(RETENTION_URL);
    expect(run.request.method).toBe('POST');
    run.flush({ gpsDays: 90, photoMonths: 12, workdaysSummarized: 3, pointsDeleted: 412, photosDeleted: 7, photosFailed: 1 });
    await settle();
    backend.expectOne((r) => r.url === WORKDAYS_URL).flush(pageOf([]));
    await settle();
    const figures = [...page.querySelectorAll('#retention-result dd')].map((d) => d.textContent?.trim());
    expect(figures).toEqual(['3', '412', '7', '1']);
    expect(page.querySelector('#retention-result')?.textContent).toContain('90 días');
    expect(page.querySelector('#retention-result')?.textContent).toContain('se reintentan');
  });

  it('a retention whose outcome is unknown says it is safe to run again', async () => {
    await create('ADMIN', []);
    buttonByText('Ejecutar retención')!.click();
    TestBed.tick();
    buttonByText('Sí, ejecutar la retención')!.click();
    TestBed.tick();
    backend.expectOne(RETENTION_URL).flush('x', { status: 504, statusText: 'Gateway Timeout' });
    await settle();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('seguro volver a ejecutarla');
    expect(page.querySelector('#retention-result')).toBeNull();
  });
});
