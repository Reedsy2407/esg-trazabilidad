import { addDays, monthPeriod, overlaps, periodProblem, previousMonth } from './issue-period';

describe('issue period', () => {
  it('a month input becomes its whole month, leap years included', () => {
    expect(monthPeriod('2026-09')).toEqual({ start: '2026-09-01', end: '2026-09-30' });
    expect(monthPeriod('2028-02')).toEqual({ start: '2028-02-01', end: '2028-02-29' });
    expect(monthPeriod('2026-13')).toBeNull();
    expect(monthPeriod('')).toBeNull();
  });

  it('defaults to the month before today: the latest one that has ended', () => {
    expect(previousMonth('2026-10-10')).toBe('2026-09');
    expect(previousMonth('2027-01-01')).toBe('2026-12');
  });

  it('adds calendar days across month and year ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('refuses a backwards period and one that has not ended in Lima, saying from when', () => {
    const today = '2026-10-10';
    expect(periodProblem({ start: '2026-09-10', end: '2026-09-01' }, today)).toEqual({ kind: 'backwards' });
    // The current month: it can be issued from the 1st of the next.
    expect(periodProblem({ start: '2026-10-01', end: '2026-10-31' }, today)).toEqual({ kind: 'not-ended', availableFrom: '2026-11-01' });
    // Ending today hasn't ended yet either.
    expect(periodProblem({ start: '2026-10-01', end: '2026-10-10' }, today)).toEqual({ kind: 'not-ended', availableFrom: '2026-10-11' });
    expect(periodProblem({ start: '2026-10-01', end: '2026-10-09' }, today)).toBeNull();
    expect(periodProblem({ start: '2026-09-01', end: '2026-09-30' }, today)).toBeNull();
    // One day is a valid period.
    expect(periodProblem({ start: '2026-09-15', end: '2026-09-15' }, today)).toBeNull();
  });

  it('overlap means sharing at least one day', () => {
    const sept = { start: '2026-09-01', end: '2026-09-30' };
    expect(overlaps(sept, { start: '2026-09-30', end: '2026-10-31' })).toBe(true);
    expect(overlaps(sept, { start: '2026-10-01', end: '2026-10-31' })).toBe(false);
    expect(overlaps(sept, { start: '2026-08-01', end: '2026-08-31' })).toBe(false);
  });
});
