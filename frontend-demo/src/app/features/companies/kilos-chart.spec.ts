import { EsgCertificate } from '../../core/api/api.types';
import { MAX_PERIODS, buildKilosChart, latestCertificate, niceAxis } from './kilos-chart';

function certificate(month: string, kg: number, issuedAt = '2026-01-10T15:00:00Z'): EsgCertificate {
  const [year, mm] = month.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, mm, 0)).getUTCDate();
  return {
    id: `cert-${month}`,
    trackedCompanyId: 'company-1',
    associationId: 'association-1',
    companyName: 'Envases del Pacífico S.A.C.',
    companyRuc: '20512345678',
    periodStart: `${month}-01`,
    periodEnd: `${month}-${String(lastDay).padStart(2, '0')}`,
    kilosTrazados: kg,
    hierarchyCompliancePercent: 90,
    issuedAt,
    status: 'ISSUED',
      annulledAt: null,
      annulledById: null,
      annulledByEmail: null,
      annulmentReason: null,
  };
}

const months = (from: number, count: number) =>
  Array.from({ length: count }, (_, i) => {
    const total = from + i; // months since 2024-01
    return `${2024 + Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
  });

describe('buildKilosChart', () => {
  it('keeps the 12 most recent periods, ordered oldest first, whatever the issue order', () => {
    // 14 periods, delivered newest-issued first but with periods shuffled.
    const certs = months(0, 14)
      .map((m, i) => certificate(m, 1000 + i))
      .sort((a, b) => (a.id < b.id ? 1 : -1));
    const shuffled = [certs[3], ...certs.slice(0, 3), ...certs.slice(4)];

    const chart = buildKilosChart(shuffled);

    expect(chart.bars).toHaveLength(MAX_PERIODS);
    expect(chart.bars[0].longLabel).toBe('Marzo 2024');
    expect(chart.bars[11].longLabel).toBe('Febrero 2025');
    expect(chart.bars.map((b) => b.kg)).toEqual(months(2, 12).map((_, i) => 1002 + i));
  });

  it('draws only the certified periods it has: no invented zero bars', () => {
    const chart = buildKilosChart([certificate('2024-10', 12480.5), certificate('2024-09', 9000)]);

    expect(chart.bars.map((b) => b.label)).toEqual(['Set 24', 'Oct 24']);
    expect(chart.bars[1].kgText).toBe('12,480.50 kg');
  });

  it('fits the axis to the data with a nice step, so a small month is not a flat line', () => {
    expect(buildKilosChart([certificate('2024-10', 79.45)])).toMatchObject({ axisTop: 100, ticks: [0, 50, 100] });
    expect(buildKilosChart([certificate('2024-10', 12480.5)]).ticks).toEqual([0, 5000, 10000, 15000]);
    expect(buildKilosChart([certificate('2024-10', 14999)]).ticks).toEqual([0, 5000, 10000, 15000]);
    expect(buildKilosChart([certificate('2024-10', 15000.01)]).ticks).toEqual([0, 10000, 20000]);
    expect(buildKilosChart([]).ticks).toEqual([0, 5, 10]);
  });

  it('always holds the largest bar, whatever the magnitude', () => {
    for (const max of [0.3, 1, 7.2, 99.99, 100, 333, 1234.56, 99999999.99]) {
      const { axisTop, ticks } = niceAxis(max);
      expect(axisTop, String(max)).toBeGreaterThanOrEqual(max);
      expect(ticks[0]).toBe(0);
      expect(ticks.length, String(max)).toBeLessThanOrEqual(6);
    }
  });
  it('is empty, not broken, without certificates', () => {
    expect(buildKilosChart([]).bars).toEqual([]);
  });
});

describe('latestCertificate', () => {
  it('is the most recent period, not the most recently issued certificate', () => {
    // October 2024 issued last (a late re-issue) must not beat December 2024.
    const certs = [
      certificate('2024-10', 1, '2025-03-01T15:00:00Z'),
      certificate('2024-12', 2, '2025-01-05T15:00:00Z'),
      certificate('2024-11', 3, '2024-12-05T15:00:00Z'),
    ];

    expect(latestCertificate(certs)?.periodStart).toBe('2024-12-01');
  });

  it('is null without certificates', () => {
    expect(latestCertificate([])).toBeNull();
  });
});

describe('annulled certificates', () => {
  const annulled = (month: string, kg: number): EsgCertificate => ({
    ...certificate(month, kg),
    status: 'ANNULLED',
    annulledAt: '2026-02-01T10:00:00Z',
    annulledById: 'staff-1',
    annulledByEmail: 'ana@example.com',
    annulmentReason: 'Datos erróneos',
  });

  it('draw no bar: an annulled period is history, and the period reissued over it counts once', () => {
    const chart = buildKilosChart([annulled('2025-03', 99_000), certificate('2025-03', 500), certificate('2025-02', 400)]);

    expect(chart.bars.map((b) => b.kg)).toEqual([400, 500]);
    expect(chart.axisTop).toBe(600); // sized by the 500 kg bar, not the annulled 99,000
  });

  it('are never the latest certified period', () => {
    expect(latestCertificate([annulled('2025-05', 1), certificate('2025-04', 2)])?.id).toBe('cert-2025-04');
    expect(latestCertificate([annulled('2025-05', 1)])).toBeNull();
  });
});
