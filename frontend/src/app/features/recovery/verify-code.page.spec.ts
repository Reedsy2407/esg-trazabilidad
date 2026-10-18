import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { VerifyPasswordResetRequest, VerifyPasswordResetResponse } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { INVALID_CODE_MESSAGE, RESENT_MESSAGE, SENT_MESSAGE, TOO_MANY_FAILURES_MESSAGE, VERIFY_UNCERTAIN } from './recovery';
import { RecoverySession } from './recovery-session';
import { VerifyCodePage } from './verify-code.page';

describe('VerifyCodePage', () => {
  let el: HTMLElement;
  let verified: VerifyPasswordResetRequest[];
  let requested: string[];
  let verifyAnswer: Subject<VerifyPasswordResetResponse>;
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
  const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());
  const resendButton = () => el.querySelector('.secondary button') as HTMLButtonElement;

  const setup = async (state: object | null = { email: 'ana@asociacion.pe', sentAt: Date.now() }) => {
    verified = [];
    requested = [];
    verifyAnswer = new Subject<VerifyPasswordResetResponse>();
    requestAnswer = new Subject<void>();
    history.replaceState(state, '');
    TestBed.configureTestingModule({
      imports: [VerifyCodePage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            verifyPasswordReset: (request: VerifyPasswordResetRequest): Observable<VerifyPasswordResetResponse> => {
              verified.push(request);
              return verifyAnswer;
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
    const fixture = TestBed.createComponent(VerifyCodePage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  afterEach(() => {
    history.replaceState(null, '');
    vi.useRealTimers();
  });

  it('shows the email from the previous step as fixed text with a link to change it, and a code field made for a one-time code', async () => {
    await setup();
    expect(el.querySelector('[data-testid="fixed-email"]')?.textContent?.trim()).toBe('ana@asociacion.pe');
    expect(el.querySelector('input[type="email"]')).toBeNull(); // not editable here
    expect(el.querySelector('.change-email a')?.textContent?.trim()).toBe('¿No es tu correo? Cambiar');
    expect(el.querySelector('.change-email a')?.getAttribute('href')).toBe('/olvide-contrasena');
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe(SENT_MESSAGE);
    expect(input('code').inputMode).toBe('numeric');
    expect(input('code').autocomplete).toBe('one-time-code');
    expect(el.querySelector('#next')).toBeNull(); // the password is asked for after the code is verified
  });

  it('without an email from the previous step it goes back to step 1', async () => {
    await setup(null);
    expect(navigate).toHaveBeenCalledWith(['/olvide-contrasena'], { replaceUrl: true });
  });

  it('says what is wrong before sending anything', async () => {
    await setup();
    await submit();
    expect(errors()).toEqual(['Escribe el código de 8 dígitos que te enviamos.']);
    expect(input('code').getAttribute('aria-invalid')).toBe('true');

    type('code', '1234567');
    await submit();
    expect(errors()).toEqual(['Escribe el código de 8 dígitos que te enviamos.']);
    expect(verified).toEqual([]);
  });

  it('sends only the digits, keeps the token in memory, empties the form and goes to the new-password step', async () => {
    await setup();
    type('code', '0427 1983');
    await submit();
    expect(verified).toEqual([{ email: 'ana@asociacion.pe', code: '04271983' }]);

    verifyAnswer.next({ resetToken: 'id.secret' });
    verifyAnswer.complete();
    await settle();
    expect(TestBed.inject(RecoverySession).current()).toBe('id.secret');
    expect(navigate).toHaveBeenCalledWith(['/nueva-contrasena'], { replaceUrl: true, state: { email: 'ana@asociacion.pe' } });
    expect(input('code').value).toBe('');
    // The token is nowhere a page could leave it: not in the DOM, not in the history state, not in storage.
    expect(el.innerHTML).not.toContain('id.secret');
    expect(JSON.stringify(history.state)).not.toContain('id.secret');
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('id.secret');
  });

  it('a code that does not work says one thing, under the code field, and editing withdraws it', async () => {
    await setup();
    type('code', '04271983');
    await submit();
    verifyAnswer.error(new ApiError(400, 'AUTH-011', null, null));
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
      verifyAnswer = new Subject<VerifyPasswordResetResponse>();
      type('code', `1111000${attempt}`);
      await submit();
      verifyAnswer.error(new ApiError(400, 'AUTH-011', null, null));
      await settle();
    }
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/olvide-contrasena'], {
      state: { email: 'ana@asociacion.pe', notice: TOO_MANY_FAILURES_MESSAGE },
    });
  });

  it('a limit says how long to wait and a bare 5xx says the outcome is unknown', async () => {
    await setup();
    type('code', '04271983');
    await submit();
    verifyAnswer.error(new ApiError(429, 'AUTH-012', null, 600));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Demasiados intentos. Prueba de nuevo en 10 minutos.');

    verifyAnswer = new Subject<VerifyPasswordResetResponse>();
    type('code', '04271984');
    await submit();
    verifyAnswer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(VERIFY_UNCERTAIN);
  });

  it('arriving with a notice (the token expired) shows it and lets the person ask for a new code at once', async () => {
    await setup({ email: 'ana@asociacion.pe', notice: 'La verificación venció.' });
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe('La verificación venció.');
    expect(resendButton().disabled).toBe(false);
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
