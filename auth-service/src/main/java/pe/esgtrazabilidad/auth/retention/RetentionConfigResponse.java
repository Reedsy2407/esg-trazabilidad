package pe.esgtrazabilidad.auth.retention;

/** What is stored after the call; {@code consentVersion} shows whether the change asked everyone again. */
record RetentionConfigResponse(int gpsDays, int photoMonths, int consentVersion) {

    static RetentionConfigResponse from(RetentionConfig config) {
        return new RetentionConfigResponse(config.gpsDays(), config.photoMonths(), config.consentVersion());
    }
}
