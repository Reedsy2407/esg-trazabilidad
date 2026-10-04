import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { testJwt } from '../../testing/jwt';
import { authGuard, guestGuard } from './guards';
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

  it('sends a signed-in user away from the login to the companies', () => {
    expect(runGuestGuard()).toBe(true);

    TestBed.inject(SessionService).start(testJwt({ email: 'a@b.pe', exp: nowSeconds() + 60 }));

    expect(String(runGuestGuard())).toBe('/empresas');
  });
});
