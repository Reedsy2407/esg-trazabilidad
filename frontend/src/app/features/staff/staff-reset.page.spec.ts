import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { staffUser } from '../../testing/staff-user';
import { StaffResetPage } from './staff-reset.page';

describe('StaffResetPage', () => {
  let el: HTMLElement;
  let sent: { id: string; password: string }[];
  let answer: Subject<void>;

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (text: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;
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
  const attributes = () => [...el.querySelectorAll('*')].flatMap((n) => [...n.attributes].map((a) => a.value)).join('\n');

  const setup = async (id = 'a-2', accounts: () => Observable<unknown> = () => of([staffUser({ id: 'a-2' })])) => {
    sent = [];
    answer = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [StaffResetPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            staffUsers: accounts,
            resetStaffPassword: (accountId: string, password: string) => {
              sent.push({ id: accountId, password });
              return answer;
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(StaffResetPage);
    fixture.componentRef.setInput('id', id);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  it('shows the account, asks for the confirmation, and sends nothing without it', async () => {
    await setup();
    expect(el.querySelector('.facts')?.textContent).toContain('ana@asociacion.pe');
    await submit();
    expect(el.querySelector('#ack-error')?.textContent).toBe('Marca la casilla para confirmar.');
    expect(sent).toEqual([]);
  });

  it('generates a 16-character password, hides it until asked, and never puts it in an attribute', async () => {
    await setup();
    acknowledge();
    await submit();
    expect(sent).toHaveLength(1);
    expect(sent[0].id).toBe('a-2');
    expect(sent[0].password).toMatch(/^[A-HJ-NP-Za-km-np-z2-9]{16}$/);

    answer.next();
    answer.complete();
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toContain('Contraseña restablecida para ana@asociacion.pe');
    expect(el.querySelector('.secret code')?.textContent).toContain('••••');
    expect(el.textContent).not.toContain(sent[0].password);
    button('Mostrar').click();
    await settle();
    expect(el.querySelector('.secret code')?.textContent).toBe(sent[0].password);
    expect(attributes()).not.toContain(sent[0].password);
    button('Ya la entregué').click();
    await settle();
    expect(el.textContent).not.toContain(sent[0].password);
  });

  it('an unknown outcome keeps the password and retries with the same one', async () => {
    await setup();
    acknowledge();
    await submit();
    answer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toContain('No pudimos confirmar');

    answer = new Subject<void>();
    button('Volver a intentar con la misma contraseña').click();
    await settle();
    expect(sent).toHaveLength(2);
    expect(sent[1].password).toBe(sent[0].password);
    answer.next();
    answer.complete();
    await settle();
    expect(el.querySelector('#result-title')?.textContent).toContain('Contraseña restablecida');
  });

  it('a refusal drops the password and says why', async () => {
    await setup();
    acknowledge();
    await submit();
    answer.error(new ApiError(409, 'AUTH-009', null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('usa «Cambiar mi contraseña»');
    expect(el.querySelector('.secret')).toBeNull();
  });

  it('an account that is not in the list says so', async () => {
    await setup('desconocida');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('La cuenta no existe en la lista');
  });
});
