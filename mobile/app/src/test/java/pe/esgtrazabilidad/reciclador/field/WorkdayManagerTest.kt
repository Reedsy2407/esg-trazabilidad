package pe.esgtrazabilidad.reciclador.field

import java.io.File
import kotlinx.coroutines.async
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
import pe.esgtrazabilidad.reciclador.offline
import pe.esgtrazabilidad.reciclador.revoked
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/** CA-43 (both branches), CA-47 (order by deviceSeq), batches, retries and the integrity rules of the workday. */
class WorkdayManagerTest {
    private lateinit var dir: File
    private val auth = FakeAuthApi()
    private val api = FakeCollectionApi()
    private val clock = FakeClock()
    private val store = InMemoryFieldStore()
    private val sessionVault = SoftwareVault()
    private val pendingVault = SoftwareVault()
    private var ids = 0

    @Before
    fun setUp() {
        dir = createTempDir("reciclador-workday-test")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun session() = SessionManager(
        api = auth,
        sessionStore = SessionStore(dir, sessionVault),
        routeCache = RouteCache(dir, sessionVault, clock),
        pending = PendingStore(dir, pendingVault),
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

    private fun manager(session: SessionManager) = WorkdayManager(store, api, session, clock) { "cliente-${++ids}" }

    private suspend fun WorkdayManager.points(n: Int) = repeat(n) {
        clock.advanceSeconds(10)
        assertTrue(recordPoint(-12.0 + it * 1e-5, -77.0, 8.0))
    }

    @Test
    fun theBackoffGrowsAndIsCapped() {
        assertEquals(0L, Backoff.delayMillis(0))
        assertEquals(15_000L, Backoff.delayMillis(1))
        assertEquals(30_000L, Backoff.delayMillis(2))
        assertEquals(60_000L, Backoff.delayMillis(3))
        assertEquals(Backoff.MAX_MILLIS, Backoff.delayMillis(50))
        assertTrue((1..30).map { Backoff.delayMillis(it) }.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun openingMakesOneWorkdayWithOneClientIdEvenIfStartedTwice() = runTest {
        val m = manager(signedIn())
        assertEquals(StartOutcome.STARTED, m.start(recordGps = true))
        assertEquals(StartOutcome.ALREADY_OPEN, m.start(recordGps = true))
        assertEquals(1, store.workdays.size)
        assertEquals(1, api.opened.size)
        assertEquals("cliente-1", store.workdays.values.single().clientId)
    }

    @Test
    fun aRetriedOpenAfterAnOfflineStartReusesTheSameClientId() = runTest {
        api.workdayErrors.add(offline())
        val m = manager(signedIn())
        m.start(recordGps = true)
        assertNull("the server was not reached", store.current()?.serverId)
        assertEquals(WorkdayManager.OFFLINE_NOTICE, m.ui.value.notice)
        m.sync(force = true)
        assertNotNull(store.current()?.serverId)
        assertEquals(listOf("cliente-1"), api.opened.keys.toList())
        // Even a second open with the same clientId would be answered 200 with the same workday (idempotent).
        assertEquals(2, api.openCalls)
    }

    @Test
    fun deviceSeqGrowsStrictlyAndSurvivesAProcessRestart() = runTest {
        val s = signedIn()
        val m = manager(s)
        m.start(recordGps = true)
        m.points(3)
        // A new manager over the same store is a revived process: the counter continues, it does not restart.
        val revived = manager(s)
        revived.resume()
        revived.points(2)
        revived.sync(force = true)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), api.receivedPoints.map { it.deviceSeq })
    }

    @Test
    fun theServerReceivesPointsInDeviceSeqOrderEvenIfThePhoneClockMovedBack() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        repeat(4) {
            clock.now += if (it == 2) -3_600_000 else 10_000 // the phone's clock jumps back an hour
            m.recordPoint(-12.0, -77.0, 5.0)
        }
        m.close()
        assertEquals(listOf(1L, 2L, 3L, 4L), api.receivedPoints.map { it.deviceSeq })
        assertFalse("the clock really went backwards", api.receivedPoints.map { it.recordedAtMillis } == api.receivedPoints.map { it.recordedAtMillis }.sorted())
    }

    @Test
    fun pointsGoInBatchesOfAtMost500() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(1200)
        m.close()
        assertEquals(listOf(500, 500, 200), api.batchSizes)
        assertEquals(1200, api.receivedPoints.size)
        assertEquals("the workday was confirmed closed and removed", null, store.current())
    }

