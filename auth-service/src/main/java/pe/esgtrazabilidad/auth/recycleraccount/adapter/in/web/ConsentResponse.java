package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerConsentUseCase.Consent;

/** A.2: {version, text, locationText, retention:{gpsDays, photoMonths}}. */
record ConsentResponse(int version, String text, String locationText, Retention retention) {

    record Retention(int gpsDays, int photoMonths) {
    }

    static ConsentResponse from(Consent consent) {
        return new ConsentResponse(
                consent.version(),
                consent.text(),
                consent.locationText(),
                new Retention(consent.gpsDays(), consent.photoMonths()));
    }
}
