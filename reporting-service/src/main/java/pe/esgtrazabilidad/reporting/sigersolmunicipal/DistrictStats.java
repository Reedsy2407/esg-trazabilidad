package pe.esgtrazabilidad.reporting.sigersolmunicipal;

import java.time.Instant;

/** One district-year of SIGERSOL Municipal's aggregated counts, as last fetched. */
public record DistrictStats(int year, String ubigeo, int associationCount, int recyclerCount, Instant fetchedAt) {
}
