import { EsgCertificate } from '../../core/api/api.types';
import { formatKg, periodLabel, shortPeriodLabel } from '../../shared/format';

/** The brief's axis: 0 / 5,000 / 10,000 / 15,000 kg, extended in 5,000 kg steps only if a value is above it. */
export const AXIS_STEP_KG = 5_000;
export const AXIS_MIN_TOP_KG = 15_000;
export const MAX_PERIODS = 12;

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

/**
 * The most recent certified period (by period, not issue date): the same one
 * the chart highlights as its last bar. Null without certificates.
 */
export function latestCertificate(certificates: readonly EsgCertificate[]): EsgCertificate | null {
  return certificates.reduce<EsgCertificate | null>(
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
  const latest = [...certificates]
    .sort((a, b) => b.periodStart.localeCompare(a.periodStart))
    .slice(0, MAX_PERIODS)
    .reverse();
  const max = latest.reduce((m, c) => Math.max(m, c.kilosTrazados), 0);
  const axisTop = Math.max(AXIS_MIN_TOP_KG, Math.ceil(max / AXIS_STEP_KG) * AXIS_STEP_KG);
  const ticks: number[] = [];
  for (let tick = 0; tick <= axisTop; tick += AXIS_STEP_KG) {
    ticks.push(tick);
  }
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
