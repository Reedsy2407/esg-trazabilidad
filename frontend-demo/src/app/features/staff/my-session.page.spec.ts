import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';

import { StaffUser } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { testJwt } from '../../testing/jwt';
import { MySessionPage } from './my-session.page';

describe('MySessionPage', () => {
  const settle = async () => {
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
  };

  const setup = async (me: () => ReturnType<BackendApi['me']>) => {
    TestBed.configureTestingModule({
      imports: [MySessionPage],
      providers: [provideRouter([]), { provide: BackendApi, useValue: { me } }],
    });
    // Issued 14:42 Lima (19:42 UTC), 45 minutes long.
    const iat = Date.parse('2026-10-10T19:42:00Z') / 1000;
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', iat, exp: iat + 45 * 60 }));
    const fixture = TestBed.createComponent(MySessionPage);
    await settle();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => sessionStorage.clear());

  it('the account from /auth/me and the expiry from the token', async () => {
    const user: StaffUser = { id: 'staff-1', email: 'ana@asociacion.pe', fullName: 'Ana Paredes Quispe', active: true, createdAt: '2026-08-14T15:20:00Z' };
    const el = await setup(() => of(user));
    expect(el.querySelector('.sub')?.textContent?.trim()).toBe('Tu sesión vence a las 15:27 (dura 45 minutos y no se renueva).');
    const facts = [...el.querySelectorAll('.facts dd')].map((d) => d.textContent?.trim());
    expect(facts).toEqual(['Ana Paredes Quispe', 'ana@asociacion.pe', 'Activa', '14/08/2026']);
    expect(el.textContent).not.toContain(TestBed.inject(SessionService).token());
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
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('el servicio de autenticación no respondió (HTTP 503)');
    (el.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    await settle();
    expect(calls).toBe(2);
  });
});
