package pe.esgtrazabilidad.reporting.sunat;

import pe.esgtrazabilidad.kernel.ruc.Ruc;

/** Port to the provider that answers SUNAT's RUC register. One remote call per invocation, no caching. */
public interface RucLookup {

    RucLookupResult lookup(Ruc ruc);
}
