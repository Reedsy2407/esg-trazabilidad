import { EsgCertificate } from '../../core/api/api.types';
import { formatKg, periodLabel, shortPeriodLabel } from '../../shared/format';

export const MAX_PERIODS = 12;
/** Intervals the axis aims for: few enough that the labels breathe, enough to read a bar against. */
const TARGET_INTERVALS = 3;

/**
 * An axis that fits the data: a "nice" step (1, 2 or 5 times a power of ten) giving about three
 * intervals, topped at the first multiple that holds the largest bar. 79.45 kg gets 0 / 50 / 100,
 * 12,480.50 kg gets 0 / 5,000 / 10,000 / 15,000. No data gets a small empty 0-10 axis.
 */
export function niceAxis(max: number): { axisTop: number; ticks: number[] } {
  const clean = (n: number) => Math.round(n * 1e6) / 1e6;
  let step = 5;
  if (max > 0) {
    const raw = max / TARGET_INTERVALS;
    const magnitude = 10 ** Math.floor(Math.log10(raw));
    const normalized = raw / magnitude;
    step = clean((normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude);
  }
  const intervals = max > 0 ? Math.ceil(clean(max / step)) : 2;
  const ticks = Array.from({ length: intervals + 1 }, (_, i) => clean(i * step));
  return { axisTop: ticks[ticks.length - 1], ticks };
}

export interface ChartBar {
  readonly key: string;
  readonly label: string;
  readonly longLabel: string;
  readonly kg: number;
  readonly kgText: string;
}

export interface ChartModel {
  /** Oldest period first, at most MAX_PERIODS. */
  readonly bars: readonly ChartBar[];
  readonly axisTop: number;
  readonly ticks: readonly number[];
}

/** An annulled certificate is history, not a certified period: it feeds neither the chart nor the summary. */
export function isIssued(certificate: EsgCertificate): boolean {
  return certificate.status === 'ISSUED';
}

/**
 * The most recent certified period (by period, not issue date): the same one
 * the chart highlights as its last bar. Null without certificates.
 */
export function latestCertificate(certificates: readonly EsgCertificate[]): EsgCertificate | null {
  return certificates.filter(isIssued).reduce<EsgCertificate | null>(
    (latest, c) => (latest === null || c.periodStart > latest.periodStart ? c : latest),
    null,
  );
}

/**
 * The 12 most recent certified periods, by period (not by issue date, which
 * is how the list arrives), oldest first. Fewer certificates give fewer bars:
 * no invented zero periods.
 */
export function buildKilosChart(certificates: readonly EsgCertificate[]): ChartModel {
  const latest = certificates
    .filter(isIssued)
    .sort((a, b) => b.periodStart.localeCompare(a.periodStart))
    .slice(0, MAX_PERIODS)
    .reverse();
  const max = latest.reduce((m, c) => Math.max(m, c.kilosTrazados), 0);
  const { axisTop, ticks } = niceAxis(max);
  return {
    axisTop,
    ticks,
    bars: latest.map((c) => ({
      key: c.id,
      label: shortPeriodLabel(c.periodStart, c.periodEnd),
      longLabel: periodLabel(c.periodStart, c.periodEnd),
      kg: c.kilosTrazados,
      kgText: formatKg(c.kilosTrazados),
    })),
  };
}
