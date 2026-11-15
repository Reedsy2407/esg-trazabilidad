package pe.esgtrazabilidad.reciclador

import android.app.Application
import android.os.Build
import java.io.File
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.field.RoomFieldStore
import pe.esgtrazabilidad.reciclador.field.WorkdayManager
import pe.esgtrazabilidad.reciclador.net.HttpAuthApi
import pe.esgtrazabilidad.reciclador.net.HttpCollectionApi
import pe.esgtrazabilidad.reciclador.route.RouteRepository
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

class RecyclerApp : Application() {

    /** Everything the app keeps on disk lives in one folder, so a wipe cannot miss a file. */
    val appDir: File by lazy { File(filesDir, "reciclador").apply { mkdirs() } }

    private val sessionVault by lazy { KeystoreVault(SESSION_KEY_ALIAS) }

    private val routeCache by lazy { RouteCache(appDir, sessionVault, TimeSource.System) }

    val session: SessionManager by lazy {
        SessionManager(
            api = HttpAuthApi(BuildConfig.AUTH_BASE_URL),
            sessionStore = SessionStore(appDir, sessionVault),
            routeCache = routeCache,
            // Its own key: wiping the session (logout, revocation) never touches what has not been uploaded.
            pending = PendingStore(appDir, KeystoreVault(PENDING_KEY_ALIAS)),
            uploader = NothingUploadable,
            time = TimeSource.System,
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".take(MAX_LABEL),
            appDir = appDir,
        )
    }

    private val collectionApi by lazy { HttpCollectionApi(BuildConfig.COLLECTION_BASE_URL) }

    val route: RouteRepository by lazy { RouteRepository(collectionApi, session, routeCache, TimeSource.System) }

    /** The workday and its GPS points. Room lives in the app's private storage, outside [appDir]: a logout keeps it. */
    val workdays: WorkdayManager by lazy { WorkdayManager(RoomFieldStore(this), collectionApi, session, TimeSource.System) }

    companion object {
        const val SESSION_KEY_ALIAS = "pe.esgtrazabilidad.reciclador.session"
        const val PENDING_KEY_ALIAS = "pe.esgtrazabilidad.reciclador.pending"
        private const val MAX_LABEL = 80
    }
}
