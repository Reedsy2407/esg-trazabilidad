import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { StaffUser } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { inOneHour, testJwt } from '../../testing/jwt';
import { staffUser } from '../../testing/staff-user';
import { STATUS_UNCERTAIN, StaffListPage } from './staff-list.page';

const ME = staffUser({ id: 'a-1', email: 'jefa@asociacion.pe', fullName: 'Jefa Admin', role: 'ADMIN' });
const ANA = staffUser({ id: 'a-2', email: 'ana@asociacion.pe', fullName: 'Ana Paredes' });
const LUIS = staffUser({
  id: 'a-3',
  email: 'luis@asociacion.pe',
  fullName: 'Luis Ramos',
  active: false,
  statusReason: 'Dejó la asociación',
  statusChangedAt: '2026-10-14T15:00:00Z',
  statusChangedBy: 'jefa@asociacion.pe',
});

describe('StaffListPage', () => {
  let el: HTMLElement;
  let calls: string[];
  let answer: Subject<StaffUser>;
  let list: StaffUser[];

  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };
  const button = (label: string) => el.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
  const rows = () => [...el.querySelectorAll('.account')] as HTMLElement[];

  const setup = async (accounts: () => Observable<StaffUser[]>) => {
    sessionStorage.clear();
    calls = [];
    answer = new Subject<StaffUser>();
    TestBed.configureTestingModule({
      imports: [StaffListPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            staffUsers: accounts,
            deactivateStaffUser: (id: string, reason: string) => {
              calls.push(`deactivate ${id} ${reason}`);
              return answer;
            },
            reactivateStaffUser: (id: string, reason: string) => {
              calls.push(`reactivate ${id} ${reason}`);
              return answer;
            },
          },
        },
      ],
    });
    TestBed.inject(SessionService).start(testJwt({ email: ME.email, exp: inOneHour(), role: 'ADMIN' }));
    const fixture = TestBed.createComponent(StaffListPage);
    el = fixture.nativeElement as HTMLElement;
    await settle();
  };

  beforeEach(() => {
    list = [ME, ANA, LUIS];
  });

  it('lists every account with its role, state and the last status change with who and why', async () => {
    await setup(() => of(list));
    expect(rows()).toHaveLength(3);
    expect(rows()[0].textContent).toContain('Jefa Admin');
    expect(rows()[0].querySelector('.tag')?.textContent).toBe('tú');
    expect(rows()[0].textContent).toContain('Administrador');
    expect(rows()[1].textContent).toContain('Personal');
    expect(rows()[2].textContent).toContain('Inactiva');
    expect(rows()[2].textContent).toContain('14/10/2026');
    expect(rows()[2].textContent).toContain('por jefa@asociacion.pe');
    expect(rows()[2].textContent).toContain('«Dejó la asociación»');
  });

  it('your own row has no deactivate or reset: it points to changing your own password', async () => {
    await setup(() => of(list));
    expect(rows()[0].querySelector('button')).toBeNull();
    expect(rows()[0].querySelector('a[href="/mi-sesion/contrasena"]')).not.toBeNull();
    expect(button('Desactivar a ana@asociacion.pe')).not.toBeNull();
    expect(el.querySelector('a[href="/mi-sesion/personal/a-2/restablecer"]')).not.toBeNull();
    expect(button('Reactivar a luis@asociacion.pe')).not.toBeNull();
    // An inactive account is reactivated, not reset.
    expect(el.querySelector('a[href="/mi-sesion/personal/a-3/restablecer"]')).toBeNull();
  });

  it('deactivating asks for the reason and the confirmation, sends them and reloads the list', async () => {
    let loads = 0;
    await setup(() => {
      loads += 1;
      return of(list);
    });
    button('Desactivar a ana@asociacion.pe').click();
    await settle();
    expect(el.querySelector('app-reason-confirm')).not.toBeNull();
    const reason = el.querySelector('#deactivate-reason') as HTMLTextAreaElement;
    reason.value = 'Cambió de trabajo';
    reason.dispatchEvent(new Event('input'));
    const check = el.querySelector('.reason input[type="checkbox"]') as HTMLInputElement;
    check.checked = true;
    check.dispatchEvent(new Event('change'));
    TestBed.tick();
    (el.querySelector('.reason') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    expect(calls).toEqual(['deactivate a-2 Cambió de trabajo']);

    answer.next(staffUser({ id: 'a-2', active: false }));
    await settle();
    expect(loads).toBe(2);
    expect(el.querySelector('app-reason-confirm')).toBeNull();
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Cuenta de ana@asociacion.pe desactivada');
  });

  it('a refusal is said inside the panel; a bare 5xx says the outcome is unknown', async () => {
    await setup(() => of(list));
    button('Desactivar a ana@asociacion.pe').click();
    await settle();
    const reason = el.querySelector('#deactivate-reason') as HTMLTextAreaElement;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    const check = el.querySelector('.reason input[type="checkbox"]') as HTMLInputElement;
    check.checked = true;
    check.dispatchEvent(new Event('change'));
    TestBed.tick();
    (el.querySelector('.reason') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    answer.error(new ApiError(409, 'AUTH-010', null, null));
    await settle();
    expect(el.querySelector('.reason [role="alert"]')?.textContent).toContain('ya estaba en ese estado');

    answer = new Subject<StaffUser>();
    (el.querySelector('.reason') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
    answer.error(new ApiError(504, null, null, null));
    await settle();
    expect(el.querySelector('.reason [role="alert"]')?.textContent).toContain(STATUS_UNCERTAIN);
  });

  it('closing the panel changes nothing', async () => {
    await setup(() => of(list));
    button('Reactivar a luis@asociacion.pe').click();
    await settle();
    expect(el.querySelector('#reactivate-reason')).not.toBeNull();
    ([...el.querySelectorAll('.reason button')].find((b) => b.textContent?.includes('No, volver')) as HTMLButtonElement).click();
    await settle();
    expect(el.querySelector('app-reason-confirm')).toBeNull();
    expect(calls).toEqual([]);
  });

  it('a load failure says why and retries', async () => {
    let calls2 = 0;
    const failing = new Subject<StaffUser[]>();
    await setup(() => {
      calls2 += 1;
      return failing;
    });
    failing.error(new ApiError(403, 'AUTH-006', null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para ver las cuentas del personal');
    (el.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    await settle();
    expect(calls2).toBe(2);
  });
});
