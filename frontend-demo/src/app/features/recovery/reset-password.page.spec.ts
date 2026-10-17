import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ConfirmPasswordResetRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import {
  CONFIRM_UNCERTAIN,
  INVALID_CODE_MESSAGE,
  RESENT_MESSAGE,
  SENT_MESSAGE,
  TOO_MANY_FAILURES_MESSAGE,
} from './recovery';
import { ResetPasswordPage } from './reset-password.page';

const GOOD = 'una frase larga que solo yo uso';

describe('ResetPasswordPage', () => {
  let el: HTMLElement;
  let confirmed: ConfirmPasswordResetRequest[];
  let requested: string[];
  let confirmAnswer: Subject<void>;
  let requestAnswer: Subject<void>;
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
  const fill = (code = '0427 1983', next = GOOD, confirm = next) => {
    type('email', 'ana@asociacion.pe');
    type('code', code);
    type('next', next);
    type('confirm', confirm);
  };
  const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());
  const resendButton = () => el.querySelector('.secondary button') as HTMLButtonElement;

  const setup = async (state: object | null = { email: 'ana@asociacion.pe', sentAt: Date.now() }) => {
    confirmed = [];
    requested = [];
    confirmAnswer = new Subject<void>();
    requestAnswer = new Subject<void>();
    history.replaceState(state, '');
    TestBed.configureTestingModule({
      imports: [ResetPasswordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            confirmPasswordReset: (request: ConfirmPasswordResetRequest): Observable<void> => {
              confirmed.push(request);
              return confirmAnswer;
            },
            requestPasswordReset: (email: string): Observable<void> => {
              requested.push(email);
              return requestAnswer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ResetPasswordPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  afterEach(() => {
    history.replaceState(null, '');
    vi.useRealTimers();
  });

  it('arrives from step 1 with the email filled in, the fixed message, and the code field made for a one-time code', async () => {
    await setup();
    expect(input('email').value).toBe('ana@asociacion.pe');
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe(SENT_MESSAGE);
    expect(input('code').inputMode).toBe('numeric');
    expect(input('code').autocomplete).toBe('one-time-code');
    expect(['next', 'confirm'].map((id) => input(id).type)).toEqual(['password', 'password']);
    expect(input('next').autocomplete).toBe('new-password');
    expect(el.querySelector('#next-hint')?.textContent).toContain('Entre 15 y 64 caracteres');
  });

  it('without a previous step the email is empty and no message claims a code was sent', async () => {
    await setup(null);
    expect(input('email').value).toBe('');
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe('');
    expect(el.querySelector('.notice')?.classList.contains('visually-hidden')).toBe(true);
    expect(resendButton().disabled).toBe(false); // nothing to wait for
  });

  it('says what is wrong before sending anything, field by field', async () => {
    await setup(null);
    await submit();
    expect(errors()).toEqual([
      'Escribe tu correo electrónico.',
      'Escribe el código de 8 dígitos que te enviamos.',
      'Escribe la contraseña.',
      'Repite la contraseña nueva.',
    ]);
    expect(input('email').getAttribute('aria-invalid')).toBe('true');

    fill('1234567', 'corta', 'otra');
    await submit();
    expect(errors()).toEqual([
      'Escribe el código de 8 dígitos que te enviamos.',
      'Debe tener al menos 15 caracteres.',
      'Las dos contraseñas no coinciden.',
    ]);
    expect(confirmed).toEqual([]);
  });

  it('sends only the digits of the code, then empties the form and goes to the login with the notice flag', async () => {
    await setup();
    fill('0427 1983');
    await submit();
    expect(confirmed).toEqual([{ email: 'ana@asociacion.pe', code: '04271983', newPassword: GOOD }]);

    confirmAnswer.next();
    confirmAnswer.complete();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'clave' }, replaceUrl: true });
    expect(input('next').value).toBe('');
    expect(el.textContent).not.toContain(GOOD);
  });

  it('a code that does not work says one thing, under the code field, and editing withdraws it', async () => {
    await setup();
    fill();
    await submit();
    confirmAnswer.error(new ApiError(400, 'AUTH-011', null, null));
    await settle();
    expect(errors()).toEqual([INVALID_CODE_MESSAGE]);
    expect(input('code').getAttribute('aria-invalid')).toBe('true');
    expect(navigate).not.toHaveBeenCalled();

    type('code', '11112222');
    expect(errors()).toEqual([]);
  });

  it('after five codes that do not work the person is sent back to ask for a new one', async () => {
    await setup();
    for (let attempt = 1; attempt <= 5; attempt++) {
      confirmAnswer = new Subject<void>();
      fill(`1111000${attempt}`);
      await submit();
      confirmAnswer.error(new ApiError(400, 'AUTH-011', null, null));
      await settle();
    }
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/olvide-contrasena'], {
      state: { email: 'ana@asociacion.pe', notice: TOO_MANY_FAILURES_MESSAGE },
    });
  });

  it('a password the service refuses (AUTH-007) is said under the new field; a limit says how long to wait', async () => {
    await setup();
    fill();
    await submit();
    confirmAnswer.error(new ApiError(400, 'AUTH-007', null, null));
    await settle();
    expect(errors()[0]).toContain('El servicio no acepta esa contraseña');
    expect(input('next').getAttribute('aria-invalid')).toBe('true');

    confirmAnswer = new Subject<void>();
    type('confirm', GOOD);
    await submit();
    confirmAnswer.error(new ApiError(429, 'AUTH-012', null, 600));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Demasiados intentos. Prueba de nuevo en 10 minutos.');
  });

  it('a bare 5xx says the outcome is unknown instead of claiming the password did not change', async () => {
    await setup();
    fill();
    await submit();
    confirmAnswer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(CONFIRM_UNCERTAIN);
  });

  it('the resend button waits 60 seconds, announces only the end, and sends another code after it', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-16T15:00:00Z'));
    await setup({ email: 'ana@asociacion.pe', sentAt: Date.now() });
    expect(resendButton().disabled).toBe(true);
    expect(el.querySelector('.wait')?.textContent).toContain('60 s');
    expect(el.querySelector('.secondary [aria-live]')?.textContent?.trim()).toBe('');

    vi.advanceTimersByTime(59_000);
    TestBed.tick();
    expect(el.querySelector('.wait')?.textContent).toContain('1 s');
    expect(el.querySelector('.secondary [aria-live]')?.textContent?.trim()).toBe('');

    vi.advanceTimersByTime(1_000);
    TestBed.tick();
    expect(resendButton().disabled).toBe(false);
    expect(el.querySelector('.wait')).toBeNull();
    expect(el.querySelector('.secondary [aria-live]')?.textContent?.trim()).toBe('Ya puedes pedir un código nuevo.');

    type('code', '12345678');
    resendButton().click();
    await settle();
    expect(requested).toEqual(['ana@asociacion.pe']);
    requestAnswer.next();
    requestAnswer.complete();
    await settle();
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe(RESENT_MESSAGE);
    expect(input('code').value).toBe(''); // the old code is no longer good
    expect(resendButton().disabled).toBe(true);
  });

  it('a reload resumes the countdown from when the code was asked for', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-16T15:00:00Z'));
    await setup({ email: 'ana@asociacion.pe', sentAt: Date.now() - 45_000 });
    expect(el.querySelector('.wait')?.textContent).toContain('15 s');
  });
});
