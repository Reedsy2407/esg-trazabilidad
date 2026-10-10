import {
  formatInstantDate,
  formatKg,
  formatLocalDate,
  formatPercent,
  formatPercentExact,
  isWholeMonth,
  periodLabel,
  shortPeriodLabel,
} from './format';

describe('es-PE formatters', () => {
  it('formats kilos with thousands commas, two decimals and the unit', () => {
    expect(formatKg(12480.5)).toBe('12,480.50 kg');
    expect(formatKg(0)).toBe('0.00 kg');
    expect(formatKg(1234567.891)).toBe('1,234,567.89 kg');
  });

  it('formats compliance with one decimal, and a missing SIGERSOL value as text, never 0 %', () => {
    expect(formatPercent(87.5)).toBe('87.5 %');
    expect(formatPercent(100)).toBe('100.0 %');
    expect(formatPercent(null)).toBe('Sin registro SIGERSOL');
  });

  it('formats a LocalDate without any time-zone shift', () => {
    expect(formatLocalDate('2024-10-01')).toBe('01/10/2024');
    expect(formatLocalDate('2024-12-31')).toBe('31/12/2024');
    expect(() => formatLocalDate('01/10/2024')).toThrow();
  });

  it('shows an Instant as its calendar date in Lima (UTC-5)', () => {
    // 04:30 UTC on Nov 5 is still Nov 4 in Lima.
    expect(formatInstantDate('2024-11-05T04:30:00Z')).toBe('04/11/2024');
    expect(formatInstantDate('2024-11-05T05:00:00Z')).toBe('05/11/2024');
  });

  it('names a whole calendar month, and spells out any other range', () => {
    expect(isWholeMonth('2024-10-01', '2024-10-31')).toBe(true);
    expect(isWholeMonth('2024-02-01', '2024-02-29')).toBe(true); // leap year
    expect(isWholeMonth('2023-02-01', '2023-02-28')).toBe(true);
    expect(isWholeMonth('2024-10-01', '2024-10-30')).toBe(false);
    expect(isWholeMonth('2024-10-02', '2024-10-31')).toBe(false);

    expect(periodLabel('2024-10-01', '2024-10-31')).toBe('Octubre 2024');
    expect(periodLabel('2024-09-01', '2024-09-30')).toBe('Setiembre 2024'); // es-PE spelling
    expect(periodLabel('2024-10-01', '2024-10-15')).toBe('01/10/2024 – 15/10/2024');
  });

  it('gives the chart a short label per period', () => {
    expect(shortPeriodLabel('2024-10-01', '2024-10-31')).toBe('Oct 24');
    expect(shortPeriodLabel('2024-09-01', '2024-09-30')).toBe('Set 24');
    expect(shortPeriodLabel('2024-10-01', '2024-10-15')).toBe('01/10');
  });
});

describe('formatPercentExact', () => {
  it('shows a typed-in percentage as stored, up to 2 decimals', () => {
    expect(formatPercentExact(91.25)).toBe('91.25 %');
    expect(formatPercentExact(90)).toBe('90 %');
    expect(formatPercentExact(87.5)).toBe('87.5 %');
  });
});
