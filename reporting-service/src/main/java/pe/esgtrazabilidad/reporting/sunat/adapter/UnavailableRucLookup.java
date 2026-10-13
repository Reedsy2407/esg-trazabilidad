package pe.esgtrazabilidad.reporting.sunat.adapter;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookup;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

/** {@code RUC_LOOKUP_PROVIDER=none}: every lookup "does not answer". Development and tests only. */
public class UnavailableRucLookup implements RucLookup {

    @Override
    public RucLookupResult lookup(Ruc ruc) {
        return new RucLookupResult.Unavailable();
    }
}
