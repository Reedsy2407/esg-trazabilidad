package pe.esgtrazabilidad.reporting.sigersolmunicipal.adapter;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;

/** No provider configured: every question is unavailable, so screens say "no disponible". */
class UnavailableSigersolMunicipal implements SigersolMunicipalStats {

    @Override
    public Answer fetch(int year, String ubigeo) {
        return new Answer.Unavailable();
    }
}
