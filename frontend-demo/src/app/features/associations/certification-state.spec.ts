import { EXPIRING_SOON_DAYS, certificationState, daysUntilExpiration, expirationNote } from './certification-state';

const TODAY = '2026-10-10';
const cert = (expirationDate: string, expired = false) => ({ expirationDate, expired });

describe('certificationState', () => {
  it('vencido: the backend flag, or a date already past in Lima', () => {
    expect(certificationState(cert('2026-09-01', true), TODAY)).toBe('vencido');
    // The backend's today can be ahead of Lima's (UTC server): its flag still wins.
    expect(certificationState(cert('2026-10-10', true), TODAY)).toBe('vencido');
    // A passed date is never "por vencer", even if the flag lags (clock skew).
    expect(certificationState(cert('2026-10-09', false), TODAY)).toBe('vencido');
  });

  it('por vencer from today up to the window, inclusive; vigente after it', () => {
    expect(certificationState(cert('2026-10-10'), TODAY)).toBe('por-vencer');
    expect(certificationState(cert('2026-11-09'), TODAY)).toBe('por-vencer'); // 30 days
    expect(EXPIRING_SOON_DAYS).toBe(30);
    expect(certificationState(cert('2026-11-10'), TODAY)).toBe('vigente'); // 31 days
    expect(certificationState(cert('2027-10-10'), TODAY)).toBe('vigente');
  });

  it('counts calendar days across month and year ends', () => {
    expect(daysUntilExpiration(cert('2027-01-01'), '2026-12-31')).toBe(1);
    expect(daysUntilExpiration(cert('2028-03-01'), '2028-02-28')).toBe(2); // leap year
  });
});

describe('expirationNote', () => {
  it('says when, in days', () => {
    expect(expirationNote(cert('2026-10-10'), TODAY)).toBe('Vence hoy');
    expect(expirationNote(cert('2026-10-11'), TODAY)).toBe('Vence en 1 día');
    expect(expirationNote(cert('2026-10-22'), TODAY)).toBe('Vence en 12 días');
    expect(expirationNote(cert('2026-10-09', true), TODAY)).toBe('Venció hace 1 día');
    expect(expirationNote(cert('2026-09-05', true), TODAY)).toBe('Venció hace 35 días');
    // Expired by the backend's clock but still today in Lima: never "hace 0 días".
    expect(expirationNote(cert('2026-10-10', true), TODAY)).toBe('Ya venció');
  });
});
