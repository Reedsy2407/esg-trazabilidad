import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { ForgotPasswordPage } from './forgot-password.page';
import { REQUEST_UNCERTAIN } from './recovery';

describe('ForgotPasswordPage', () => {
  let el: HTMLElement;
  let sent: string[];
  let answer: Subject<void>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const input = () => el.querySelector('#email') as HTMLInputElement;
  const type = (value: string) => {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    TestBed.tick();
  };
  const submit = async () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
  };

  const setup = async () => {
    sent = [];
    answer = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [ForgotPasswordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            requestPasswordReset: (email: string): Observable<void> => {
              sent.push(email);
              return answer;
            },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  afterEach(() => history.replaceState(null, ''));

  it('asks only for the email, labelled, and moves focus to the title of the step', async () => {
    await setup();
    expect(el.querySelector('label[for="email"]')?.textContent).toBe('Correo electrónico');
    expect(input().autocomplete).toBe('username');
    // Focusable by code (not in the tab order): the page moves focus to it when the step opens.
    expect(el.querySelector('h1')?.getAttribute('tabindex')).toBe('-1');
    expect(el.querySelector('a[href="/login"]')?.textContent).toBe('Volver a iniciar sesión');
  });

  it('says what is wrong with the email before sending anything', async () => {
    await setup();
    await submit();
    expect(el.querySelector('.field-error')?.textContent?.trim()).toBe('Escribe tu correo electrónico.');
    expect(input().getAttribute('aria-invalid')).toBe('true');

    type('ana');
    await submit();
    expect(el.querySelector('.field-error')?.textContent?.trim()).toContain('Escribe un correo válido');
    expect(sent).toEqual([]);
  });

  it('sends the trimmed email and goes to step 2 carrying only the email and when it asked', async () => {
    await setup();
    type('  ana@asociacion.pe ');
    await submit();
    expect(sent).toEqual(['ana@asociacion.pe']);
    expect(el.querySelector('button[type=submit]')?.textContent?.trim()).toBe('Enviando…');

    answer.next();
    answer.complete();
    await settle();
    expect(navigate).toHaveBeenCalledTimes(1);
    const [commands, extras] = navigate.mock.calls[0] as [string[], { state: { email: string; sentAt: number } }];
    expect(commands).toEqual(['/restablecer-contrasena']);
    expect(extras.state.email).toBe('ana@asociacion.pe');
    expect(extras.state.sentAt).toBeGreaterThan(0);
    expect(Object.keys(extras.state).sort()).toEqual(['email', 'sentAt']);
  });

  it('a limit on this address says how many minutes to wait, in an alert', async () => {
    await setup();
    type('ana@asociacion.pe');
    await submit();
    answer.error(new ApiError(429, 'AUTH-012', null, 1800));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Demasiados intentos. Prueba de nuevo en 30 minutos.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('a bare 5xx or no answer never claims the code was not sent', async () => {
    await setup();
    type('ana@asociacion.pe');
    await submit();
    answer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(REQUEST_UNCERTAIN);
  });

  it('shows why the person was sent back from step 2, and keeps their email', async () => {
    history.replaceState({ email: 'ana@asociacion.pe', notice: 'Escribiste varios códigos que no sirven. Pide un código nuevo.' }, '');
    await setup();
    expect(el.querySelector('.notice')?.textContent?.trim()).toBe('Escribiste varios códigos que no sirven. Pide un código nuevo.');
    expect(input().value).toBe('ana@asociacion.pe');
  });
});
