package pe.esgtrazabilidad.reporting.sigersolmunicipal;

/**
 * What a screen or a certificate says about a district: the counts and the year they belong to, or
 * that they are not available. Nothing depends on this being available.
 *
 * @param stale the counts come from a snapshot older than the refresh interval because SIGERSOL
 *     did not answer; the year is still the snapshot's own.
 */
public record DistrictContext(
        String ubigeo, Status status, Integer year, Integer associationCount, Integer recyclerCount, boolean stale) {

    public enum Status {
        AVAILABLE,
        UNAVAILABLE
    }

    public static DistrictContext unavailable(String ubigeo) {
        return new DistrictContext(ubigeo, Status.UNAVAILABLE, null, null, null, false);
    }

    public static DistrictContext of(DistrictStats stats, boolean stale) {
        return new DistrictContext(
                stats.ubigeo(), Status.AVAILABLE, stats.year(), stats.associationCount(), stats.recyclerCount(), stale);
    }
}
