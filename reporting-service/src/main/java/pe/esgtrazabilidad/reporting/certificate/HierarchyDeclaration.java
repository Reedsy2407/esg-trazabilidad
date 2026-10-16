package pe.esgtrazabilidad.reporting.certificate;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/**
 * Where a certificate's hierarchy-compliance figure came from: the declaration of the association's
 * operator that covers the certificate's period, with its origin, who registered it and the optional
 * proof it points to. It is a figure somebody declared, never one SIGERSOL or this platform confirmed,
 * and {@link #note()} says so in every place the figure is printed.
 *
 * <p>Read from the SIGERSOL record that covers the period rather than copied into the certificate:
 * those records are never edited or deleted, and two of them can't overlap, so the one covering a
 * period when a certificate was issued is the one that covers it now. All three parts are null for a
 * record made before the origin was kept.
 */
public record HierarchyDeclaration(SigersolOrigin origin, String registeredByEmail, String reference) {

    /** No declaration at hand (a record from before origins existed, or none found): origin not recorded. */
    public static final HierarchyDeclaration NOT_RECORDED = new HierarchyDeclaration(null, null, null);

    /** The label of the figure on screen, in the PDF and in the CSV. */
    public static final String LABEL = "Cumplimiento de jerarquía (declarado)";

    /**
     * «El cumplimiento de jerarquía es un dato declarado por el operador de la asociación (origen: …,
     * registrado por …), respaldado por el comprobante «…»; no es una cifra oficial de SIGERSOL ni la
     * verifica esta plataforma.» The parts that are not known are left out.
     */
    public String note() {
        String originText =
                switch (origin) {
                    case null -> "no registrado";
                    case DECLARED_BY_OPERATOR -> "declarado por el operador";
                    case REFERENCED_PROOF -> "con comprobante referenciado";
                };
        String who = registeredByEmail == null || registeredByEmail.isBlank() ? "" : ", registrado por " + registeredByEmail;
        String proof = reference == null || reference.isBlank() ? "" : ", respaldado por el comprobante «" + reference + "»";
        return "El cumplimiento de jerarquía es un dato declarado por el operador de la asociación (origen: "
                + originText
                + who
                + ")"
                + proof
                + "; no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.";
    }
}
