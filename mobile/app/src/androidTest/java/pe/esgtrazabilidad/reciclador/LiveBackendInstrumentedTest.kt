package pe.esgtrazabilidad.reciclador

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.HttpAuthApi
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.session.LogoutPlan
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * The real flow against the LOCAL backend (never production), reached through `adb reverse tcp:8084 tcp:8084`.
 * Skipped unless run with `-e step <name>`; the activation code is passed with `-e code <code>`. Each step is its
 * own instrumentation run on the app's real files and real Keystore keys, so "reopen" really is a fresh process
 * reading what an earlier step left on the disk, and the staff's revocation between steps is done through the API
 * by the person running them (see tasks/evidencia-m1).
 */
class LiveBackendInstrumentedTest {
    private val args = InstrumentationRegistry.getArguments()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RecyclerApp

    /** Remembers every token the server issued, only in memory, to prove none reached the disk in clear. */
    private class RecordingApi(private val inner: AuthApi) : AuthApi by inner {
        val refreshTokens = mutableListOf<String>()
        val accessTokens = mutableListOf<String>()

        override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String): Tokens =
            inner.activate(code, deviceLabel, deviceInstallId).also { remember(it) }

        override suspend fun refresh(refreshToken: String): Tokens = inner.refresh(refreshToken).also { remember(it) }

