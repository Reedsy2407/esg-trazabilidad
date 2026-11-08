package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Duration;

/** The lifetimes of a recycler's session (A.3). */
final class SessionPolicy {

    /** A device unused this long can no longer refresh (CA-10). */
    static final Duration INACTIVITY = Duration.ofDays(30);

    /** Hard stop counted from the activation, however active the device is (CA-10). */
    static final Duration ABSOLUTE = Duration.ofDays(90);

    /** After a rotation the previous token still works this long (CA-8). */
    static final Duration GRACE = Duration.ofMinutes(10);

    private SessionPolicy() {
    }
}
