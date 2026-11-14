import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/interceptors';
import { RecyclerSchedulesPage } from './recycler-schedules.page';

const { collection, recycler } = environment.services;
const RECYCLER_URL = `${recycler}/associations/a-1/recyclers/r-1`;
const ASSIGNMENT_URL = `${collection}/recyclers/r-1/assignment`;
const ASSIGNABLE_URL = `${collection}/schedules/assignable`;

const RECYCLER = { id: 'r-1', fullName: 'Rosa Quispe Huamán', associationId: 'a-1', status: 'ACTIVE' };
const MINE = {
  scheduleId: 's-mine',
  dayOfWeek: 'WEDNESDAY',
  time: '09:00:00',
  scheduleStatus: 'ACTIVE',
  neighborId: 'n-0',
  neighborName: 'Carmen Rojas',
  address: 'Jr. Cusco 10',
  district: 'Lima',
  neighborStatus: 'ACTIVE',
};
const MONDAY = { ...MINE, scheduleId: 's-mon', dayOfWeek: 'MONDAY', neighborName: 'Zoila Vega' };

const row = (id: string, name: string, assignedRecyclerId: string | null) => ({
  scheduleId: id,
  dayOfWeek: 'TUESDAY',
  time: '08:30:00',
  neighbor: { id: `n-${id}`, fullName: name, address: 'Av. Perú 455', district: 'Lima' },
  assignedRecyclerId,
});
const assignablePage = (content: object[]) => ({
  content,
  page: 0,
  size: 50,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
});

