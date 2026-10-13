package pe.esgtrazabilidad.reporting.sigersolmunicipal;

/**
 * Read-only port to SIGERSOL Municipal "Acceso Libre". It only exposes the counts of
 * associations and recyclers per district and year: the tonnes are not used (the API does not
 * declare their unit and its figures contradict each other, see the plausibility ADR).
 *
 * <p>The district is the INEI ubigeo (6 digits): SIGERSOL's province and district codes are the
 * ubigeo's suffix. An implementation never throws: any failure is {@link Answer.Unavailable}.
 */
public interface SigersolMunicipalStats {

    Answer fetch(int year, String ubigeo);

    sealed interface Answer {

        /** The year has rows for the district. */
        record Found(int associationCount, int recyclerCount) implements Answer {
        }

        /** SIGERSOL answered, but has no rows for that district and year. */
        record NoData() implements Answer {
        }

        /** No answer: timeout, error, unreadable body, or no provider configured. */
        record Unavailable() implements Answer {
        }
    }
}
