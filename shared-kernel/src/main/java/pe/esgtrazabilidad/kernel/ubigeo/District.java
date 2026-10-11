package pe.esgtrazabilidad.kernel.ubigeo;

/**
 * One district of the closed list: its INEI ubigeo (6 digits), the INEI name
 * exactly as the source spells it (upper case, no accents), and the name to
 * show on screens and PDFs (same name with Spanish accents and case).
 */
public record District(String ubigeo, Area area, String ineiName, String displayName) {

    public enum Area {
        LIMA_METROPOLITANA,
        CALLAO
    }
}
