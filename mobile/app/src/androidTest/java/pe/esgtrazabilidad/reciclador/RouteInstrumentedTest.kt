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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.CollectionApi
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.GpsPointDto
import pe.esgtrazabilidad.reciclador.net.OpenedWorkday
import pe.esgtrazabilidad.reciclador.net.PointsAck
import pe.esgtrazabilidad.reciclador.net.RouteNeighbor
import pe.esgtrazabilidad.reciclador.net.RouteStop
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.route.RouteRepository
import pe.esgtrazabilidad.reciclador.route.RouteState
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * CA-42 and CA-24 on the phone's REAL Keystore and files, with a clock the test moves: the route is downloaded and
 * sealed; without network the saved one shows with its date; at 7 days only the route is deleted.
 */
class RouteInstrumentedTest {
    private class Clock(var now: Long = 1_800_000_000_000L) : TimeSource {
        override fun nowMillis() = now
    }

    private class StubAuth : AuthApi {
        private fun token(): String {
            val enc = Base64.getUrlEncoder().withoutPadding()
            val payload = JSONObject().put("sub", "x").put("cv", 1).put("lc", true).toString()
            return listOf("""{"alg":"none"}""", payload, "s").joinToString(".") { enc.encodeToString(it.toByteArray()) }
        }

        override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String) = Tokens(token(), "rt-${UUID.randomUUID()}", 900)
        override suspend fun refresh(refreshToken: String) = Tokens(token(), "rt-${UUID.randomUUID()}", 900)
        override suspend fun consent(accessToken: String) = Consent(1, "t", "l", 90, 12)
        override suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean) = Unit
        override suspend fun logout(accessToken: String, refreshToken: String) = Unit
    }

    private class StubCollection : CollectionApi {
        var down = false
        override suspend fun openWorkday(accessToken: String, startedAtMillis: Long, clientId: String): OpenedWorkday = error("not used")
        override suspend fun sendPoints(accessToken: String, workdayId: String, points: List<GpsPointDto>): PointsAck = error("not used")
        override suspend fun closeWorkday(accessToken: String, workdayId: String, endedAtMillis: Long) = error("not used")
        override suspend fun route(accessToken: String): List<RouteStop> {
            if (down) throw ApiException(0, null)
            return listOf(RouteStop("s-1", "MONDAY", "08:30", RouteNeighbor("n-1", "VECINO-CENTINELA", "CALLE-CENTINELA 123", "DISTRITO-CENTINELA")))
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = UUID.randomUUID().toString().take(8)
    private val sessionVault = KeystoreVault("test-route-session-$tag")
    private val pendingVault = KeystoreVault("test-route-pending-$tag")
    private val clock = Clock()
    private val collection = StubCollection()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "route-$tag").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        sessionVault.destroyKey()
        pendingVault.destroyKey()
    }

    private fun cache() = RouteCache(dir, sessionVault, clock)
    private fun pending() = PendingStore(dir, pendingVault)
    private fun manager() = SessionManager(StubAuth(), SessionStore(dir, sessionVault), cache(), pending(), NothingUploadable, clock, "Honor de prueba", dir)

    private suspend fun repository(): RouteRepository {
        val session = manager()
        session.start()
        session.activate("CODIGO12345")
        return RouteRepository(collection, session, cache(), clock)
    }

    @Test
    fun downloadedRouteIsSealedAndShownWithoutNetworkWithItsDate() = runBlocking {
        val repo = repository()
        val downloadedAt = clock.now
        assertTrue(repo.refresh() is RouteState.Ready)

        val onDisk = File(dir, RouteCache.FILE_NAME).readBytes().toString(Charsets.ISO_8859_1)
        for (secret in listOf("VECINO-CENTINELA", "CALLE-CENTINELA", "DISTRITO-CENTINELA")) {
            assertFalse("$secret in the clear", onDisk.contains(secret))
        }

        collection.down = true
        clock.now += 3 * 24 * 60 * 60 * 1000
        val offline = repo.refresh() as RouteState.Ready
        assertTrue(offline.stale)
        assertEquals(downloadedAt, offline.fetchedAtMillis)
        assertEquals("VECINO-CENTINELA", offline.stops.single().neighbor.fullName)
    }

    @Test
    fun atSevenDaysOnlyTheRouteIsDeletedAndThePendingKeepUploading() = runBlocking {
        val repo = repository()
        repo.refresh()
        val record = PendingRecord("c-1", clock.now, "9.5", "TIPO-CENTINELA", "vecino-opaco-1")
        pending().add(record)

        clock.now += 7 * 24 * 60 * 60 * 1000 - 1
        assertTrue(repo.saved() is RouteState.Ready)

        clock.now += 1
        assertNull(repo.saved())
        assertFalse(File(dir, RouteCache.FILE_NAME).exists())
        assertEquals(listOf(record), pending().list())

        collection.down = true
        val state = repo.refresh() as RouteState.Unavailable
        assertTrue(state.expired)
    }
}