        private fun remember(tokens: Tokens) {
            refreshTokens += tokens.refreshToken
            accessTokens += tokens.accessToken
        }
    }

    private val sessionVault = KeystoreVault(RecyclerApp.SESSION_KEY_ALIAS)
    private val pendingVault = KeystoreVault(RecyclerApp.PENDING_KEY_ALIAS)
    private fun pending() = PendingStore(app.appDir, pendingVault)
    private fun route() = RouteCache(app.appDir, sessionVault, TimeSource.System)

    private fun manager(api: AuthApi) = SessionManager(
        api = api,
        sessionStore = SessionStore(app.appDir, sessionVault),
        routeCache = route(),
        pending = pending(),
        uploader = NothingUploadable,
        time = TimeSource.System,
        deviceLabel = "Honor X7b (prueba instrumentada)",
        appDir = app.appDir,
    )

    private fun step(name: String) = assumeTrue("run with -e step $name", args.getString("step") == name)

    private fun filesText(): String =
        app.filesDir.walkTopDown().filter { it.isFile }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }

    private fun sentinel(n: Int) = PendingRecord(
        id = "centinela-$n",
        recordedAtMillis = System.currentTimeMillis() - n * 60_000L,
        weightKg = "12.5",
        wasteType = "TIPO-CENTINELA-$n",
        neighborId = "vecino-opaco-$n",
    )

    @Test
    fun activate() {
        step("activate")
        val code = args.getString("code")
        assumeTrue("needs -e code <code>", code != null)
        val api = RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL))
        val session = manager(api)

        runBlocking {
            session.start()
            assertTrue(session.state.value is SessionState.NeedsCode)
            session.activate(code!!)
            // A first activation asks for the consent; the same account re-activated after a revocation already
            // accepted the version in force, so its token carries `cv` and the app goes straight in.
            val needsConsent = session.state.value as? SessionState.NeedsConsent
            if (needsConsent != null) {
                assertEquals(90, needsConsent.consent.gpsDays)
                session.acceptConsent(locationAccepted = true)
            }
        }

        assertEquals(SessionState.Active(offline = false, locationAccepted = true), session.state.value)
        val onDisk = filesText()
        api.refreshTokens.forEach { assertFalse("a refresh token is on disk in clear", onDisk.contains(it)) }
        api.accessTokens.forEach { assertFalse("an access token is on disk", onDisk.contains(it)) }
        assertTrue(File(app.appDir, SessionStore.FILE_NAME).exists())
    }

    @Test
    fun reopen() {
        step("reopen")
        val session = manager(RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL)))

        runBlocking { session.start() }

        assertEquals(SessionState.Active(offline = false, locationAccepted = true), session.state.value)
    }

    /** CA-16: "Cerrar sesión" with nothing pending, and the server really revoked this device. */
    @Test
    fun logoutWithNothingPending() {
        step("logout")
        val api = RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL))
        val session = manager(api)

        runBlocking {
            session.start()
            assertTrue(session.state.value is SessionState.Active)
            val currentRefresh = api.refreshTokens.last()
            assertEquals(LogoutPlan.Done, session.requestLogout())
            assertTrue(session.state.value is SessionState.NeedsCode)
            // The device is revoked on the server: its token no longer refreshes.
            val error = runCatching { HttpAuthApi(BuildConfig.AUTH_BASE_URL).refresh(currentRefresh) }.exceptionOrNull() as ApiException
            assertEquals("AUTH-017", error.code)
        }

        assertEquals("no file of the session remains", emptyList<String>(), app.appDir.list().orEmpty().toList())
        assertFalse(sessionVault.hasKey())
        assertFalse(pendingVault.hasKey())
    }

    /** CA-21, step 1: a session with a cached route and 3 sentinel records, before the staff revokes the device. */
    @Test
    fun seedPendingAndRoute() {
        step("seed")
        runBlocking { manager(RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL))).start() }
        route().save("""{"vecinos":[{"nombre":"VECINO-CENTINELA","direccion":"CALLE-CENTINELA 123","distrito":"DISTRITO-CENTINELA"}]}""")
        (1..3).forEach { pending().add(sentinel(it)) }
        assertEquals(3, pending().count())
        assertTrue(File(app.appDir, RouteCache.FILE_NAME).exists())
        assertTrue(filesText().isNotEmpty())
    }

    /** CA-21, step 2 (after the staff revoked the device through the API): the app finds out on its next call. */
    @Test
    fun afterRevocation() {
        step("after-revoke")
        val session = manager(RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL)))

        runBlocking { session.start() }

        assertEquals(SessionState.Revoked(pendingCount = 3, code = "AUTH-017"), session.state.value)
        assertFalse("credentials gone", File(app.appDir, SessionStore.FILE_NAME).exists())
        assertFalse("route gone", File(app.appDir, RouteCache.FILE_NAME).exists())
        assertFalse("session key gone", sessionVault.hasKey())
        assertTrue("pending key kept", pendingVault.hasKey())
        val text = filesText()
        assertFalse(text.contains("VECINO-CENTINELA"))
        assertFalse(text.contains("CALLE-CENTINELA"))
        assertEquals("the sentinels are still readable", listOf("centinela-1", "centinela-2", "centinela-3"), pending().list().map { it.id })
    }

    /** CA-21 / CA-26 (mechanism): a new code for the same account on the same phone; the pending records stay. */
    @Test
    fun reactivateKeepingPending() {
        step("reactivate")
        val code = args.getString("code")
        assumeTrue("needs -e code <code>", code != null)
        val session = manager(RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL)))

        runBlocking {
            session.start()
            assertTrue(session.state.value is SessionState.Revoked)
            session.activate(code!!)
        }

        assertTrue(session.state.value is SessionState.Active)
        assertEquals(3, pending().count())
    }

    /** CA-20 (b-cancelar, b-sincronizar sin servidor, b-descartar): nothing is lost without the double confirmation. */
    @Test
    fun logoutWithPendingBranches() {
        step("logout-pending")
        val session = manager(RecordingApi(HttpAuthApi(BuildConfig.AUTH_BASE_URL)))

        runBlocking {
            session.start()
            assertEquals(LogoutPlan.NeedsDecision(3), session.requestLogout())
            assertEquals("cancel deletes nothing", 3, pending().count())
            assertTrue(File(app.appDir, SessionStore.FILE_NAME).exists())

            // Module 1 has no upload route: "Sincronizar ahora" confirms nothing and closes nothing.
            val remaining = session.logoutAfterSync()
            assertEquals(3, (remaining as pe.esgtrazabilidad.reciclador.session.SyncOutcome.Remaining).count)
            assertEquals(3, pending().count())

            val ticket = session.beginDiscard()
            runCatching { session.confirmDiscard(ticket, shownCount = 2) }
            assertEquals("a wrong confirmation deletes nothing", 3, pending().count())

            session.confirmDiscard(ticket, shownCount = 3)
        }

        assertEquals(SessionState.NeedsCode(), runBlocking { session.state.value })
        assertEquals(emptyList<String>(), app.appDir.list().orEmpty().toList())
        assertFalse(pendingVault.hasKey())
    }
}
