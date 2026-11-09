package pe.esgtrazabilidad.reciclador

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.session.LogoutPlan
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.PendingUploader
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.session.SyncOutcome
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * CA-20, CA-21 and CA-24 with sentinel records on the phone's REAL Keystore and real files, and a clock the test
 * moves. Each test uses its own folder and its own key aliases, so nothing here touches the app's own session.
 */
class LifecycleInstrumentedTest {
    private class Clock(var now: Long = 1_800_000_000_000L) : TimeSource {
        override fun nowMillis() = now
    }

    /** A server that always agrees, issuing tokens that carry the consent, with one switch to revoke. */
    private class StubApi : AuthApi {
        var revoked = false
        var counter = 0
        private fun token(): String {
            val enc = Base64.getUrlEncoder().withoutPadding()
            val payload = JSONObject().put("sub", "x").put("cv", 1).put("lc", true).toString()
            return listOf("""{"alg":"none"}""", payload, "s").joinToString(".") { enc.encodeToString(it.toByteArray()) }
        }

        private fun issue(): Tokens {
            if (revoked) throw ApiException(401, "AUTH-017")
            return Tokens(token(), "rt-${++counter}-${UUID.randomUUID()}", 900)
        }

        override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String) = issue()
        override suspend fun refresh(refreshToken: String) = issue()
        override suspend fun consent(accessToken: String) = Consent(1, "t", "l", 90, 12)
        override suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean) = Unit
        override suspend fun logout(accessToken: String, refreshToken: String) = Unit
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = UUID.randomUUID().toString().take(8)
    private val sessionVault = KeystoreVault("test-session-$tag")
    private val pendingVault = KeystoreVault("test-pending-$tag")
    private val clock = Clock()
    private val api = StubApi()
    private var uploader: PendingUploader = NothingUploadable
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "lifecycle-$tag").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        sessionVault.destroyKey()
        pendingVault.destroyKey()
    }

    private fun pending() = PendingStore(dir, pendingVault)
    private fun route() = RouteCache(dir, sessionVault, clock)
    private fun manager() = SessionManager(
        api, SessionStore(dir, sessionVault), route(), pending(), { t, r -> uploader.upload(t, r) }, clock, "Honor de prueba", dir,
    )

    private fun record(n: Int) = PendingRecord("c-$n", clock.now - n, "9.5", "TIPO-CENTINELA-$n", "vecino-opaco-$n")

    private suspend fun signedIn(pendingCount: Int): SessionManager {
        val session = manager()
        session.start()
        session.activate("CODIGO12345")
        route().save("""{"v":"VECINO-CENTINELA CALLE-CENTINELA DISTRITO-CENTINELA"}""")
        repeat(pendingCount) { pending().add(record(it + 1)) }
        return session
    }

    private fun allText() = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }

    @Test
    fun branchA_nothingPendingWipesEverything() = runBlocking {
        val session = signedIn(0)

        assertEquals(LogoutPlan.Done, session.requestLogout())

        assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        assertFalse(sessionVault.hasKey())
        assertTrue(session.state.value is SessionState.NeedsCode)
    }

    @Test
    fun branchB_cancelThenSyncConfirmedThenWipe() = runBlocking {
        val session = signedIn(3)

        assertEquals(LogoutPlan.NeedsDecision(3), session.requestLogout())
        assertEquals("cancel: nothing deleted", 3, pending().count())
        assertTrue(File(dir, SessionStore.FILE_NAME).exists())

        uploader = PendingUploader { _, records -> records.map { it.id }.toSet() }
        assertEquals(SyncOutcome.Done, session.logoutAfterSync())

        assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        assertFalse(pendingVault.hasKey())
    }

    @Test
    fun branchB_discardOnlyWithTheRepeatedCount() = runBlocking {
        val session = signedIn(3)

        val ticket = session.beginDiscard()
        runCatching { session.confirmDiscard(ticket, shownCount = 1) }
        assertEquals(3, pending().count())

        session.confirmDiscard(ticket, shownCount = 3)

        assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        assertEquals(0, pending().count())
    }

    @Test
    fun revocationKeepsThePendingReadableWithTheirOwnKey() = runBlocking {
        val session = signedIn(3)
        val before = pending().list()
        clock.now += 16 * 60 * 1000 // the access token expired
        api.revoked = true

        val error = runCatching { session.accessToken() }.exceptionOrNull() as ApiException

        assertEquals("AUTH-017", error.code)
        assertEquals(SessionState.Revoked(3, "AUTH-017"), session.state.value)
        assertFalse(File(dir, SessionStore.FILE_NAME).exists())
        assertFalse(File(dir, RouteCache.FILE_NAME).exists())
        assertFalse(sessionVault.hasKey())
        assertTrue(pendingVault.hasKey())
        assertEquals(before, pending().list())
        val text = allText()
        assertFalse(text.contains("VECINO-CENTINELA"))
        assertFalse(text.contains("TIPO-CENTINELA"))
    }

    @Test
    fun theRouteExpiresAtSevenDaysAndOnlyTheRoute() = runBlocking {
        val session = signedIn(3)
        val before = pending().list()

        clock.now += (7L * 24 * 3600 - 3600) * 1000
        assertNotNull("6 days 23 hours: still cached", route().load())

        clock.now += 3600 * 1000
        manager().start()

        assertFalse("the route is gone", File(dir, RouteCache.FILE_NAME).exists())
        assertEquals("the pending records are intact", before, pending().list())
        assertTrue("the session is untouched", File(dir, SessionStore.FILE_NAME).exists())
        assertTrue(session.state.value is SessionState.Active)
    }
}
