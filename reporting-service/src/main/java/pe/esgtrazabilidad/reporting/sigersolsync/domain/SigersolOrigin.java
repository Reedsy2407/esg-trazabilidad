package pe.esgtrazabilidad.reporting.sigersolsync.domain;

/**
 * Where the figure staff typed came from (D1). Nothing here is verified against SIGERSOL: it is
 * the staff member's own statement of how they got the number.
 */
public enum SigersolOrigin {
    /** The operator declares the figure from their own reading of SIGERSOL. */
    DECLARED_BY_OPERATOR,
    /** The operator holds a proof (a screenshot, a constancia) and gives its reference. */
    REFERENCED_PROOF
}