describe('RecyclerSchedulesPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let fixture: ComponentFixture<RecyclerSchedulesPage>;

  const settle = async () => {
    await Promise.resolve();
    TestBed.tick();
  };
  const flush = async (req: TestRequest, body: object) => {
    req.flush(body);
    await settle();
  };
  const buttonByText = (text: string) =>
    [...page.querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(text)) as HTMLButtonElement;
  const chooseDistrict = async () => {
    (fixture.componentInstance as unknown as { districtControl: { setValue(v: string): void } }).districtControl.setValue('150101');
    TestBed.tick();
    await flush(backend.expectOne((r) => r.url === ASSIGNABLE_URL), assignablePage([
      row('s-free', 'Ana Flores', null),
      row('s-other', 'Beto Díaz', 'r-9'),
      row('s-own', 'Carmen Rojas', 'r-1'),
    ]));
  };
  const tick = async (label: string) => {
    const box = [...page.querySelectorAll<HTMLInputElement>('input[type=checkbox]')].find((i) =>
      i.closest('label')?.textContent?.includes(label),
    )!;
    box.click();
    await settle();
  };

  async function create(status = 'ACTIVE') {
    TestBed.configureTestingModule({
      imports: [RecyclerSchedulesPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecyclerSchedulesPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flush(backend.expectOne(RECYCLER_URL), { ...RECYCLER, status });
    await flush(backend.expectOne(ASSIGNMENT_URL), { recyclerId: 'r-1', schedules: [MINE, MONDAY] });
  }

  afterEach(() => backend.verify());

  it('lists what the recycler has, Monday first', async () => {
    await create();
    expect(page.querySelector('h1')?.textContent).toContain('Rosa Quispe Huamán');
    const names = [...page.querySelectorAll('table tbody td.type')].map((td) => td.textContent?.trim());
    expect(names).toEqual(['Zoila Vega', 'Carmen Rojas']);
    expect(page.textContent).toContain('2');
  });

  it('shows the district\'s schedules as free, of another recycler, or already this recycler\'s', async () => {
    await create();
    await chooseDistrict();
    const text = page.querySelector('.pick-list')!.textContent!;
    expect(text).toContain('Libre');
    expect(text).toContain('De otro reciclador');
    expect(text).toContain('Ya es de este reciclador');
    const own = [...page.querySelectorAll<HTMLInputElement>('input[type=checkbox]')].find((i) => i.closest('label')?.textContent?.includes('Carmen'))!;
    expect(own.disabled).toBe(true);
    expect(own.checked).toBe(true);
  });

  it('assigning free schedules keeps the current list, sends it whole, and reports the service\'s counts', async () => {
    await create();
    await chooseDistrict();
    await tick('Ana Flores');
    buttonByText('Asignar 1 marcado').click();
    TestBed.tick();
    const put = backend.expectOne(ASSIGNMENT_URL);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ scheduleIds: ['s-mine', 's-mon', 's-free'] });
    put.flush({ recyclerId: 'r-1', scheduleIds: ['s-mine', 's-mon', 's-free'], moved: [] });
    await settle();
    backend.expectOne(ASSIGNMENT_URL).flush({ recyclerId: 'r-1', schedules: [MINE, MONDAY] });
    backend.expectOne((r) => r.url === ASSIGNABLE_URL).flush(assignablePage([]));
    await settle();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Asignación guardada');
    expect(page.querySelector('[role=status]')?.textContent).toContain('Se movieron 0');
    expect(page.querySelector('[role=status]')?.textContent).toContain('Ahora tiene 3 en total');
  });

  it('moving a schedule from another recycler asks first, and "No, volver" sends nothing', async () => {
    await create();
    await chooseDistrict();
    await tick('Beto Díaz');
    buttonByText('Asignar 1 marcado').click();
    TestBed.tick();
    backend.expectNone(ASSIGNMENT_URL);
    const confirm = page.querySelector('[data-confirm]')!;
    expect(confirm.textContent).toContain('de otro reciclador');
    expect(confirm.textContent).toContain('dejan de estar en la ruta del anterior');

    buttonByText('No, volver').click();
    TestBed.tick();
    backend.expectNone(ASSIGNMENT_URL);
    expect(page.querySelector('[data-confirm]')).toBeNull();
  });

  it('confirming the move sends the PUT and says how many were moved', async () => {
    await create();
    await chooseDistrict();
    await tick('Beto Díaz');
    await tick('Ana Flores');
    buttonByText('Asignar 2 marcados').click();
    TestBed.tick();
    buttonByText('Sí, mover y asignar').click();
    TestBed.tick();
    const put = backend.expectOne(ASSIGNMENT_URL);
    expect(put.request.body).toEqual({ scheduleIds: ['s-mine', 's-mon', 's-other', 's-free'] });
    put.flush({ recyclerId: 'r-1', scheduleIds: ['s-mine', 's-mon', 's-other', 's-free'], moved: [{ scheduleId: 's-other', fromRecyclerId: 'r-9' }] });
    await settle();
    backend.expectOne(ASSIGNMENT_URL).flush({ recyclerId: 'r-1', schedules: [MINE] });
    backend.expectOne((r) => r.url === ASSIGNABLE_URL).flush(assignablePage([]));
    await settle();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Se movieron 1 desde otro reciclador');
  });

  it('assigning the whole district asks, then shows the three counts the service answered', async () => {
    await create();
    await chooseDistrict();
    buttonByText('Asignar todo').click();
    TestBed.tick();
    expect(page.querySelector('[data-confirm]')?.textContent).toContain('se omiten');
    buttonByText('Sí, asignar el distrito').click();
    TestBed.tick();
    const post = backend.expectOne(`${collection}/assignments/bulk`);
    expect(post.request.body).toEqual({ recyclerId: 'r-1', districtUbigeo: '150101' });
    post.flush({ assigned: 4, skippedOtherRecycler: 2, skippedInactive: 1 });
    await settle();
    backend.expectOne(ASSIGNMENT_URL).flush({ recyclerId: 'r-1', schedules: [MINE] });
    backend.expectOne((r) => r.url === ASSIGNABLE_URL).flush(assignablePage([]));
    await settle();
    const figures = [...page.querySelectorAll('.figures dd')].map((d) => d.textContent?.trim());
    expect(figures).toEqual(['4', '2', '1']);
  });

  it('a recycler that is not active cannot be assigned anything', async () => {
    await create('INACTIVE');
    expect(page.textContent).toContain('inactivo');
    await chooseDistrict();
    expect(buttonByText('Asignar todo').disabled).toBe(true);
    expect(buttonByText('Asignar 0').disabled).toBe(true);
    const free = [...page.querySelectorAll<HTMLInputElement>('input[type=checkbox]')].find((i) => i.closest('label')?.textContent?.includes('Ana'))!;
    expect(free.disabled).toBe(true);
  });

  it('a failed write says so with the service\'s reason, never as a success', async () => {
    await create();
    await chooseDistrict();
    await tick('Ana Flores');
    buttonByText('Asignar 1 marcado').click();
    TestBed.tick();
    backend.expectOne(ASSIGNMENT_URL).flush(
      { code: 'COL-019', detail: 'x', status: 403, title: 'x' },
      { status: 403, statusText: 'Forbidden' },
    );
    await settle();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('no está activo');
    expect(page.querySelector('[role=status]')?.textContent?.trim()).toBe('');
  });
});
