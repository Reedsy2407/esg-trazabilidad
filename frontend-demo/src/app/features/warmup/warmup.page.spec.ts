import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorInterceptor, jwtInterceptor } from '../../core/http/interceptors';
import { UNRESPONSIVE_AFTER_MS, WarmupPage } from './warmup.page';

const liveness = (base: string) => `${base}/actuator/health/liveness`;
const { auth, recycler, collection, reporting } = environment.services;

describe('WarmupPage', () => {
  let fixture: ComponentFixture<WarmupPage>;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;
  let page: HTMLElement;

  const answer = (base: string) =>
    backend
      .match(liveness(base))
      .filter((request) => !request.cancelled) // earlier attempts were cancelled by their timeout
      .forEach((request) => request.flush({ status: 'UP' }));
  const visible = () => !page.querySelector('main')?.classList.contains('hidden');

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [WarmupPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(WarmupPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('stays hidden and goes straight to the login when all four answer within 1.5 s', () => {
    [auth, recycler, collection, reporting].forEach(answer);
    vi.advanceTimersByTime(1500);
    fixture.detectChanges();

    expect(visible()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('shows the wait, offers Retry only after 180 s without an answer, and recovers on retry', () => {
    [auth, recycler, collection].forEach(answer);
    vi.advanceTimersByTime(1500);
    fixture.detectChanges();
    expect(visible()).toBe(true);
    expect(page.textContent).toContain('Despertando');
    expect(page.querySelector('button')).toBeNull();
    // A ready service shows only its state, never a frozen 00:00.
    const readyTimes = Array.from(page.querySelectorAll('tr[data-status="ready"] .time')).map((td) => td.textContent?.trim());
    expect(readyTimes).toEqual(['', '', '']);

    // reporting-service never answers: every attempt times out and is retried.
    vi.advanceTimersByTime(UNRESPONSIVE_AFTER_MS);
    fixture.detectChanges();
    expect(page.textContent).toContain('Sin respuesta');
    expect(navigate).not.toHaveBeenCalled();

    const retry = page.querySelector<HTMLButtonElement>('button');
    expect(retry?.textContent).toContain('Reintentar');
    retry?.click();
    fixture.detectChanges();
    answer(reporting);
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('treats a network or CORS failure as still waking, not as an error', () => {
    [auth, recycler, collection].forEach(answer);
    backend.expectOne(liveness(reporting)).error(new ProgressEvent('error'), { status: 0 });
    vi.advanceTimersByTime(1500);
    fixture.detectChanges();

    const reportingRow = Array.from(page.querySelectorAll('tbody tr')).find((row) =>
      row.textContent?.includes('reporting-service'),
    );
    expect(reportingRow?.textContent).toContain('Despertando');
    expect(page.querySelector('button')).toBeNull();
  });
});
