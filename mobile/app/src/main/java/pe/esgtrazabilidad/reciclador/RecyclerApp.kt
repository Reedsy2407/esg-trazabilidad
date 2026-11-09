package pe.esgtrazabilidad.reciclador

import android.app.Application
import android.os.Build
import java.io.File
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.net.HttpAuthApi
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

class RecyclerApp : Application() {

    /** Everything the app keeps on disk lives in one folder, so a wipe cannot miss a file. */
    val appDir: File by lazy { File(filesDir, "reciclador").apply { mkdirs() } }

    val session: SessionManager by lazy {
        val sessionVault = KeystoreVault(SESSION_KEY_ALIAS)
        SessionManager(
            api = HttpAuthApi(BuildConfig.AUTH_BASE_URL),
            sessionStore = SessionStore(appDir, sessionVault),
            routeCache = RouteCache(appDir, sessionVault, TimeSource.System),
            // Its own key: wiping the session (logout, revocation) never touches what has not been uploaded.
            pending = PendingStore(appDir, KeystoreVault(PENDING_KEY_ALIAS)),
            uploader = NothingUploadable,
            time = TimeSource.System,
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".take(MAX_LABEL),
            appDir = appDir,
        )
    }

    companion object {
        const val SESSION_KEY_ALIAS = "pe.esgtrazabilidad.reciclador.session"
        const val PENDING_KEY_ALIAS = "pe.esgtrazabilidad.reciclador.pending"
        private const val MAX_LABEL = 80
    }
}
