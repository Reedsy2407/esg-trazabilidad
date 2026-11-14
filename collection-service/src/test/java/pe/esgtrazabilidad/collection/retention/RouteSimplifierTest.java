package pe.esgtrazabilidad.collection.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.collection.retention.RouteSimplifier.Point;

class RouteSimplifierTest {

    @Test
    void oneDegreeOfLatitudeIsAbout111Kilometres() {
        double metres = RouteSimplifier.haversineMeters(new Point(-12.0, -77.0), new Point(-13.0, -77.0));

        assertThat(metres).isCloseTo(111_195, within(300.0));
    }

    @Test
    void theDistanceAddsUpTheLegsInTheOrderGiven() {
        List<Point> route = List.of(new Point(-12.000, -77.0), new Point(-12.001, -77.0), new Point(-12.001, -77.001));

        double expected = RouteSimplifier.haversineMeters(route.get(0), route.get(1)) + RouteSimplifier.haversineMeters(route.get(1), route.get(2));

        assertThat(RouteSimplifier.distanceMeters(route)).isEqualTo(expected);
        assertThat(RouteSimplifier.distanceMeters(List.of(new Point(-12.0, -77.0)))).isZero();
        assertThat(RouteSimplifier.distanceMeters(List.of())).isZero();
    }

    @Test
    void aStraightLineCollapsesToItsEnds() {
        List<Point> line = new ArrayList<>();
        for (int i = 0; i <= 50; i++) {
            line.add(new Point(-12.0 - i * 0.0001, -77.0));
        }

        List<Point> simplified = RouteSimplifier.simplify(line, 25, 200);

        assertThat(simplified).containsExactly(line.get(0), line.get(line.size() - 1));
    }

    @Test
    void aCornerFarEnoughFromTheChordIsKept() {
        // About 111 m east of the chord's end: well over the 25 m tolerance.
        List<Point> lShape = List.of(new Point(-12.000, -77.000), new Point(-12.000, -77.001), new Point(-12.001, -77.001));

        assertThat(RouteSimplifier.simplify(lShape, 25, 200)).containsExactlyElementsOf(lShape);
    }

    @Test
    void aWigglWithinTheToleranceIsDropped() {
        // 5 m of sideways noise on a 1 km walk.
        List<Point> walk = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            walk.add(new Point(-12.0 - i * 0.00009, -77.0 + (i % 2 == 0 ? 0 : 0.00004)));
        }

        List<Point> simplified = RouteSimplifier.simplify(walk, 25, 200);

        assertThat(simplified).hasSize(2);
    }

    @Test
    void theResultNeverExceedsTheCapAndKeepsTheFirstAndTheLastPoint() {
        List<Point> zigzag = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            zigzag.add(new Point(-12.0 - i * 0.0002, -77.0 + (i % 2 == 0 ? 0 : 0.002)));
        }

        List<Point> simplified = RouteSimplifier.simplify(zigzag, 25, 200);

        assertThat(simplified.size()).isLessThanOrEqualTo(200).isGreaterThanOrEqualTo(2);
        assertThat(simplified.get(0)).isEqualTo(zigzag.get(0));
        assertThat(simplified.get(simplified.size() - 1)).isEqualTo(zigzag.get(zigzag.size() - 1));
    }

    @Test
    void zeroOneOrTwoPointsComeBackAsTheyAre() {
        assertThat(RouteSimplifier.simplify(List.of(), 25, 200)).isEmpty();
        assertThat(RouteSimplifier.simplify(List.of(new Point(1, 1)), 25, 200)).hasSize(1);
        assertThat(RouteSimplifier.simplify(List.of(new Point(1, 1), new Point(2, 2)), 25, 200)).hasSize(2);
    }
}
