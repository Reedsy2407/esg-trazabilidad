package pe.esgtrazabilidad.reciclador.route

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.esgtrazabilidad.reciclador.FakeAuthApi
import pe.esgtrazabilidad.reciclador.FakeClock
import pe.esgtrazabilidad.reciclador.FakeCollectionApi
import pe.esgtrazabilidad.reciclador.SoftwareVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.RouteJson
import pe.esgtrazabilidad.reciclador.offline
import pe.esgtrazabilidad.reciclador.revoked
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/** CA-24 (the 7 days, with a controlled clock) and CA-42 (download, or the saved route with its date). */
class RouteRepositoryTest {
    private lateinit var dir: File
    private val auth = FakeAuthApi()
    private val collection = FakeCollectionApi()
    private val clock = FakeClock()
    private val sessionVault = SoftwareVault()
    private val pendingVault = SoftwareVault()

    @Before
    fun setUp() {
        dir = createTempDir("reciclador-route-test")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cache() = RouteCache(dir, sessionVault, clock)
    private fun pending() = PendingStore(dir, pendingVault)

    private fun session() = SessionManager(
        api = auth,
        sessionStore = SessionStore(dir, sessionVault),
        routeCache = cache(),
        pending = pending(),
        uploader = NothingUploadable,
        time = clock,
        deviceLabel = "Honor de prueba",
        appDir = dir,
    )

    private suspend fun signedIn(): SessionManager {
        val s = session()
        s.start()
        s.activate("CODIGO12345")
        s.acceptConsent(locationAccepted = true)
        return s
    }

    private fun repository(session: SessionManager) = RouteRepository(collection, session, cache(), clock)

    @Test
    fun downloadsSavesAndReportsTheDownloadTime() = runTest {
        val repo = repository(signedIn())
        val downloadedAt = clock.now
        val state = repo.refresh() as RouteState.Ready
        assertEquals("VECINO-CENTINELA-1", state.stops.single().neighbor.fullName)
        assertEquals(downloadedAt, state.fetchedAtMillis)
        assertFalse(state.stale)
        assertEquals(1, collection.routeCalls)
        // What is saved is the same route, with the same download time.
        clock.advanceSeconds(3600)
        assertEquals(downloadedAt, (repo.saved() as RouteState.Ready).fetchedAtMillis)
    }

    @Test
    fun withoutNetworkShowsTheSavedRouteWithItsDate() = runTest {
        val repo = repository(signedIn())
        val downloadedAt = clock.now
        repo.refresh()
        clock.advanceDays(2)
        collection.routeError = offline()
        val state = repo.refresh() as RouteState.Ready
        assertTrue(state.stale)
        assertEquals(downloadedAt, state.fetchedAtMillis)
        assertNotNull(state.reason)
    }

    @Test
    fun aServerErrorOrARateLimitAlsoFallsBackToTheSavedRoute() = runTest {
        val repo = repository(signedIn())
        repo.refresh()
        for (status in listOf(500, 503, 429)) {
            collection.routeError = ApiException(status, null)
            assertTrue("status $status", (repo.refresh() as RouteState.Ready).stale)
        }
    }

    @Test
    fun withoutNetworkAndWithoutASavedRouteSaysSo() = runTest {
        val repo = repository(signedIn())
        collection.routeError = offline()
        val state = repo.refresh() as RouteState.Unavailable
        assertFalse(state.expired)
    }

    @Test
    fun theRouteExpiresAfterSevenDaysAndOnlyTheRouteIsDeleted() = runTest {
        val session = signedIn()
        val repo = repository(session)
        repo.refresh()
        val record = PendingRecord("id-1", clock.now, "10.5", "PAPEL", "vecino-opaco-1")
        pending().add(record)

        clock.advanceDays(6)
        assertNotNull("6 days: still there", repo.saved())

        clock.advanceDays(1)
        // 7 days since the download: gone, with the clock alone (no network involved).
        assertNull(repo.saved())
        assertFalse(File(dir, RouteCache.FILE_NAME).exists())
        assertTrue(cache().wasExpired())
        // The pending records did not move.
        assertEquals(listOf(record), pending().list())

        collection.routeError = offline()
        val state = repo.refresh() as RouteState.Unavailable
        assertTrue("the screen says the saved route expired", state.expired)
    }

    @Test
    fun startingTheAppAppliesTheSevenDaysAndKeepsThePending() = runTest {
        val session = signedIn()
        repository(session).refresh()
        pending().add(PendingRecord("id-1", clock.now, "10.5", "PAPEL", "vecino-opaco-1"))
        clock.advanceDays(8)
        val reopened = session()
        reopened.start()
        assertFalse(File(dir, RouteCache.FILE_NAME).exists())
        assertEquals(1, pending().count())
    }

    @Test
    fun aNewDownloadAfterExpiryClearsTheExpiredNotice() = runTest {
        val repo = repository(signedIn())
        repo.refresh()
        clock.advanceDays(8)
        assertNull(repo.saved())
        assertTrue(cache().wasExpired())
        assertTrue(repo.refresh() is RouteState.Ready)
        assertFalse(cache().wasExpired())
    }

    @Test
    fun theCachedRouteIsSealedAndHoldsNothingInTheClear() = runTest {
        val repo = repository(signedIn())
        repo.refresh()
        val raw = File(dir, RouteCache.FILE_NAME).readBytes().toString(Charsets.ISO_8859_1)
        for (secret in listOf("VECINO-CENTINELA-1", "CALLE-CENTINELA 1", "DISTRITO-CENTINELA", "vecino-1")) {
            assertFalse("$secret must not be on disk in the clear", raw.contains(secret))
        }
    }

    @Test
    fun anEmptyRouteIsNotAnError() = runTest {
        collection.stops = emptyList()
        val state = repository(signedIn()).refresh()
        assertTrue(state is RouteState.Empty)
    }

    @Test
    fun a401IsRetriedOnceWithAFreshToken() = runTest {
        val session = signedIn()
        val repo = repository(session)
        collection.routeErrors.add(ApiException(401, null))
        val state = repo.refresh()
        assertTrue(state is RouteState.Ready)
        assertEquals(2, collection.routeCalls)
        assertEquals("a refresh happened between the two calls", 2, collection.tokensSeen.toSet().size)
    }

    @Test
    fun aRevocationDropsTheSessionAndTheRoute() = runTest {
        val session = signedIn()
        val repo = repository(session)
        repo.refresh()
        collection.routeError = revoked("AUTH-017")
        repo.refresh()
        assertTrue(session.state.value is SessionState.NeedsCode)
        assertFalse(File(dir, RouteCache.FILE_NAME).exists())
    }

    @Test
    fun anAccountInactiveAnswerAlsoEndsTheSession() = runTest {
        val session = signedIn()
        collection.routeError = revoked("AUTH-018")
        repository(session).refresh()
        assertTrue(session.state.value is SessionState.NeedsCode)
    }

    @Test
    fun aTransientKeystoreFailureDoesNotCrashAndStillShowsTheDownload() = runTest {
        val session = signedIn()
        val flaky = pe.esgtrazabilidad.reciclador.FlakyVault(sessionVault)
        val repo = RouteRepository(collection, session, RouteCache(dir, flaky, clock), clock)
        flaky.failOpen = true
        val state = repo.refresh()
        assertTrue("the downloaded stops are shown", state is RouteState.Ready)
        // Offline with the Keystore also failing: no crash, just no saved route to show.
        collection.routeError = offline()
        assertTrue(repo.refresh() is RouteState.Unavailable)
        assertNull(repo.saved())
    }

    @Test
    fun theRouteJsonRoundTrips() {
        val stops = listOf(FakeCollectionApi.stop(1), FakeCollectionApi.stop(2))
        assertEquals(stops, RouteJson.parse(RouteJson.write(stops)))
    }

    @Test
    fun theTimeComesInAnyOfTheServersForms() {
        val body = """[{"scheduleId":"a","dayOfWeek":"FRIDAY","time":"07:05:00","neighbor":{"id":"n","fullName":"x","address":"y","district":"z"}},
            {"scheduleId":"b","dayOfWeek":"FRIDAY","time":[18,0],"neighbor":{"id":"n","fullName":"x","address":"y","district":"z"}}]"""
        assertEquals(listOf("07:05", "18:00"), RouteJson.parse(body).map { it.time })
    }
}
