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

  it('a token without iat, or a session stored before iat was kept, has no issue time', () => {
    TestBed.inject(SessionService).start(testJwt({ email: 'ana@asociacion.pe', exp: 1_791_663_600 }));
    expect(TestBed.inject(SessionService).issuedAt()).toBeNull();

    TestBed.resetTestingModule();
    sessionStorage.setItem('esg.session', JSON.stringify({ id: 's', token: 't', email: 'ana@asociacion.pe', expiresAt: 1 }));
    expect(TestBed.inject(SessionService).issuedAt()).toBeNull();
    expect(TestBed.inject(SessionService).expiresAt()).toBe(1);
  });
});
