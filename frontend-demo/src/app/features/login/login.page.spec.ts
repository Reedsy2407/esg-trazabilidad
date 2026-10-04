import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SessionService } from '../../core/auth/session.service';
import { errorInterceptor, jwtInterceptor } from '../../core/http/interceptors';
import { inOneHour, testJwt } from '../../testing/jwt';
import { LoginPage } from './login.page';

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
  });

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
});
