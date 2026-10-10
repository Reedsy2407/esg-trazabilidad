import { parseLocalDate } from '../../shared/format';

/** A period as the API takes it: two LocalDates ("yyyy-MM-dd"), both days included. */
export interface Period {
  readonly start: string;
  readonly end: string;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** "2026-09" (an <input type="month"> value) → 1 to 30 September 2026. Null if not a month. */
export function monthPeriod(month: string): Period | null {
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (match === null) {
    return null;
  }
  const year = Number(match[1]);
  const m = Number(match[2]);
  if (m < 1 || m > 12) {
    return null;
  }
  const lastDay = new Date(Date.UTC(year, m, 0)).getUTCDate();
  return { start: `${year}-${pad(m)}-01`, end: `${year}-${pad(m)}-${pad(lastDay)}` };
}

/** The month before `today`'s, as "yyyy-MM": the latest month that has already ended. */
export function previousMonth(today: string): string {
  const { year, month } = parseLocalDate(today);
  return month === 1 ? `${year - 1}-12` : `${year}-${pad(month - 1)}`;
}

/** A LocalDate `days` later (or earlier), on the calendar, never shifted by a time zone. */
export function addDays(localDate: string, days: number): string {
  const { year, month, day } = parseLocalDate(localDate);
  const d = new Date(Date.UTC(year, month - 1, day + days));
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

export type PeriodProblem =
  | { readonly kind: 'backwards' }
  /** Angel's rule (2026-10-10): a period that hasn't ended (end >= today in Lima) is not issued. */
  | { readonly kind: 'not-ended'; readonly availableFrom: string };

/**
 * Checks the backend doesn't make (it would issue them): an end before the start (it has no code
 * for that, RPT-005 or a 500 at best), and a period still running in Lima, which would freeze its
 * kilos and leave the rest of its collections out for good.
 */
export function periodProblem(period: Period, today: string): PeriodProblem | null {
  if (period.end < period.start) {
    return { kind: 'backwards' };
  }
  if (period.end >= today) {
    return { kind: 'not-ended', availableFrom: addDays(period.end, 1) };
  }
  return null;
}

/** Two periods overlap when they share at least one day (the backend's own test, RPT-004). */
export function overlaps(a: Period, b: Period): boolean {
  return a.start <= b.end && a.end >= b.start;
}
