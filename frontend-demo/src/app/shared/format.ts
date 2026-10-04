import {
  FormStyle,
  TranslationWidth,
  formatDate,
  formatNumber,
  getLocaleMonthNames,
  registerLocaleData,
} from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';

/** es-PE everywhere; registered here too so pure helpers work outside the app bootstrap (tests). */
export const LOCALE = 'es-PE';
/** Lima: UTC-5 all year, no DST. */
export const LIMA_OFFSET = '-0500';
registerLocaleData(localeEsPe);

const LOCAL_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

interface DateParts {
  readonly year: number;
  readonly month: number; // 1-12
  readonly day: number;
}

/** A backend LocalDate ("yyyy-MM-dd"). Parsed by hand: it has no time zone, so it must never shift a day. */
export function parseLocalDate(value: string): DateParts {
  const match = LOCAL_DATE.exec(value);
  if (match === null) {
    throw new Error(`Not a LocalDate: ${value}`);
  }
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

/** "2024-10-01" -> "01/10/2024". */
export function formatLocalDate(value: string): string {
  const { year, month, day } = parseLocalDate(value);
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year}`;
}

/** A backend Instant shown as a Lima calendar date: dd/MM/yyyy. */
export function formatInstantDate(value: string): string {
  return formatDate(value, 'dd/MM/yyyy', LOCALE, LIMA_OFFSET);
}

/** 12480.5 -> "12,480.50 kg". */
export function formatKg(value: number): string {
  return `${formatNumber(value, LOCALE, '1.2-2')} kg`;
}

/** 87.5 -> "87.5 %"; null (no SIGERSOL sync covers the period) is a missing value, never 0 %. */
export function formatPercent(value: number | null): string {
  return value === null ? 'Sin registro SIGERSOL' : `${formatNumber(value, LOCALE, '1.1-1')} %`;
}

function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** True when the period is exactly one whole calendar month. */
export function isWholeMonth(periodStart: string, periodEnd: string): boolean {
  const start = parseLocalDate(periodStart);
  const end = parseLocalDate(periodEnd);
  return (
    start.year === end.year &&
    start.month === end.month &&
    start.day === 1 &&
    end.day === daysInMonth(end.year, end.month)
  );
}

function monthName(month: number, width: TranslationWidth): string {
  return getLocaleMonthNames(LOCALE, FormStyle.Standalone, width)[month - 1];
}

/** "Octubre 2024" for a whole month (brief), otherwise the exact range "01/10/2024 – 15/10/2024". */
export function periodLabel(periodStart: string, periodEnd: string): string {
  if (isWholeMonth(periodStart, periodEnd)) {
    const { year, month } = parseLocalDate(periodStart);
    return `${monthName(month, TranslationWidth.Wide)} ${year}`;
  }
  return `${formatLocalDate(periodStart)} – ${formatLocalDate(periodEnd)}`;
}

/** Chart axis label: "Oct 24" for a whole month, "01/10" (start) otherwise. */
export function shortPeriodLabel(periodStart: string, periodEnd: string): string {
  const { year, month, day } = parseLocalDate(periodStart);
  if (isWholeMonth(periodStart, periodEnd)) {
    return `${monthName(month, TranslationWidth.Abbreviated).replace('.', '')} ${String(year % 100).padStart(2, '0')}`;
  }
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`;
}
