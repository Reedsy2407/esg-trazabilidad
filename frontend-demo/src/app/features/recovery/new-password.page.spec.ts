import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ConfirmPasswordResetRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { CONFIRM_UNCERTAIN, EXPIRED_MESSAGE } from './recovery';
import { NewPasswordPage } from './new-password.page';
import { RecoverySession, TOKEN_LIFETIME_MS, recoveryTokenGuard } from './recovery-session';

const GOOD = 'una frase larga que solo yo uso';

describe('NewPasswordPage', () => {
  let el: HTMLElement;
  let confirmed: ConfirmPasswordResetRequest[];
  let answer: Subject<void>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const input = (id: string) => el.querySelector(`#${id}`) as HTMLInputElement;
  const type = (id: string, value: string) => {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
    TestBed.tick();
  };
  const submit = async () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
  };
  const fill = (next = GOOD, confirm = next) => {
    type('next', next);
    type('confirm', confirm);
  };
  const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());

  const setup = async (token: string | null = 'id.secret') => {
    confirmed = [];
    answer = new Subject<void>();
    history.replaceState({ email: 'ana@asociacion.pe' }, '');
    TestBed.configureTestingModule({
      imports: [NewPasswordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            confirmPasswordReset: (request: ConfirmPasswordResetRequest): Observable<void> => {
              confirmed.push(request);
              return answer;
            },
          },
        },
      ],
    });
    if (token !== null) {
      TestBed.inject(RecoverySession).hold(token);
    }
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(NewPasswordPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  afterEach(() => {
    history.replaceState(null, '');
    vi.useRealTimers();
  });

  it('asks only for the new password twice, with a toggle, and no email or code', async () => {
    await setup();
    expect(['next', 'confirm'].map((id) => input(id).type)).toEqual(['password', 'password']);
    expect(input('next').autocomplete).toBe('new-password');
    expect(el.querySelector('#email')).toBeNull();
    expect(el.querySelector('#code')).toBeNull();
    expect(el.querySelector('#next-hint')?.textContent).toContain('Entre 15 y 64 caracteres');

    (el.querySelector('.check input') as HTMLInputElement).click();
    TestBed.tick();
    expect(['next', 'confirm'].map((id) => input(id).type)).toEqual(['text', 'text']);
  });

  it('says what is wrong before sending anything, field by field', async () => {
    await setup();
    await submit();
    expect(errors()).toEqual(['Escribe la contraseña.', 'Repite la contraseña nueva.']);
    expect(input('next').getAttribute('aria-invalid')).toBe('true');

    fill('corta', 'otra');
    await submit();
    expect(errors()).toEqual(['Debe tener al menos 15 caracteres.', 'Las dos contraseñas no coinciden.']);
    expect(confirmed).toEqual([]);
  });

  it('sends the token with the password, then empties the form, drops the token and goes to the login', async () => {
    await setup();
    fill();
    await submit();
    expect(confirmed).toEqual([{ resetToken: 'id.secret', newPassword: GOOD }]);

    answer.next();
    answer.complete();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'clave' }, replaceUrl: true });
    expect(TestBed.inject(RecoverySession).current()).toBeNull();
    expect(input('next').value).toBe('');
    expect(el.textContent).not.toContain(GOOD);
  });

  it('a refused password (AUTH-007) is said under the field and the person can type another', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(400, 'AUTH-007', null, null));
    await settle();
    expect(errors()[0]).toContain('El servicio no acepta esa contraseña');
    expect(input('next').getAttribute('aria-invalid')).toBe('true');
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(RecoverySession).current()).toBe('id.secret'); // not spent, so still usable

    answer = new Subject<void>();
    fill('otra frase larga que solo yo uso');
    await submit();
    expect(confirmed.length).toBe(2);
    expect(confirmed[1]).toEqual({ resetToken: 'id.secret', newPassword: 'otra frase larga que solo yo uso' });
  });

  it('a token the service no longer accepts (AUTH-011) sends the person back to the code step with a clear message', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(400, 'AUTH-011', null, null));
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/restablecer-contrasena'], {
      replaceUrl: true,
      state: { email: 'ana@asociacion.pe', notice: EXPIRED_MESSAGE },
    });
    expect(TestBed.inject(RecoverySession).current()).toBeNull();
  });

  it('a token that expired in the browser goes back without calling the service', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-17T15:00:00Z'));
    await setup();
    vi.setSystemTime(Date.now() + TOKEN_LIFETIME_MS + 1);
    fill();
    await submit();
    expect(confirmed).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/restablecer-contrasena'], expect.objectContaining({ replaceUrl: true }));
  });

  it('a limit says how long to wait and a bare 5xx says the outcome is unknown', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(429, 'AUTH-012', null, 600));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Demasiados intentos. Prueba de nuevo en 10 minutos.');

    answer = new Subject<void>();
    type('confirm', GOOD);
    await submit();
    answer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(CONFIRM_UNCERTAIN);
  });
});

describe('recoveryTokenGuard', () => {
  it('lets the step through only while a token is held, and otherwise sends the person to ask for a code', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const run = () => TestBed.runInInjectionContext(() => recoveryTokenGuard({} as never, {} as never));

    expect(String(run())).toBe('/olvide-contrasena');
    TestBed.inject(RecoverySession).hold('id.secret');
    expect(run()).toBe(true);
    TestBed.inject(RecoverySession).clear();
    expect(String(run())).toBe('/olvide-contrasena');
  });
});
