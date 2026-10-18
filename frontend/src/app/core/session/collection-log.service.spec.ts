import { TestBed } from '@angular/core/testing';

import { inOneHour, testJwt } from '../../testing/jwt';
import { SessionService } from '../auth/session.service';
import { CollectionLogService } from './collection-log.service';

const entry = (id: string) => ({ id, neighbor: 'Rosa', association: 'Recicla Rímac', date: '10/10/2026', kilos: '1.00 kg' });
const token = (email = 'ana@asociacion.pe') => testJwt({ email, exp: inOneHour() });

describe('CollectionLogService', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('newest first, one entry per record, at most 50, kept in sessionStorage', () => {
    TestBed.inject(SessionService).start(token());
    const log = TestBed.inject(CollectionLogService);
    log.add(entry('a'));
    log.add(entry('b'));
    log.add(entry('a'));
    expect(log.entries().map((e) => e.id)).toEqual(['a', 'b']);
    for (let i = 0; i < 60; i++) {
      log.add(entry(`x${i}`));
    }
    expect(log.entries()).toHaveLength(50);
    expect(JSON.parse(sessionStorage.getItem('esg.bitacora') ?? '{}').entries).toHaveLength(50);
  });

  it('survives a reload of the same sign-in, and ignores a damaged stored copy', () => {
    TestBed.inject(SessionService).start(token());
    TestBed.inject(CollectionLogService).add(entry('a'));
    TestBed.resetTestingModule(); // a reload: both services read sessionStorage again
    expect(TestBed.inject(CollectionLogService).entries().map((e) => e.id)).toEqual(['a']);

    TestBed.resetTestingModule();
    sessionStorage.setItem('esg.bitacora', '["no es un registro"]');
    expect(TestBed.inject(CollectionLogService).entries()).toEqual([]);
  });

  it('a sign-out ends it', () => {
    const session = TestBed.inject(SessionService);
    session.start(token());
    const log = TestBed.inject(CollectionLogService);
    log.add(entry('a'));
    session.clear();
    expect(sessionStorage.getItem('esg.bitacora')).toBeNull();
    expect(log.entries()).toEqual([]);
  });

  it('the next sign-in on the tab never sees it, even when the old session just expired (no clear())', () => {
    const session = TestBed.inject(SessionService);
    session.start(token('ana@asociacion.pe'));
    const log = TestBed.inject(CollectionLogService);
    log.add(entry('a'));
    // Expired token: the guard sends the tab to the login and someone signs in, without clear().
    session.start(token('luis@asociacion.pe'));
    expect(log.entries()).toEqual([]);
    expect(sessionStorage.getItem('esg.bitacora')).toBeNull();

    // Even a stored log left behind by another sign-in is not shown.
    sessionStorage.setItem('esg.bitacora', JSON.stringify({ owner: 'otra-sesion', entries: [entry('z')] }));
    TestBed.resetTestingModule();
    expect(TestBed.inject(CollectionLogService).entries()).toEqual([]);
  });
});
