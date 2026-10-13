package pe.esgtrazabilidad.reporting.certificate.domain;

import java.math.BigDecimal;

import pe.esgtrazabilidad.kernel.ubigeo.District;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictContext;

/**
 * One district of a certificate: the kilos traced there and what SIGERSOL Municipal says about the
 * district, as aggregated public counts (never a claim about the company). Frozen when the
 * certificate is issued. A line is AVAILABLE with the year and the counts, UNAVAILABLE when SIGERSOL
 * had nothing, or UNKNOWN_DISTRICT for the kilos whose records carry no district (no ubigeo).
 */
public record DistrictLine(
        String districtUbigeo,
        String districtName,
        BigDecimal kilos,
        Status status,
        Integer year,
        Integer associationCount,
        Integer recyclerCount) {

    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        UNKNOWN_DISTRICT
    }

    public static final String UNKNOWN_NAME = "Distrito desconocido";

    public static DistrictLine of(String ubigeo, BigDecimal kilos, DistrictContext context) {
        String name = UbigeoCatalog.find(ubigeo).map(District::displayName).orElse(ubigeo);
        if (context.status() == DistrictContext.Status.AVAILABLE) {
            return new DistrictLine(
                    ubigeo, name, kilos, Status.AVAILABLE, context.year(), context.associationCount(), context.recyclerCount());
        }
        return new DistrictLine(ubigeo, name, kilos, Status.UNAVAILABLE, null, null, null);
    }

    public static DistrictLine unknown(BigDecimal kilos) {
        return new DistrictLine(null, UNKNOWN_NAME, kilos, Status.UNKNOWN_DISTRICT, null, null, null);
    }
}
