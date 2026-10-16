import { TestBed } from '@angular/core/testing';

import { testJwt } from '../../testing/jwt';
import { SessionService } from './session.service';

describe('SessionService, token times', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('keeps exp and iat from the token, and survives a reload', () => {
    const iat = 1_791_660_000;
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', exp: iat + 3600, iat }));
    TestBed.resetTestingModule(); // a reload: read back from sessionStorage
    const session = TestBed.inject(SessionService);
    expect(session.expiresAt()).toBe((iat + 3600) * 1000);
    expect(session.issuedAt()).toBe(iat * 1000);
  });

  it('reads the role and the pending password change from the token; no claim means STAFF and nothing pending', () => {
    const exp = Math.floor(Date.now() / 1000) + 600;
    const session = TestBed.inject(SessionService);
    session.start(testJwt({ email: 'jefa@asociacion.pe', exp, role: 'ADMIN', pwdChange: true }));
    expect([session.role(), session.isAdmin(), session.mustChangePassword()]).toEqual(['ADMIN', true, true]);

    session.start(testJwt({ email: 'ana@asociacion.pe', exp }));
    expect([session.role(), session.isAdmin(), session.mustChangePassword()]).toEqual(['STAFF', false, false]);

    session.start(testJwt({ email: 'rara@asociacion.pe', exp, role: 'SUPERUSER' }));
    expect(session.role()).toBe('STAFF'); // an unknown role is never an admin

    session.clear();
    expect([session.role(), session.isAdmin(), session.mustChangePassword()]).toEqual([null, false, false]);
  });

  it('keeps the role across a reload, and a session stored before roles is a STAFF with nothing pending', () => {
    const exp = Math.floor(Date.now() / 1000) + 600;
    TestBed.inject(SessionService).start(testJwt({ email: 'jefa@asociacion.pe', exp, role: 'ADMIN' }));
    TestBed.resetTestingModule();
    expect(TestBed.inject(SessionService).isAdmin()).toBe(true);

    TestBed.resetTestingModule();
    sessionStorage.setItem('esg.session', JSON.stringify({ id: 's', token: 't', email: 'ana@asociacion.pe', expiresAt: exp * 1000 }));
    const old = TestBed.inject(SessionService);
    expect([old.role(), old.mustChangePassword()]).toEqual(['STAFF', false]);
  });

  it('a token without iat, or a session stored before iat was kept, has no issue time', () => {
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', exp: 1_791_663_600 }));
    expect(TestBed.inject(SessionService).issuedAt()).toBeNull();

    TestBed.resetTestingModule();
    sessionStorage.setItem('esg.session', JSON.stringify({ id: 's', token: 't', email: 'ana@asociacion.pe', expiresAt: 1 }));
    expect(TestBed.inject(SessionService).issuedAt()).toBeNull();
    expect(TestBed.inject(SessionService).expiresAt()).toBe(1);
  });
});
