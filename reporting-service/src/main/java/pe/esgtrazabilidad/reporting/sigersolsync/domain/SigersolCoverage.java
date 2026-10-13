package pe.esgtrazabilidad.reporting.sigersolsync.domain;

/**
 * Whether a company's period is covered by a SIGERSOL figure staff recorded. UNVERIFIED reads as
 * "No verificado": no record covers the period, so nothing is claimed about compliance.
 */
public enum SigersolCoverage {
    RECORDED,
    UNVERIFIED
}
