import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SessionService } from '../../core/auth/session.service';
import { errorInterceptor, jwtInterceptor } from '../../core/http/interceptors';
import { inOneHour, testJwt } from '../../testing/jwt';
import { LoginPage, WAKE_NOTICE_AFTER_MS } from './login.page';

const LOGIN_URL = `${environment.services.auth}/auth/login`;

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let backend: HttpTestingController;
  let router: Router;
  let page: HTMLElement;

  const type = (selector: string, value: string) => {
    const input = page.querySelector<HTMLInputElement>(selector);
    if (input === null) {
      throw new Error(`no ${selector}`);
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = () => {
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  const text = () => page.textContent ?? '';
  const submitButton = () => page.querySelector<HTMLButtonElement>('button[type=submit]');

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    answerLiveness();
  });

  /** The page wakes the four services in the background; the tests answer those pings. */
  const answerLiveness = () => {
    for (const request of backend.match((r) => r.url.endsWith('/actuator/health/liveness'))) {
      request.flush({ status: 'UP' });
    }
  };

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('does not call the API with an empty form, and says what is missing next to each field', () => {
    submit();

    backend.expectNone(LOGIN_URL);
    expect(text()).toContain('Escribe tu correo electrónico.');
    expect(text()).toContain('Escribe tu contraseña.');
    expect(page.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('on success, starts the session from the token and opens the companies', () => {
    type('#email', 'ana.paredes@asociacion.pe');
    type('#password', 'correcta');
    submit();

    const request = backend.expectOne(LOGIN_URL);
    expect(request.request.body).toEqual({ email: 'ana.paredes@asociacion.pe', password: 'correcta' });
    request.flush({ accessToken: testJwt({ email: 'ana.paredes@asociacion.pe', exp: inOneHour() }) });

    expect(TestBed.inject(SessionService).email()).toBe('ana.paredes@asociacion.pe');
    expect(router.navigate).toHaveBeenCalledWith(['/empresas']);
  });

  it('on AUTH-001, shows the credentials message in the form and stays on the login', () => {
    type('#email', 'ana.paredes@asociacion.pe');
    type('#password', 'incorrecta');
    submit();

    backend
      .expectOne(LOGIN_URL)
      .flush({ status: 401, code: 'AUTH-001', detail: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('El correo o la contraseña no son correctos.');
    expect(router.navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(SessionService).token()).toBeNull();
  });

  it('after AUTH-001 only the credentials message shows, the password field takes the focus, and the next submit validates as usual', () => {
    vi.useFakeTimers();
    type('#email', 'ana.paredes@asociacion.pe');
    type('#password', 'incorrecta');
    submit();
    backend
      .expectOne(LOGIN_URL)
      .flush({ status: 401, code: 'AUTH-001', detail: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    vi.runAllTimers();

    expect(text()).toContain('El correo o la contraseña no son correctos.');
    expect(text()).not.toContain('Escribe tu contraseña.');
    expect(page.querySelector('#password')?.getAttribute('aria-invalid')).toBe('false');
    expect((page.querySelector('#password') as HTMLInputElement).value).toBe('');
    expect(document.activeElement).toBe(page.querySelector('#password'));

    // Submitting again with the field still empty: the field error is back, and no request goes out.
    submit();
    backend.expectNone(LOGIN_URL);
    expect(text()).toContain('Escribe tu contraseña.');
  });

  it('on AUTH-004, announces the lock once, counts Retry-After down, then lets the person try again', () => {
    vi.useFakeTimers();
    type('#email', 'ana.paredes@asociacion.pe');
    type('#password', 'incorrecta');
    submit();

    backend
      .expectOne(LOGIN_URL)
      .flush({ status: 429, code: 'AUTH-004' }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '8' } });
    fixture.detectChanges();

    const live = page.querySelector('[aria-live=polite]');
    expect(live?.textContent?.trim()).toBe('Demasiados intentos. Podrás volver a intentarlo en 8 segundos.');
    expect(submitButton()?.disabled).toBe(true);
    expect(text()).toContain('8 s');

    vi.advanceTimersByTime(3000);
    fixture.detectChanges();
    // The countdown moves, the announced sentence does not change.
    expect(text()).toContain('5 s');
    expect(live?.textContent?.trim()).toBe('Demasiados intentos. Podrás volver a intentarlo en 8 segundos.');

    vi.advanceTimersByTime(5000);
    fixture.detectChanges();
    expect(submitButton()?.disabled).toBe(false);
    expect(live?.textContent?.trim()).toBe('');
  });

  it('says plainly when the auth service cannot be reached', () => {
    type('#email', 'ana.paredes@asociacion.pe');
    type('#password', 'x');
    submit();

    backend.expectOne(LOGIN_URL).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('No se pudo conectar con el servicio de autenticación.');
  });

  it('says the auth service is unavailable, without a status code, when it answers 503 or a bare 502/504', () => {
    for (const status of [503, 502, 504]) {
      type('#email', 'ana.paredes@asociacion.pe');
      type('#password', 'x');
      submit();

      backend.expectOne(LOGIN_URL).flush(null, { status, statusText: 'Service Unavailable' });
      fixture.detectChanges();

      const text = page.querySelector('[role=alert]')?.textContent ?? '';
      expect(text).toContain('El servicio de autenticación no está disponible en este momento');
      expect(text).not.toMatch(/HTTP|\d{3}/);
    }
  });
});

describe('LoginPage waking the services', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let fixture: ComponentFixture<LoginPage>;

  const pings = () => backend.match((r) => r.url.endsWith('/actuator/health/liveness'));
  const notice = () => page.querySelector('.wake') as HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('pings the four services at once, without blocking the form, and says nothing if they answer fast', () => {
    const requests = pings();
    expect(requests.map((r) => r.request.url).sort()).toEqual(
      Object.values(environment.services).map((base) => `${base}/actuator/health/liveness`).sort(),
    );
    vi.advanceTimersByTime(WAKE_NOTICE_AFTER_MS - 100);
    requests.forEach((r) => r.flush({ status: 'UP' }));
    vi.advanceTimersByTime(WAKE_NOTICE_AFTER_MS);
    fixture.detectChanges();
    expect(notice().textContent?.trim()).toBe('');
    expect((page.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('says "Despertando servicios" once it is slow, and takes it back when the last one answers', () => {
    const requests = pings();
    requests.slice(0, 3).forEach((r) => r.flush({ status: 'UP' }));
    vi.advanceTimersByTime(WAKE_NOTICE_AFTER_MS + 10);
    fixture.detectChanges();
    expect(notice().textContent).toContain('Despertando servicios');
    expect(notice().classList.contains('visually-hidden')).toBe(false);
    expect((page.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(false); // never blocks

    requests[3].flush({ status: 'UP' });
    fixture.detectChanges();
    expect(notice().textContent?.trim()).toBe('');
    expect(notice().classList.contains('visually-hidden')).toBe(true);
  });

  it('a service that fails is retried, not shown as an error', () => {
    const [first, ...rest] = pings();
    rest.forEach((r) => r.flush({ status: 'UP' }));
    first.flush(null, { status: 502, statusText: 'Bad Gateway' });
    vi.advanceTimersByTime(3_100);
    expect(pings()).toHaveLength(1); // the retry
    expect(page.querySelector('[role=alert]')).toBeNull();
  });
});
