import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ChangePasswordRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { inOneHour, testJwt } from '../../testing/jwt';
import { CHANGE_UNCERTAIN, ChangePasswordPage } from './change-password.page';

const GOOD = 'una frase larga que solo yo uso';

describe('ChangePasswordPage', () => {
  let el: HTMLElement;
  let sent: ChangePasswordRequest[];
  let answer: Subject<void>;

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
  const fill = (current = 'la-clave-que-tengo-ahora', next = GOOD, confirm = next) => {
    type('current', current);
    type('next', next);
    type('confirm', confirm);
  };
  const errors = () => [...el.querySelectorAll('.field-error')].map((e) => e.textContent?.trim());

  const setup = async (pwdChange = false) => {
    sessionStorage.clear();
    sent = [];
    answer = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [ChangePasswordPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            changeOwnPassword: (request: ChangePasswordRequest): Observable<void> => {
              sent.push(request);
              return answer;
            },
          },
        },
      ],
    });
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', exp: inOneHour(), pwdChange }));
    const fixture = TestBed.createComponent(ChangePasswordPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  it('three password fields with the right autocomplete, hidden until asked, and the rules written', async () => {
    await setup();
    expect(['current', 'next', 'confirm'].map((id) => input(id).type)).toEqual(['password', 'password', 'password']);
    expect(input('current').autocomplete).toBe('current-password');
    expect(input('next').autocomplete).toBe('new-password');
    expect(el.querySelector('#next-hint')?.textContent).toContain('Entre 15 y 64 caracteres');

    const show = el.querySelector('.check input') as HTMLInputElement;
    show.checked = true;
    show.dispatchEvent(new Event('change'));
    TestBed.tick();
    expect(['current', 'next', 'confirm'].map((id) => input(id).type)).toEqual(['text', 'text', 'text']);
  });

  it('says what is wrong before sending anything', async () => {
    await setup();
    await submit();
    expect(errors()).toEqual(['Escribe tu contraseña actual.', 'Escribe la contraseña.', 'Repite la contraseña nueva.']);
    expect(input('current').getAttribute('aria-invalid')).toBe('true');
    expect(input('next').getAttribute('aria-describedby')).toBe('next-hint next-error');

    fill('la-clave-que-tengo-ahora', 'corta', 'otra');
    await submit();
    expect(errors()).toEqual(['Debe tener al menos 15 caracteres.', 'Las dos contraseñas no coinciden.']);

    fill('la-clave-que-tengo-ahora', 'la-clave-que-tengo-ahora', 'la-clave-que-tengo-ahora');
    await submit();
    expect(errors()).toEqual(['La nueva contraseña debe ser distinta de la actual.']);
    expect(sent).toEqual([]);
  });

  it('sends the current and the new password, then empties the form and stays signed in', async () => {
    await setup();
    fill();
    await submit();
    expect(sent).toEqual([{ currentPassword: 'la-clave-que-tengo-ahora', newPassword: GOOD }]);

    answer.next();
    answer.complete();
    await settle();
    expect(el.querySelector('#done-title')?.textContent).toBe('Contraseña cambiada');
    expect(el.querySelector('[role="status"]')?.textContent).toBe('Contraseña cambiada.');
    expect(TestBed.inject(SessionService).isAuthenticated()).toBe(true);
    expect(el.textContent).not.toContain(GOOD);
  });

  it('a wrong current password (AUTH-008) is said under that field, not as a session problem', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(400, 'AUTH-008', null, null));
    await settle();
    expect(errors()).toEqual(['La contraseña actual no es correcta.']);
    expect(input('current').getAttribute('aria-invalid')).toBe('true');
    expect(TestBed.inject(SessionService).isAuthenticated()).toBe(true);
  });

  it('a password the service refuses (AUTH-007) is said under the new field', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(400, 'AUTH-007', null, null));
    await settle();
    expect(errors()[0]).toContain('El servicio no acepta esa contraseña');
    expect(input('next').getAttribute('aria-invalid')).toBe('true');
  });

  it('editing after a refusal withdraws it; a bare 5xx says the outcome is unknown; a 429 says when to retry', async () => {
    await setup();
    fill();
    await submit();
    answer.error(new ApiError(400, 'AUTH-008', null, null));
    await settle();
    type('current', 'otra-clave-que-tengo-ya');
    expect(errors()).toEqual([]);

    answer = new Subject<void>();
    await submit();
    answer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe(CHANGE_UNCERTAIN);

    answer = new Subject<void>();
    await submit();
    answer.error(new ApiError(429, 'AUTH-004', null, 30));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Demasiadas solicitudes. Vuelve a intentarlo en 30 s.');
  });

  it('a forced change ends the session after success and goes to the login with the notice flag', async () => {
    await setup(true);
    expect(el.querySelector('.back-link')).toBeNull(); // nowhere else to go
    expect(el.querySelector('.sub')?.textContent).toContain('Tu contraseña la eligió otra persona');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fill();
    await submit();
    answer.next();
    answer.complete();
    await settle();
    expect(TestBed.inject(SessionService).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'clave' } });
  });
});