    @Test
    fun aPointLeavesThePhoneOnlyAfterTheServerConfirmedItsBatch() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(5)
        // open already happened; the next call (the batch) fails
        api.workdayErrors.add(ApiException(503, null))
        assertEquals(SyncOutcome.WAITING, m.sync(force = true))
        assertEquals("nothing lost on a 503", 5, store.pointCount("cliente-1"))
        api.workdayErrors.add(ApiException(429, null))
        assertEquals(SyncOutcome.WAITING, m.sync(force = true))
        assertEquals(5, store.pointCount("cliente-1"))
        assertEquals(SyncOutcome.DONE, m.sync(force = true))
        assertEquals(0, store.pointCount("cliente-1"))
        assertEquals(5, api.receivedPoints.size)
    }

    @Test
    fun aFailureInTheMiddleKeepsOnlyWhatWasNotConfirmed() = runTest {
        // First batch (500) passes, the second one fails with a 500.
        val m = WorkdayManager(store, FlakyOnSecondBatch(api), signedIn(), clock) { "cliente-1" }
        m.start(recordGps = true)
        m.points(700)
        assertEquals(SyncOutcome.WAITING, m.sync(force = true))
        assertEquals(200, store.pointCount("cliente-1"))
        assertEquals(500, api.receivedPoints.size)
        // The rest goes on the next try, with no duplicates.
        assertEquals(SyncOutcome.DONE, m.sync(force = true))
        assertEquals(0, store.pointCount("cliente-1"))
        assertEquals(700, api.receivedPoints.size)
        assertEquals(700, api.receivedPoints.map { it.deviceSeq }.toSet().size)
    }

    @Test
    fun aPointStoredWhileABatchIsInFlightIsNotLostNorSentTwice() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : pe.esgtrazabilidad.reciclador.net.CollectionApi by api {
            override suspend fun sendPoints(
                accessToken: String,
                workdayId: String,
                points: List<pe.esgtrazabilidad.reciclador.net.GpsPointDto>,
            ): pe.esgtrazabilidad.reciclador.net.PointsAck {
                entered.complete(Unit)
                gate.await()
                return api.sendPoints(accessToken, workdayId, points)
            }
        }
        val m = WorkdayManager(store, slow, signedIn(), clock) { "cliente-1" }
        m.start(recordGps = true)
        m.points(3)
        val sending = async { m.sync(force = true) }
        entered.await()
        // The batch of 3 is on the wire; a 4th reading arrives now.
        clock.advanceSeconds(10)
        assertTrue(m.recordPoint(-12.5, -77.0, 4.0))
        gate.complete(Unit)
        sending.await()
        // The running send drains the 4th point too; nothing is lost and nothing goes twice.
        assertEquals(0, store.pointCount("cliente-1"))
        assertEquals(listOf(1L, 2L, 3L, 4L), api.receivedPoints.map { it.deviceSeq })
        assertEquals(listOf(3, 1), api.batchSizes)
    }

    @Test
    fun retriesWaitLongerAfterEachFailureAndNeverUseATimer() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(WorkdayManager.FLUSH_THRESHOLD)
        api.workdayErrors.add(offline())
        assertEquals(SyncOutcome.WAITING, m.sync(force = true)) // failure 1: wait 15 s
        val opens = api.openCalls
        assertEquals("inside the waiting time nothing is tried", SyncOutcome.WAITING, m.syncIfDue())
        clock.advanceSeconds(14)
        assertEquals(SyncOutcome.WAITING, m.syncIfDue())
        assertEquals(opens, api.openCalls)
        api.workdayErrors.add(offline())
        clock.advanceSeconds(2)
        assertEquals(SyncOutcome.WAITING, m.syncIfDue()) // tried, failed again (failure 2: 30 s)
        clock.advanceSeconds(29)
        assertEquals(SyncOutcome.WAITING, m.syncIfDue()) // still waiting
        clock.advanceSeconds(2)
        assertEquals(SyncOutcome.DONE, m.syncIfDue())
        assertEquals(0, store.pointCount("cliente-1"))
    }

    @Test
    fun aSmallNumberOfPointsIsNotSentOneByOneWhileRecording() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(3)
        val before = api.batchSizes.size
        m.onPointStored()
        assertEquals(before, api.batchSizes.size)
        m.points(WorkdayManager.FLUSH_THRESHOLD)
        m.onPointStored()
        assertEquals(before + 1, api.batchSizes.size)
    }

    @Test
    fun aWorkdayWithoutLocationRecordsAndSendsNoPoints() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = false)
        assertFalse(m.recordPoint(-12.0, -77.0, 5.0))
        assertFalse(m.shouldRecord())
        m.close()
        assertTrue(api.receivedPoints.isEmpty())
        assertTrue(api.batchSizes.isEmpty())
        assertEquals(1, api.closeCalls)
        assertNull(store.current())
    }

    @Test
    fun invalidReadingsAreIgnored() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        assertFalse(m.recordPoint(Double.NaN, -77.0, 5.0))
        assertFalse(m.recordPoint(91.0, -77.0, 5.0))
        assertFalse(m.recordPoint(-12.0, 181.0, 5.0))
        assertEquals(0, store.pointCount("cliente-1"))
        assertTrue("a missing accuracy is stored as 0, never negative", m.recordPoint(-12.0, -77.0, -1.0))
        assertEquals(0.0, store.nextBatch("cliente-1", 1).single().accuracyM, 0.0)
    }

    @Test
    fun closingTwiceIsHarmlessAndClosingOfflineWaits() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(2)
        api.workdayErrors.add(offline())
        // The open already happened at start; this makes the next call (points) fail while closing.
        assertEquals(SyncOutcome.WAITING, m.close())
        val w = store.current()!!
        assertEquals(WorkdayStatus.CLOSED, w.status)
        assertEquals(2, store.pointCount(w.clientId))
        val endedAt = w.endedAtMillis
        // Closing again does not move the end and recording is over.
        m.close()
        assertEquals(null, store.current())
        assertEquals(endedAt, api.closedAt)
        assertEquals(SyncOutcome.NOTHING, m.close())
    }

    @Test
    fun aClosedWorkdayRefusedByTheServerKeepsItsPointsAndLetsANewOneOpen() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(4)
        api.workdayErrors.add(ApiException(409, ApiException.CODE_WORKDAY_CLOSED))
        assertEquals(SyncOutcome.REJECTED, m.sync(force = true))
        assertEquals("the points are not lost", 4, store.pointCount("cliente-1"))
        assertEquals(WorkdayStatus.REJECTED, store.workdays.getValue("cliente-1").status)
        assertEquals(WorkdayManager.REJECTED_NOTICE, m.ui.value.notice)
        assertEquals(StartOutcome.STARTED, m.start(recordGps = true))
        // Nothing deletes them silently, however much time passes (R18).
        clock.advanceDays(30)
        m.resume()
        assertEquals(4, store.pointCount("cliente-1"))
    }

    @Test
    fun aRevocationKeepsEveryPointAndStopsTheRecording() = runTest {
        val s = signedIn()
        val m = manager(s)
        m.start(recordGps = true)
        m.points(WorkdayManager.FLUSH_THRESHOLD)
        api.workdayErrors.add(revoked("AUTH-017"))
        assertEquals(SyncOutcome.SESSION_ENDED, m.sync(force = true))
        assertEquals(WorkdayManager.FLUSH_THRESHOLD, store.pointCount("cliente-1"))
        assertFalse(s.state.value is SessionState.Active)
        assertFalse("the service must stop: there is no session", m.shouldRecord())
    }

    @Test
    fun aWorkdayOfAnotherAccountIsNeverSentNorOverwritten() = runTest {
        val first = signedIn()
        val m = manager(first)
        m.start(recordGps = true)
        m.points(3)
        // Another recycler activates this same phone.
        auth.recyclerId = "rid-otra-cuenta"
        val other = session().also {
            it.start()
            it.activate("OTRO-CODIGO")
            it.acceptConsent(true)
        }
        val m2 = WorkdayManager(store, api, other, clock) { "x" }
        assertEquals(StartOutcome.OTHER_ACCOUNT, m2.start(recordGps = true))
        assertEquals(SyncOutcome.OTHER_ACCOUNT, m2.sync(force = true))
        assertTrue("nothing of the first account reached the server", api.receivedPoints.isEmpty())
        assertEquals(3, store.pointCount("cliente-1"))
    }

    @Test
    fun aServerThatDoesNotKnowTheWorkdayIsAskedToOpenItAgainByClientId() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(2)
        api.workdayErrors.add(ApiException(404, ApiException.CODE_WORKDAY_NOT_FOUND))
        m.sync(force = true)
        assertNull(store.current()?.serverId)
        assertEquals(SyncOutcome.DONE, m.sync(force = true))
        assertEquals(2, api.receivedPoints.size)
    }

    @Test
    fun theLocationBeingTakenAwayContinuesTheWorkdayWithoutGps() = runTest {
        val m = manager(signedIn())
        m.start(recordGps = true)
        m.points(1)
        m.stopRecordingGps("El GPS se apagó")
        assertFalse(m.recordPoint(-12.0, -77.0, 5.0))
        assertEquals(WorkdayStatus.OPEN, store.current()?.status)
        assertEquals("El GPS se apagó", m.ui.value.notice)
    }
}

/** Passes the first batch and fails the second one with a server error. */
private class FlakyOnSecondBatch(private val inner: FakeCollectionApi) : pe.esgtrazabilidad.reciclador.net.CollectionApi by inner {
    private var batches = 0
    private var failed = false

    override suspend fun sendPoints(
        accessToken: String,
        workdayId: String,
        points: List<pe.esgtrazabilidad.reciclador.net.GpsPointDto>,
    ): pe.esgtrazabilidad.reciclador.net.PointsAck {
        if (++batches == 2 && !failed) {
            failed = true
            throw ApiException(500, null)
        }
        return inner.sendPoints(accessToken, workdayId, points)
    }
}
