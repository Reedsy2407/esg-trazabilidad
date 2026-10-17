import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';

import { StaffUser } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { testJwt } from '../../testing/jwt';
import { staffUser } from '../../testing/staff-user';
import { MySessionPage } from './my-session.page';

describe('MySessionPage', () => {
  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };

  const setup = async (me: () => ReturnType<BackendApi['me']>, role?: string) => {
    TestBed.configureTestingModule({
      imports: [MySessionPage],
      providers: [provideRouter([]), { provide: BackendApi, useValue: { me } }],
    });
    // Issued 14:42 Lima (19:42 UTC), 45 minutes long.
    const iat = Date.parse('2026-10-10T19:42:00Z') / 1000;
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', iat, exp: iat + 45 * 60, role }));
    const fixture = TestBed.createComponent(MySessionPage);
    await settle();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => sessionStorage.clear());

  it('the account from /auth/me and the expiry from the token', async () => {
    const el = await setup(() => of(staffUser()));
    expect(el.querySelector('.sub')?.textContent?.trim()).toBe('Tu sesión vence a las 15:27 (dura 45 minutos y no se renueva).');
    const facts = [...el.querySelectorAll('.facts dd')].map((d) => d.textContent?.trim());
    expect(facts).toEqual([
      'Ana Paredes Quispe',
      'ana@asociacion.pe',
      'Personal',
      'Activa',
      '14/08/2026',
      'Nunca desde que se creó la cuenta',
    ]);
    expect(el.textContent).not.toContain(TestBed.inject(SessionService).token());
  });

  it('shows when the password was last changed, and links to change it', async () => {
    const el = await setup(() => of(staffUser({ passwordChangedAt: '2026-10-14T15:00:00Z' })));
    const facts = [...el.querySelectorAll('.facts dd')].map((d) => d.textContent?.trim());
    expect(facts.at(-1)).toBe('14/10/2026');
    expect(el.querySelector('a[href="/mi-sesion/contrasena"]')?.textContent?.trim()).toBe('Cambiar mi contraseña');
  });

  it('the account screens are offered to an admin only', async () => {
    const staff = await setup(() => of(staffUser()));
    expect(staff.querySelector('a[href="/mi-sesion/personal"]')).toBeNull();
    expect(staff.querySelector('a[href="/mi-sesion/personal/nueva"]')).toBeNull();
    TestBed.resetTestingModule();

    const admin = await setup(() => of(staffUser({ role: 'ADMIN' })), 'ADMIN');
    expect(admin.querySelector('a[href="/mi-sesion/personal"]')).not.toBeNull();
    expect(admin.querySelector('a[href="/mi-sesion/personal/nueva"]')).not.toBeNull();
    expect([...admin.querySelectorAll('.facts dd')].map((d) => d.textContent?.trim())).toContain('Administrador');
  });

  it('a failure says why and retries', async () => {
    let calls = 0;
    let answer = new Subject<StaffUser>();
    const el = await setup(() => {
      calls += 1;
      answer = new Subject<StaffUser>();
      return answer;
    });
    answer.error(new ApiError(503, null, null, null));
    await settle();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('el servicio de autenticación no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo.');
    (el.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    await settle();
    expect(calls).toBe(2);
  });
});
