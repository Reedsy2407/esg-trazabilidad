package pe.esgtrazabilidad.reporting.sunat;

/**
 * What the SUNAT lookup answered. {@code Unavailable} covers every way the
 * provider can fail to give an answer (timeout, 5xx, bad token, exhausted quota,
 * malformed body): the caller cannot tell "SUNAT has no such RUC" from those, so
 * only {@code NotFound} means the provider answered that the RUC does not exist.
 */
public sealed interface RucLookupResult {

    record Found(String legalName, String estado, String condicion, String ubigeo) implements RucLookupResult {

        public boolean isDeregistered() {
            return estado != null && estado.trim().toUpperCase().startsWith("BAJA DEFINITIVA");
        }

        public boolean isNotLocated() {
            return condicion != null && condicion.trim().equalsIgnoreCase("NO HABIDO");
        }
    }

    record NotFound() implements RucLookupResult {
    }

    record Unavailable() implements RucLookupResult {
    }
}
