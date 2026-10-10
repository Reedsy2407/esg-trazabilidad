import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { CreateStaffUserRequest, StaffUser } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { StaffNewPage } from './staff-new.page';

describe('StaffNewPage', () => {
  let el: HTMLElement;
  let sent: CreateStaffUserRequest[];
  let answer: Subject<StaffUser>;
  let destroy: () => void;
  let component: StaffNewPage;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const type = (id: string, value: string) => {
    const input = el.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    TestBed.tick();
  };
  const acknowledge = () => {
    const box = el.querySelector('.check input') as HTMLInputElement;
    box.checked = true;
    box.dispatchEvent(new Event('change'));
    TestBed.tick();
  };
  const submit = async () => {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
  };
  const button = (text: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;
  const fill = () => {
    type('fullName', ' Luis Ramos ');
    type('email', ' luis.ramos@asociacion.pe ');
    acknowledge();
  };
  /** Every attribute value in the component's DOM, to prove the password is never one. */
  const attributes = () => [...el.querySelectorAll('*')].flatMap((node) => [...node.attributes].map((a) => a.value)).join('\n');

  beforeEach(async () => {
    sent = [];
    answer = new Subject<StaffUser>();
    TestBed.configureTestingModule({
      imports: [StaffNewPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            createStaffUser: (request: CreateStaffUserRequest): Observable<StaffUser> => {
              sent.push(request);
              return answer;
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(StaffNewPage);
    el = fixture.nativeElement as HTMLElement;
    destroy = () => fixture.destroy();
    component = fixture.componentInstance;
    await settle();
  });

  it('has no password field at all: nothing for the browser to offer to save', () => {
    expect(el.querySelector('input[type="password"]')).toBeNull();
    expect(el.querySelector('form')?.getAttribute('autocomplete')).toBe('off');
    expect([...el.querySelectorAll('input:not([type="checkbox"])')].map((i) => i.getAttribute('autocomplete'))).toEqual(['off', 'off']);
  });

  it('needs the confirmation and an email with a dot in its domain before sending anything', async () => {
    type('fullName', 'Luis Ramos');
    type('email', 'luis@asociacion');
    await submit();
    expect(el.querySelector('#email-error')?.textContent).toContain('con un punto en el dominio');
    expect(el.querySelector('#ack-error')?.textContent).toContain('Marca la casilla');
    expect(sent).toEqual([]);
  });

  it('sends a generated 16-character password, shows it only on request, and "Ya la entregué" drops it', async () => {
    fill();
    await submit();
    await submit(); // ignored while sending
    expect(sent).toHaveLength(1);
    const password = sent[0].password;
    expect(sent[0]).toEqual({ email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', password });
    expect(password).toMatch(/^[A-HJ-NP-Za-km-np-z2-9]{16}$/);

    answer.next({ id: 's-2', email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', active: true, createdAt: '2026-10-10T15:00:00Z' });
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toContain('Cuenta creada para luis.ramos@asociacion.pe');
    expect(el.textContent).not.toContain(password); // hidden until "Mostrar"
    button('Mostrar').click();
    TestBed.tick();
    expect(el.querySelector('.secret code')?.textContent).toBe(password);
    expect(attributes()).not.toContain(password);

    button('Ya la entregué').click();
    await settle();
    expect(el.textContent).not.toContain(password);
    expect(el.innerHTML).not.toContain(password);
    expect(el.querySelector('[role="status"]')?.textContent).toBe('La contraseña se borró de esta pantalla.');
    expect((el.querySelector('#fullName') as HTMLInputElement).value).toBe('');
  });

  it('an unknown outcome keeps the password and retries with the SAME one; AUTH-002 then only says an account exists', async () => {
    fill();
    await submit();
    const password = sent[0].password;
    answer.error(new ApiError(504, null, null, null));
    answer = new Subject<StaffUser>();
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toContain('No pudimos confirmar si la cuenta se creó');
    button('Mostrar').click();
    TestBed.tick();
    expect(el.querySelector('.secret code')?.textContent).toBe(password);

    acknowledge();
    button('Volver a intentar con los mismos datos').click();
    await settle();
    expect(sent[1]).toEqual(sent[0]);
    answer.error(new ApiError(409, 'AUTH-002', null, null));
    await settle();
    // An account with that email exists, but maybe not ours: the screen says so, never "it was created".
    expect(el.querySelector('#result-title')?.textContent).toBe('Ya existe una cuenta con el correo luis.ramos@asociacion.pe');
    expect(el.textContent).toContain('compruébalo iniciando sesión con ese correo y esta contraseña en una ventana privada u otro navegador');
    expect(el.textContent).not.toMatch(/sí se creó|la cuenta se creó con esta contraseña/);
    // "Descartar" asks here too.
    button('Descartar').click();
    TestBed.tick();
    expect(el.querySelector('.confirm-discard')).not.toBeNull();
    expect((component as unknown as { password(): string | null }).password()).toBe(password);
    expect(el.querySelector('[role="status"]')?.textContent).toBe('Ya existe una cuenta con el correo luis.ramos@asociacion.pe.');
    expect(el.querySelector('.secret code')?.textContent).toBe(password);
  });

  it('after an unknown outcome, "Descartar" asks first; only "Sí" drops the password', async () => {
    fill();
    await submit();
    const memory = () => (component as unknown as { password(): string | null }).password();
    answer.error(new ApiError(502, null, null, null));
    answer = new Subject<StaffUser>();
    await settle();
    // The uncertain text never promises that an "already exists" answer proves the password.
    expect(el.textContent).toContain('todavía hay que comprobarlo: esa cuenta pudo existir antes');
    button('Descartar').click();
    TestBed.tick();
    expect(el.querySelector('.confirm-discard')?.textContent).toContain('nadie podrá volver a ver su contraseña');
    expect(memory()).toBe(sent[0].password);
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.id).toBe('discard-text');
    button('No, mantenerla').click();
    TestBed.tick();
    expect(el.querySelector('.confirm-discard')).toBeNull();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.textContent?.trim()).toBe('Descartar');
    expect(memory()).toBe(sent[0].password);
    button('Descartar').click();
    TestBed.tick();
    button('Sí, descartar la contraseña').click();
    await settle();
    expect(memory()).toBeNull();
    expect(el.querySelector('#fullName')).not.toBeNull();
  });

  it('an unknown outcome, then a retry: a 201 shows it created; a refusal keeps the password', async () => {
    fill();
    await submit();
    const password = sent[0].password;
    answer.error(new ApiError(0, null, null, null));
    answer = new Subject<StaffUser>();
    await settle();
    acknowledge();
    button('Volver a intentar con los mismos datos').click();
    await settle();
    answer.error(new ApiError(400, 'VALIDATION_ERROR', null, null));
    answer = new Subject<StaffUser>();
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('El servicio rechazó los datos');
    expect((component as unknown as { password(): string | null }).password()).toBe(password);

    acknowledge();
    button('Volver a intentar con los mismos datos').click();
    await settle();
    expect(sent[2].password).toBe(password);
    answer.next({ id: 's-2', email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', active: true, createdAt: '2026-10-10T15:00:00Z' });
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toBe('Cuenta creada para luis.ramos@asociacion.pe');
  });

  it('"Mostrar" says whether it is pressed; hidden, the code reads as a hidden password', async () => {
    fill();
    await submit();
    answer.next({ id: 's-2', email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', active: true, createdAt: '2026-10-10T15:00:00Z' });
    await settle();
    expect(button('Mostrar').getAttribute('aria-pressed')).toBe('false');
    expect(el.querySelector('.secret code')?.getAttribute('aria-label')).toBe('Contraseña oculta');
    button('Mostrar').click();
    TestBed.tick();
    expect(button('Mostrar').getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('.secret code')?.hasAttribute('aria-label')).toBe(false);
  });

  it('a plain AUTH-002 drops the never-used password; a new attempt gets a new one', async () => {
    fill();
    await submit();
    const first = sent[0].password;
    answer.error(new ApiError(409, 'AUTH-002', null, null));
    answer = new Subject<StaffUser>();
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ya existe una cuenta del personal con el correo luis.ramos@asociacion.pe.');
    expect(el.querySelector('.secret')).toBeNull();
    type('email', 'luis.ramos2@asociacion.pe');
    await submit();
    expect(sent[1].password).not.toBe(first);
  });

  it('copied, then dropped or left: the clipboard this screen filled is overwritten', async () => {
    const writes: string[] = [];
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: (text: string) => (writes.push(text), Promise.resolve()) },
    });
    onTestFinished(() => {
      if (original === undefined) {
        delete (navigator as unknown as Record<string, unknown>)['clipboard'];
      } else {
        Object.defineProperty(navigator, 'clipboard', original);
      }
    });
    fill();
    await submit();
    answer.next({ id: 's-2', email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', active: true, createdAt: '2026-10-10T15:00:00Z' });
    await settle();
    button('Copiar').click();
    await settle();
    expect(writes).toEqual([sent[0].password]);
    destroy(); // leaving the page, without "Ya la entregué"
    expect(writes).toEqual([sent[0].password, '']);
  });

  it('leaving the page forgets the password', async () => {
    fill();
    await submit();
    answer.next({ id: 's-2', email: 'luis.ramos@asociacion.pe', fullName: 'Luis Ramos', active: true, createdAt: '2026-10-10T15:00:00Z' });
    await settle();
    const memory = () => (component as unknown as { password(): string | null }).password();
    expect(memory()).toBe(sent[0].password);
    destroy();
    expect(memory()).toBeNull();
  });
});
