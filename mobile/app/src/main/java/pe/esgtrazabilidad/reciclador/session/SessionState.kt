package pe.esgtrazabilidad.reciclador.session

import pe.esgtrazabilidad.reciclador.net.Consent

sealed interface SessionState {
    /** Reading the stored session; nothing to show yet. */
    data object Starting : SessionState

    /** No session: the recycler types the code the staff gave in person. [notice] says why, if it matters. */
    data class NeedsCode(val notice: String? = null) : SessionState

    /** Activated, but the current consent version has not been accepted (first time, or the periods changed). */
    data class NeedsConsent(val consent: Consent) : SessionState

    /**
     * Activated (the code is spent and the session is stored) but the consent text could not be fetched, for lack
     * of network: asking for a new code would be wrong, so the app offers to retry the consent.
     */
    data class ConsentUnavailable(val reason: String) : SessionState

    /**
     * The session ended without the recycler's choice (a revocation by the staff, an inactive account) while
     * [pendingCount] records are still on the phone. Blocking: they are kept, cannot be sent without a session, and
     * are never discarded automatically. [code] is the AUTH code that ended it, null when only restarting the app.
     */
    data class Revoked(val pendingCount: Int, val code: String?) : SessionState

    /** A working session. [offline]: the token could not be refreshed this time; nothing was dropped. */
    data class Active(val offline: Boolean = false, val locationAccepted: Boolean = false) : SessionState
}
