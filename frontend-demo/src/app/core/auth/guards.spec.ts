import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { testJwt } from '../../testing/jwt';
import { adminGuard, authGuard, guestGuard, passwordChangeGuard } from './guards';
import { SessionService } from './session.service';

const route = {} as ActivatedRouteSnapshot;
const state = {} as RouterStateSnapshot;
const runAuthGuard = () => TestBed.runInInjectionContext(() => authGuard(route, state));
const runGuestGuard = () => TestBed.runInInjectionContext(() => guestGuard(route, state));
const nowSeconds = () => Math.floor(Date.now() / 1000);

describe('route guards', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('sends a signed-out visitor to the login', () => {
    const result = runAuthGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(String(result)).toBe('/login');
  });

  it('lets a signed-in user through', () => {
    TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60 }));

    expect(runAuthGuard()).toBe(true);
  });

  it('treats an expired token as signed out and forgets it', () => {
    const session = TestBed.inject(SessionService);
    session.start(testJwt({ email: 'a@b.pe', exp: nowSeconds() - 1 }));

    expect(String(runAuthGuard())).toBe('/login');
    expect(session.token()).toBeNull();
  });

  describe('passwordChangeGuard', () => {
    const run = (url: string) =>
      TestBed.runInInjectionContext(() => passwordChangeGuard(route, { url } as RouterStateSnapshot));
    const exp = () => nowSeconds() + 60;

    it('lets anyone through when no password change is pending', () => {
      TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: exp() }));
      expect(run('/empresas')).toBe(true);
    });

    it('sends a person with a pending change to the change screen, and lets that screen open', () => {
      TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: exp(), pwdChange: true }));
      expect(String(run('/empresas'))).toBe('/mi-sesion/contrasena');
      expect(String(run('/mi-sesion'))).toBe('/mi-sesion/contrasena');
      expect(run('/mi-sesion/contrasena')).toBe(true);
      expect(run('/mi-sesion/contrasena?x=1')).toBe(true);
    });
  });

  describe('adminGuard', () => {
    const run = () => TestBed.runInInjectionContext(() => adminGuard(route, state));

    it('opens the account screens to an admin only', () => {
      const session = TestBed.inject(SessionService);
      session.start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60, role: 'ADMIN' }));
      expect(run()).toBe(true);

      session.start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60, role: 'STAFF' }));
      expect(String(run())).toBe('/mi-sesion');

      session.start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60 }));
      expect(String(run())).toBe('/mi-sesion');
    });
  });

  it('sends a signed-in user away from the login to the companies', () => {
    expect(runGuestGuard()).toBe(true);

    TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60 }));

    expect(String(runGuestGuard())).toBe('/empresas');
  });
});
