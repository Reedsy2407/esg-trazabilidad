package pe.esgtrazabilidad.reporting.trackedcompany.domain;

/** Optional contact person of a tracked company; every part may be absent. */
public record CompanyContact(String name, String email, String phone) {

    public static final CompanyContact NONE = new CompanyContact(null, null, null);
}
