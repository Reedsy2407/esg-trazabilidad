/**
 * The route drawn by hand: no map, no tiles, no third-party service (the coordinates never leave the
 * browser). Latitude and longitude are flattened with an equirectangular projection around the route's own
 * mean latitude, which is exact enough for a day's walk in one district.
 */

export interface LatLng {
  readonly lat: number;
  readonly lng: number;
}

export interface Projected {
  readonly x: number;
  readonly y: number;
}

export interface ScaleBar {
  /** Length of the bar in drawing units. */
  readonly px: number;
  readonly meters: number;
  readonly label: string;
}

export interface Projection {
  readonly project: (point: LatLng) => Projected;
  readonly scaleBar: ScaleBar;
}

const METERS_PER_DEGREE = 111_320;
const NICE_METERS = [10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000];

/**
 * Fits `points` into a `width` x `height` drawing with `padding` on every side, north up. A single point (or a
 * route that never moved) is centred at a fixed scale instead of dividing by zero.
 */
export function fitProjection(points: readonly LatLng[], width: number, height: number, padding: number): Projection {
  const lats = points.map((p) => p.lat);
  const lngs = points.map((p) => p.lng);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const meanLat = (minLat + maxLat) / 2;
  const cos = Math.cos((meanLat * Math.PI) / 180);
  const spanX = (maxLng - minLng) * cos;
  const spanY = maxLat - minLat;
  const room = { w: width - 2 * padding, h: height - 2 * padding };
  // Drawing units per degree of latitude. A route that did not move gets 1 unit per ~2 m.
  const scale =
    spanX === 0 && spanY === 0
      ? METERS_PER_DEGREE / 2
      : Math.min(spanX === 0 ? Infinity : room.w / spanX, spanY === 0 ? Infinity : room.h / spanY);
  const offsetX = (width - spanX * scale) / 2;
  const offsetY = (height - spanY * scale) / 2;
  const project = (p: LatLng): Projected => ({
    x: offsetX + (p.lng - minLng) * cos * scale,
    y: offsetY + (maxLat - p.lat) * scale,
  });
  const metersPerUnit = METERS_PER_DEGREE / scale;
  const maxBar = width * 0.3;
  const meters = [...NICE_METERS].reverse().find((m) => m / metersPerUnit <= maxBar) ?? NICE_METERS[0];
  return {
    project,
    scaleBar: { px: meters / metersPerUnit, meters, label: meters >= 1000 ? `${meters / 1000} km` : `${meters} m` },
  };
}

/** The SVG path of a polyline in drawing units; empty for no points. */
export function pathOf(points: readonly Projected[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
}

/** Great-circle distance in metres between two coordinates. */
export function haversineM(a: LatLng, b: LatLng): number {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

/** Walked distance, counting only the legs between two readings of 100 m accuracy or better (as the retention does). */
export function walkedMeters(points: readonly (LatLng & { readonly accuracyM: number })[]): number {
  let total = 0;
  for (let i = 1; i < points.length; i += 1) {
    if (points[i - 1].accuracyM <= 100 && points[i].accuracyM <= 100) {
      total += haversineM(points[i - 1], points[i]);
    }
  }
  return total;
}

/** 3180.5 -> "3.18 km"; 640 -> "640 m". Same decimal separator as the kilos (formatKg), so a summary never mixes both. */
export function formatDistance(meters: number): string {
  return meters >= 1000 ? `${(meters / 1000).toFixed(2)} km` : `${Math.round(meters)} m`;
}
