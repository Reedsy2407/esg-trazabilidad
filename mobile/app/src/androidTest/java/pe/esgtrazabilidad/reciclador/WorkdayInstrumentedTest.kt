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
import pe.esgtrazabilidad.reciclador.field.RoomFieldStore
import pe.esgtrazabilidad.reciclador.field.StartOutcome
import pe.esgtrazabilidad.reciclador.field.SyncOutcome
import pe.esgtrazabilidad.reciclador.field.WorkdayManager
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.CollectionApi
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.GpsPointDto
import pe.esgtrazabilidad.reciclador.net.OpenedWorkday
import pe.esgtrazabilidad.reciclador.net.PointsAck
import pe.esgtrazabilidad.reciclador.net.RouteStop
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * CA-43 (the two branches) and the Room store, on the phone: real Room (in memory), the real Keystore for the session
 * and a server that the test scripts. The foreground service itself is exercised by hand on the Honor (see the report).
 */
class WorkdayInstrumentedTest {
    private class Clock(var now: Long = 1_800_000_000_000L) : TimeSource {
        override fun nowMillis() = now
    }

    private class StubAuth : AuthApi {
        private fun token(): String {
            val enc = Base64.getUrlEncoder().withoutPadding()
            val payload = JSONObject().put("sub", "x").put("rid", "rid-prueba").put("cv", 1).put("lc", true).toString()
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
        val points = mutableListOf<GpsPointDto>()
        var closed = false

        override suspend fun route(accessToken: String): List<RouteStop> = emptyList()

        override suspend fun openWorkday(accessToken: String, startedAtMillis: Long, clientId: String): OpenedWorkday {
            if (down) throw ApiException(0, null)
            return OpenedWorkday("jornada-1", created = true)
        }

        override suspend fun sendPoints(accessToken: String, workdayId: String, points: List<GpsPointDto>): PointsAck {
            if (down) throw ApiException(0, null)
            this.points += points
            return PointsAck(points.size, 0)
        }

        override suspend fun closeWorkday(accessToken: String, workdayId: String, endedAtMillis: Long) {
            if (down) throw ApiException(0, null)
            closed = true
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = UUID.randomUUID().toString().take(8)
    private val sessionVault = KeystoreVault("test-workday-session-$tag")
    private val pendingVault = KeystoreVault("test-workday-pending-$tag")
    private val clock = Clock()
    private val server = StubCollection()
    private lateinit var dir: File
    private lateinit var store: RoomFieldStore

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "workday-$tag").apply { mkdirs() }
        store = RoomFieldStore.inMemory(context)
    }

    @After
    fun tearDown() {
        store.close()
        dir.deleteRecursively()
        sessionVault.destroyKey()
        pendingVault.destroyKey()
    }

    private suspend fun manager(): WorkdayManager {
        val session = SessionManager(
            StubAuth(), SessionStore(dir, sessionVault), RouteCache(dir, sessionVault, clock), PendingStore(dir, pendingVault),
            NothingUploadable, clock, "Honor de prueba", dir,
        )
        session.start()
        session.activate("CODIGO12345")
        return WorkdayManager(store, server, session, clock)
    }

    @Test
    fun branchWithLocationStoresPointsInRoomAndSendsThemInOrder() = runBlocking {
        val m = manager()
        assertEquals(StartOutcome.STARTED, m.start(recordGps = true))
        repeat(30) {
            clock.now += if (it == 10) -3_600_000 else 10_000 // the phone changes its hour in the middle
            assertTrue(m.recordPoint(-12.04 + it * 1e-5, -77.03, 6.0))
        }
        val clientId = store.current()!!.clientId
        assertEquals(30, store.pointCount(clientId))
        assertEquals((1L..30L).toList(), store.nextBatch(clientId, 500).map { it.deviceSeq })

        m.close()
        assertEquals((1L..30L).toList(), server.points.map { it.deviceSeq })
        assertTrue(server.closed)
        assertNull("confirmed closed and nothing left", store.current())
        assertEquals(0, store.pointCount(clientId))
    }

    @Test
    fun branchWithoutLocationOpensWithoutGpsAndSendsNoPoints() = runBlocking {
        val m = manager()
        m.start(recordGps = false)
        assertFalse(m.recordPoint(-12.04, -77.03, 6.0))
        assertFalse(m.shouldRecord())
        m.close()
        assertTrue(server.points.isEmpty())
        assertTrue(server.closed)
    }

    @Test
    fun withoutNetworkNothingIsLostAndTheNextOpeningSendsIt() = runBlocking {
        val m = manager()
        server.down = true
        m.start(recordGps = true)
        repeat(5) {
            clock.now += 10_000
            m.recordPoint(-12.04, -77.03, 6.0)
        }
        assertEquals(SyncOutcome.WAITING, m.close())
        val clientId = store.current()!!.clientId
        assertEquals("kept on the phone", 5, store.pointCount(clientId))

        server.down = false
        // The app is opened again (a new manager over the same database): it resumes and sends.
        val reopened = manager()
        assertEquals(SyncOutcome.DONE, reopened.resume())
        assertEquals(5, server.points.size)
        assertNull(store.current())
    }

    @Test
    fun theCounterIsInTheSameRowAsThePointsSoItNeverGoesBack() = runBlocking {
        val m = manager()
        m.start(recordGps = true)
        server.down = true
        repeat(3) { m.recordPoint(-12.0, -77.0, 5.0) ; clock.now += 1 }
        val clientId = store.current()!!.clientId
        val before = store.current()!!.nextSeq
        // Points removed (as after a confirmation) do not make the counter start over.
        store.deletePoints(store.nextBatch(clientId, 10).map { it.id })
        m.recordPoint(-12.0, -77.0, 5.0)
        assertEquals(before, store.nextBatch(clientId, 1).single().deviceSeq)
    }
}
