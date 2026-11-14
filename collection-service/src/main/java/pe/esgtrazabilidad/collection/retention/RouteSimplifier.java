package pe.esgtrazabilidad.collection.retention;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Geometry of the route summary (spec B2, CA-39): distance over the legs between points, and a simplified
 * polyline (Douglas-Peucker). Pure functions on (lat, lng) pairs, in metres on a local flat projection:
 * exact enough over the few kilometres of a collection route, and no dependency to maintain.
 */
final class RouteSimplifier {

    record Point(double lat, double lng) {
    }

    private static final double EARTH_RADIUS_M = 6_371_008.8;
    private static final double METRES_PER_DEGREE_LAT = 110_540;
    private static final double METRES_PER_DEGREE_LNG_AT_EQUATOR = 111_320;

    private RouteSimplifier() {
    }

    /** Great-circle distance between two points, in metres. */
    static double haversineMeters(Point a, Point b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Sum of the legs between consecutive points, in the order given. */
    static double distanceMeters(List<Point> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }

    /**
     * The route reduced to at most {@code maxPoints} points that stay within {@code toleranceMeters} of the
     * original (the tolerance doubles until the cap is met). The first and the last point are always kept.
     */
    static List<Point> simplify(List<Point> points, double toleranceMeters, int maxPoints) {
        if (points.size() <= 2) {
            return List.copyOf(points);
        }
        double tolerance = toleranceMeters;
        while (true) {
            List<Point> result = douglasPeucker(points, tolerance);
            if (result.size() <= Math.max(2, maxPoints)) {
                return result;
            }
            tolerance *= 2;
        }
    }

    private static List<Point> douglasPeucker(List<Point> points, double toleranceMeters) {
        int n = points.size();
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {0, n - 1});
        while (!pending.isEmpty()) {
            int[] range = pending.pop();
            int start = range[0];
            int end = range[1];
            double farthest = -1;
            int index = -1;
            for (int i = start + 1; i < end; i++) {
                double d = perpendicularMeters(points.get(i), points.get(start), points.get(end));
                if (d > farthest) {
                    farthest = d;
                    index = i;
                }
            }
            if (index != -1 && farthest > toleranceMeters) {
                keep[index] = true;
                pending.push(new int[] {start, index});
                pending.push(new int[] {index, end});
            }
        }
        List<Point> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    /** Distance from {@code p} to the segment a-b, on a flat projection centred on a. */
    private static double perpendicularMeters(Point p, Point a, Point b) {
        double metresPerDegreeLng = METRES_PER_DEGREE_LNG_AT_EQUATOR * Math.cos(Math.toRadians(a.lat()));
        double px = (p.lng() - a.lng()) * metresPerDegreeLng;
        double py = (p.lat() - a.lat()) * METRES_PER_DEGREE_LAT;
        double bx = (b.lng() - a.lng()) * metresPerDegreeLng;
        double by = (b.lat() - a.lat()) * METRES_PER_DEGREE_LAT;
        double lengthSquared = bx * bx + by * by;
        if (lengthSquared == 0) {
            return Math.hypot(px, py);
        }
        double t = Math.max(0, Math.min(1, (px * bx + py * by) / lengthSquared));
        return Math.hypot(px - t * bx, py - t * by);
    }
}
