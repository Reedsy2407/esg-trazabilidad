import { fitProjection, formatDistance, haversineM, pathOf, walkedMeters } from './route-geometry';

describe('route geometry', () => {
  const square = [
    { lat: -12.04, lng: -77.05 },
    { lat: -12.04, lng: -77.04 },
    { lat: -12.05, lng: -77.04 },
    { lat: -12.05, lng: -77.05 },
  ];

  it('puts north up and fits inside the drawing with its padding', () => {
    const { project } = fitProjection(square, 640, 400, 40);
    const [nw, ne, se, sw] = square.map(project);
    expect(nw.y).toBeLessThan(sw.y);
    expect(nw.x).toBeLessThan(ne.x);
    expect(se.x).toBeCloseTo(ne.x, 5);
    for (const p of [nw, ne, se, sw]) {
      expect(p.x).toBeGreaterThanOrEqual(40 - 0.001);
      expect(p.x).toBeLessThanOrEqual(600 + 0.001);
      expect(p.y).toBeGreaterThanOrEqual(40 - 0.001);
      expect(p.y).toBeLessThanOrEqual(360 + 0.001);
    }
  });

  it('keeps the true proportions (a degree of longitude is shorter than one of latitude)', () => {
    const { project } = fitProjection(
      [
        { lat: 0, lng: 0 },
        { lat: 0, lng: 1 },
        { lat: 1, lng: 0 },
      ],
      500,
      500,
      0,
    );
    const west = project({ lat: 0, lng: 0 });
    const east = project({ lat: 0, lng: 1 });
    const north = project({ lat: 1, lng: 0 });
    expect(Math.abs(east.x - west.x)).toBeCloseTo(Math.abs(north.y - west.y), 0);
  });

  it('centres a route that never moved instead of failing', () => {
    const { project, scaleBar } = fitProjection([{ lat: -12, lng: -77 }], 640, 400, 40);
    expect(project({ lat: -12, lng: -77 })).toEqual({ x: 320, y: 200 });
    expect(Number.isFinite(scaleBar.px)).toBe(true);
  });

  it('draws a scale bar that is a round length no wider than 30 % of the drawing', () => {
    const { scaleBar } = fitProjection(square, 640, 400, 40);
    expect([10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000]).toContain(scaleBar.meters);
    expect(scaleBar.px).toBeLessThanOrEqual(640 * 0.3);
    expect(scaleBar.px).toBeGreaterThan(0);
    // ~1.1 km square: the bar is the largest round length that fits.
    expect(scaleBar.label).toMatch(/^(\d+ m|\d+ km)$/);
  });

  it('writes a path in drawing units', () => {
    expect(pathOf([])).toBe('');
    expect(pathOf([{ x: 1, y: 2 }, { x: 3.14159, y: 4 }])).toBe('M1.0 2.0 L3.1 4.0');
  });

  it('measures about 111 km for a degree of latitude and ignores legs of poor accuracy', () => {
    expect(haversineM({ lat: 0, lng: 0 }, { lat: 1, lng: 0 })).toBeGreaterThan(111_000);
    expect(haversineM({ lat: 0, lng: 0 }, { lat: 1, lng: 0 })).toBeLessThan(111_400);
    const walked = walkedMeters([
      { lat: 0, lng: 0, accuracyM: 10 },
      { lat: 0.001, lng: 0, accuracyM: 20 },
      { lat: 0.002, lng: 0, accuracyM: 300 },
      { lat: 0.003, lng: 0, accuracyM: 10 },
    ]);
    expect(walked).toBeCloseTo(111.2, 0);
  });

  it('says short distances in metres and long ones in kilometres', () => {
    expect(formatDistance(640.4)).toBe('640 m');
    expect(formatDistance(3180.5)).toBe('3.18 km');
  });
});
