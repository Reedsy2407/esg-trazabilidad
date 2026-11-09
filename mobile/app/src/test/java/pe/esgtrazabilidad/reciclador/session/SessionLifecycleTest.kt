package pe.esgtrazabilidad.reciclador.session

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import pe.esgtrazabilidad.reciclador.FakeAuthApi
import pe.esgtrazabilidad.reciclador.FakeClock
import pe.esgtrazabilidad.reciclador.FlakyVault
import pe.esgtrazabilidad.reciclador.SoftwareVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.offline
import pe.esgtrazabilidad.reciclador.revoked
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * CA-20 (logging out: the four branches), CA-21 (revocation keeps the pending records), CA-24 (7 days apply only to
 * the cached route), plus the automatic refresh. Sentinel records stand in for what module 2 will record.
 */
class SessionLifecycleTest {
    private lateinit var dir: File
    private val api = FakeAuthApi()
    private val clock = FakeClock()
    private val sessionVault = SoftwareVault()
    private val pendingVault = SoftwareVault()
    private var uploader: PendingUploader = NothingUploadable

    @Before
    fun setUp() {
        dir = createTempDir("reciclador-test")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val flakySession = FlakyVault(sessionVault)
    private val flakyPending = FlakyVault(pendingVault)

    private fun sessionStore() = SessionStore(dir, flakySession)
    private fun routeCache() = RouteCache(dir, flakySession, clock)
    private fun pendingStore() = PendingStore(dir, flakyPending)

    private fun manager() = SessionManager(
        api = api,
        sessionStore = sessionStore(),
        routeCache = routeCache(),
        pending = pendingStore(),
        uploader = { token, records -> uploader.upload(token, records) },
        time = clock,
        deviceLabel = "Honor de prueba",
        appDir = dir,
    )

    private fun record(n: Int) = PendingRecord(
        id = "client-id-$n",
        recordedAtMillis = clock.now - n * 1000L,
        weightKg = "12.5$n",
        wasteType = "TIPO-CENTINELA-$n",
        neighborId = "vecino-opaco-$n",
    )

    private suspend fun signedIn(pendingCount: Int = 0, withRoute: Boolean = true): SessionManager {
        val session = manager()
        session.start()
        session.activate("CODIGO12345")
        session.acceptConsent(locationAccepted = true)
        if (withRoute) {
            routeCache().save("""{"vecinos":[{"nombre":"VECINO-CENTINELA","direccion":"CALLE-CENTINELA 123","distrito":"DISTRITO-CENTINELA"}]}""")
        }
        repeat(pendingCount) { pendingStore().add(record(it + 1)) }
        File(dir, "otro-archivo.tmp").writeText("residuo de la sesion")
        return session
    }

    private fun files() = dir.listFiles().orEmpty().map { it.name }.toSet()

    private fun assertNothingOfTheSessionRemains() {
        assertFalse(files().contains(SessionStore.FILE_NAME))
        assertFalse(files().contains(RouteCache.FILE_NAME))
        assertFalse(files().contains("otro-archivo.tmp"))
        assertFalse("the session key must be gone", sessionVault.hasKey)
    }

    // ---- CA-20: logging out ------------------------------------------------------------------

    @Test
    fun branchA_withNothingPendingLoggingOutWipesEverythingAndAsksForTheCodeAgain() = runTest {
        val session = signedIn(pendingCount = 0)
        val lastRefresh = api.tokenPrefix + api.refreshCounter

        val plan = session.requestLogout()

        assertEquals(LogoutPlan.Done, plan)
        assertTrue(session.state.value is SessionState.NeedsCode)
        assertNothingOfTheSessionRemains()
        assertTrue("no pending file either", files().isEmpty())
        assertFalse(pendingVault.hasKey)
        assertEquals("the server was told to revoke this device", lastRefresh, api.loggedOutWith)
    }

    @Test
    fun branchA_loggingOutWorksWithoutNetworkAndStillWipesThePhone() = runTest {
        val session = signedIn()
        api.logoutError = offline()

        assertEquals(LogoutPlan.Done, session.requestLogout())
        assertNothingOfTheSessionRemains()
    }

    @Test
    fun branchB_withPendingRecordsNothingIsDeletedUntilTheRecyclerChooses() = runTest {
        val session = signedIn(pendingCount = 3)

        val plan = session.requestLogout()

        assertEquals(LogoutPlan.NeedsDecision(3), plan)
        assertTrue(session.state.value is SessionState.Active)
        assertTrue(files().contains(SessionStore.FILE_NAME))
        assertTrue(files().contains(RouteCache.FILE_NAME))
        assertEquals(3, pendingStore().count())
        assertNull("the server was not told yet", api.loggedOutWith)
    }

    @Test
    fun branchB_cancelLeavesEverythingAsItWas() = runTest {
        val session = signedIn(pendingCount = 3)
        session.requestLogout() // the dialog opened; "Cancelar" calls nothing

        assertTrue(session.state.value is SessionState.Active)
        assertEquals(3, pendingStore().count())
        assertNotNull(sessionStore().load())
        assertNotNull(routeCache().load())
    }

    @Test
    fun branchB_syncConfirmedByTheServerThenClosesAndWipesEverything() = runTest {
        val session = signedIn(pendingCount = 3)
        uploader = PendingUploader { _, records -> records.map { it.id }.toSet() }

        val outcome = session.logoutAfterSync()

        assertEquals(SyncOutcome.Done, outcome)
        assertTrue(session.state.value is SessionState.NeedsCode)
        assertNothingOfTheSessionRemains()
        assertEquals(0, pendingStore().count())
        assertFalse(pendingVault.hasKey)
    }

    @Test
    fun branchB_syncKeepsWhateverTheServerDidNotConfirmAndDoesNotCloseTheSession() = runTest {
        val session = signedIn(pendingCount = 3)
        uploader = PendingUploader { _, records -> records.take(2).map { it.id }.toSet() }

        val outcome = session.logoutAfterSync() as SyncOutcome.Remaining

        assertEquals(1, outcome.count)
        assertTrue(session.state.value is SessionState.Active)
        assertEquals(listOf("client-id-3"), pendingStore().list().map { it.id })
        assertNotNull(sessionStore().load())
    }

    @Test
    fun branchB_syncWithoutNetworkKeepsEverything() = runTest {
        val session = signedIn(pendingCount = 3)
        uploader = PendingUploader { _, _ -> throw offline() }

        val outcome = session.logoutAfterSync() as SyncOutcome.Remaining

        assertEquals(3, outcome.count)
        assertEquals(3, pendingStore().count())
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test
    fun branchB_syncThatConfirmsNothingKeepsEverything() = runTest {
        val session = signedIn(pendingCount = 2)
        // The default uploader of module 1: there is no route to send to yet.
        uploader = NothingUploadable

        val outcome = session.logoutAfterSync() as SyncOutcome.Remaining

        assertEquals(2, outcome.count)
        assertEquals(2, pendingStore().count())
    }

    @Test
    fun branchB_discardNeedsTheSecondConfirmationThatRepeatsTheCount() = runTest {
        val session = signedIn(pendingCount = 3)
        val ticket = session.beginDiscard()
        assertEquals(3, ticket.count)

        // A confirmation that does not repeat the real count is not a confirmation: nothing is deleted.
        assertRefused { session.confirmDiscard(ticket, shownCount = 2) }
        assertEquals(3, pendingStore().count())
        assertTrue(session.state.value is SessionState.Active)

        session.confirmDiscard(ticket, shownCount = 3)

        assertTrue(session.state.value is SessionState.NeedsCode)
        assertNothingOfTheSessionRemains()
        assertEquals(0, pendingStore().count())
        assertFalse(pendingVault.hasKey)
    }

    // ---- CA-21: revocation ------------------------------------------------------------------

    @Test
    fun revocationInAnyCallDropsCredentialsAndRouteButKeepsThePendingRecordsReadable() = runTest {
        val session = signedIn(pendingCount = 3)
        val before = pendingStore().list()

        val error = runCatching { session.authorized<Unit> { throw revoked("AUTH-017") } }.exceptionOrNull() as ApiException

        assertEquals("AUTH-017", error.code)
        assertEquals(SessionState.Revoked(pendingCount = 3, code = "AUTH-017"), session.state.value)
        assertNothingOfTheSessionRemains()
        assertEquals("the pending records were not touched", before, pendingStore().list())
        assertTrue("their own key survives the session wipe", pendingVault.hasKey)
    }

    @Test
    fun anInactiveAccountIsTreatedTheSameWay() = runTest {
        val session = signedIn(pendingCount = 1)

        runCatching { session.authorized<Unit> { throw ApiException(403, "AUTH-018") } }

        assertEquals(SessionState.Revoked(1, "AUTH-018"), session.state.value)
        assertEquals(1, pendingStore().count())
    }

    @Test
    fun revocationFoundWhenRefreshingAlsoKeepsThePending() = runTest {
        val session = signedIn(pendingCount = 2)
        clock.advanceSeconds(901) // the access token expired
        api.refreshError = revoked()

        val error = runCatching { session.accessToken() }.exceptionOrNull() as ApiException

        assertTrue(error.isRevocation)
        assertEquals(SessionState.Revoked(2, "AUTH-017"), session.state.value)
        assertEquals(2, pendingStore().count())
        assertNothingOfTheSessionRemains()
    }

    @Test
    fun withNothingPendingARevocationJustAsksForANewCodeWithANotice() = runTest {
        val session = signedIn(pendingCount = 0)

        runCatching { session.authorized<Unit> { throw revoked() } }

        val state = session.state.value as SessionState.NeedsCode
        assertNotNull(state.notice)
        assertNothingOfTheSessionRemains()
    }

    @Test
    fun afterARevocationTheAppStillShowsTheBlockingNoticeOnTheNextStart() = runTest {
        val session = signedIn(pendingCount = 3)
        runCatching { session.authorized<Unit> { throw revoked() } }

        val restarted = manager()
        restarted.start()

        assertEquals(SessionState.Revoked(pendingCount = 3, code = null), restarted.state.value)
    }

    @Test
    fun reactivatingTheSamePhoneKeepsThePendingRecordsForTheUpload() = runTest {
        val session = signedIn(pendingCount = 3)
        runCatching { session.authorized<Unit> { throw revoked() } }
        val before = pendingStore().list()

        session.activate("CODIGONUEVO1")
        session.acceptConsent(locationAccepted = true)

        assertTrue(session.state.value is SessionState.Active)
        assertEquals(before, pendingStore().list())
    }

    @Test
    fun aRevokedSessionCanDiscardOnlyWithTheSecondConfirmation() = runTest {
        val session = signedIn(pendingCount = 3)
        runCatching { session.authorized<Unit> { throw revoked() } }
        val ticket = session.beginDiscard()

        assertRefused { session.confirmDiscard(ticket, shownCount = 1) }
        assertEquals(3, pendingStore().count())

        session.confirmDiscard(ticket, shownCount = 3)

        assertTrue(session.state.value is SessionState.NeedsCode)
        assertEquals(0, pendingStore().count())
    }

    // ---- the double confirmation is enforced by the API, not only by the screens ------------------

    private suspend fun assertRefused(block: suspend () -> Unit) {
        try {
            block()
            fail("must refuse")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun withoutTheFirstStepNothingIsDiscardedEvenWithTheRightNumber() = runTest {
        val session = signedIn(pendingCount = 3)
        val forged = DiscardTicket(count = 3, ids = pendingStore().list().map { it.id })

        assertRefused { session.confirmDiscard(forged, shownCount = 3) }

        assertEquals(3, pendingStore().count())
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test
    fun aCancelledOrReplacedTicketIsRefusedAndTheLatestOneWorks() = runTest {
        val session = signedIn(pendingCount = 3)

        val cancelled = session.beginDiscard()
        session.cancelDiscard()
        assertRefused { session.confirmDiscard(cancelled, shownCount = 3) }

        val first = session.beginDiscard()
        val second = session.beginDiscard()
        assertRefused { session.confirmDiscard(first, shownCount = 3) }
        assertEquals(3, pendingStore().count())

        session.confirmDiscard(second, shownCount = 3)
        assertEquals(0, pendingStore().count())
    }

    @Test
    fun aTicketCannotBeUsedTwice() = runTest {
        val session = signedIn(pendingCount = 2)
        val ticket = session.beginDiscard()

        session.confirmDiscard(ticket, shownCount = 2)
        pendingStore().add(record(9)) // something recorded afterwards

        assertRefused { session.confirmDiscard(ticket, shownCount = 2) }
        assertEquals(1, pendingStore().count())
    }

    @Test
    fun aRecordAddedAfterTheConfirmationWasShownBlocksTheDiscard() = runTest {
        val session = signedIn(pendingCount = 3)
        val ticket = session.beginDiscard()

        pendingStore().add(record(9))

        assertRefused { session.confirmDiscard(ticket, shownCount = 3) }
        assertEquals("the new record was not counted, so it is not deleted", 4, pendingStore().count())
        assertTrue(session.state.value is SessionState.Active)
        assertNotNull(sessionStore().load())
    }

    @Test
    fun aRecordAddedWhileTheTokenIsBeingRefreshedBlocksTheDiscardToo() = runTest {
        val session = signedIn(pendingCount = 3)
        val ticket = session.beginDiscard()
        clock.advanceSeconds(901) // closing the session first needs a refresh, which takes the network
        api.onRefresh = { pendingStore().add(record(9)) }

        assertRefused { session.confirmDiscard(ticket, shownCount = 3) }

        assertEquals(4, pendingStore().count())
        assertNotNull("nothing of the session was wiped", sessionStore().load())
    }

    @Test
    fun aRecordAddedWhileLoggingOutWithNothingPendingTurnsIntoADecisionNotAWipe() = runTest {
        val session = signedIn(pendingCount = 0)
        clock.advanceSeconds(901)
        api.onRefresh = { pendingStore().add(record(1)) }

        val plan = session.requestLogout()

        assertEquals(LogoutPlan.NeedsDecision(1), plan)
        assertEquals(1, pendingStore().count())
        assertNotNull(sessionStore().load())
    }

    // ---- the Keystore failing at a bad moment leaves a consistent phone ---------------------------

    @Test
    fun ifDeletingAKeyFailsTheLogoutStillEndsCleanly() = runTest {
        val session = signedIn(pendingCount = 3)
        val ticket = session.beginDiscard()
        flakySession.failDestroy = true
        flakyPending.failDestroy = true

        session.confirmDiscard(ticket, shownCount = 3)

        assertEquals(SessionState.NeedsCode(), session.state.value)
        assertEquals("the files are gone: a key left behind opens nothing", emptySet<String>(), files())
    }

    @Test
    fun aTransientKeystoreFailureWhileCountingDeletesNothingAndIsReported() = runTest {
        val session = signedIn(pendingCount = 3)
        flakyPending.failOpen = true

        val error = runCatching { session.requestLogout() }.exceptionOrNull()

        assertTrue(error is pe.esgtrazabilidad.reciclador.crypto.VaultTransientException)
        assertTrue(files().contains(PendingStore.FILE_NAME))
        assertTrue(files().contains(SessionStore.FILE_NAME))
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test
    fun aRevocationWhileTheKeystoreCannotCountStillEndsTheSessionAndKeepsThePending() = runTest {
        val session = signedIn(pendingCount = 3)
        flakyPending.failOpen = true

        runCatching { session.authorized<Unit> { throw revoked() } }

        assertEquals(SessionState.NeedsCode(notice = SessionManager.KEYSTORE_BUSY), session.state.value)
        assertNothingOfTheSessionRemains()
        assertTrue("the pending file was not touched", files().contains(PendingStore.FILE_NAME))

        flakyPending.failOpen = false
        val restarted = manager()
        restarted.start()
        assertEquals(SessionState.Revoked(pendingCount = 3, code = null), restarted.state.value)
    }

    @Test
    fun pendingRecordsAreSealedOnDiskAndCarryNoNeighbourData() = runTest {
        signedIn(pendingCount = 2)

        val raw = String(File(dir, PendingStore.FILE_NAME).readBytes(), Charsets.ISO_8859_1)
        assertFalse("sealed, not readable", raw.contains("TIPO-CENTINELA"))
        assertFalse(raw.contains("vecino-opaco"))
        // What is stored is only the opaque id: no name, address, district or phone field exists.
        val fields = PendingRecord::class.java.declaredFields.filterNot { it.isSynthetic || it.name.startsWith("\$") }.map { it.name }.toSet()
        assertEquals(setOf("id", "recordedAtMillis", "weightKg", "wasteType", "neighborId"), fields)
    }

    // ---- CA-24: the 7 days belong to the route only -----------------------------------------

    @Test
    fun theRouteExpiresAfterSevenDaysAndOnlyTheRoute() = runTest {
        val session = signedIn(pendingCount = 3)
        val pendingBefore = pendingStore().list()

        clock.advanceDays(6)
        clock.advanceSeconds(23 * 3600)
        assertNotNull("6 days and 23 hours: still there", routeCache().load())

        clock.advanceSeconds(3600) // exactly 7 days since it was fetched
        api.refreshError = offline() // the app starts with no network: the purge must not depend on it
        val restarted = manager()
        restarted.start()

        assertFalse("the route is gone", files().contains(RouteCache.FILE_NAME))
        assertEquals("the pending records are intact", pendingBefore, pendingStore().list())
        assertEquals("they keep being uploadable", 3, pendingStore().count())
        assertNotNull("the session is untouched", sessionStore().load())
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test
    fun theRouteIsAlsoPurgedWhenTheAppTriesToSyncLaterWithoutRestarting() = runTest {
        val session = signedIn(pendingCount = 1)
        clock.advanceDays(8)

        session.accessToken()

        assertFalse(files().contains(RouteCache.FILE_NAME))
        assertEquals(1, pendingStore().count())
    }

    @Test
    fun pendingRecordsNeverExpireByAge() = runTest {
        signedIn(pendingCount = 2)
        clock.advanceDays(400)

        val restarted = manager()
        restarted.start()

        assertEquals(2, pendingStore().count())
    }

    // ---- automatic refresh ------------------------------------------------------------------

    @Test
    fun theAccessTokenIsReusedWhileValidAndRefreshedOnceWhenItExpires() = runTest {
        val session = signedIn()
        val refreshesAfterSignIn = api.refreshCounter

        val first = session.accessToken()
        clock.advanceSeconds(600)
        assertEquals("still valid at 10 minutes", first, session.accessToken())
        assertEquals(refreshesAfterSignIn, api.refreshCounter)

        clock.advanceSeconds(300) // 15 minutes: expired (with the safety margin)
        val second = session.accessToken()

        assertEquals(refreshesAfterSignIn + 1, api.refreshCounter)
        assertTrue(second != first)
    }

    @Test
    fun severalCallsAtTheSameMomentCauseOneRefreshNotARace() = runTest {
        val session = signedIn()
        clock.advanceSeconds(901)
        val before = api.refreshCounter

        val tokens = (1..5).map { async { session.accessToken() } }.awaitAll()

        assertEquals(before + 1, api.refreshCounter)
        assertEquals(1, tokens.toSet().size)
    }

    @Test
    fun theRotatedRefreshTokenIsTheOneStoredSealed() = runTest {
        val session = signedIn()
        clock.advanceSeconds(901)

        session.accessToken()

        assertEquals(api.tokenPrefix + api.refreshCounter, sessionStore().load())
    }

    @Test
    fun withoutNetworkARefreshFailsButTheSessionIsKept() = runTest {
        val session = signedIn(pendingCount = 1)
        clock.advanceSeconds(901)
        api.refreshError = offline()

        val error = runCatching { session.accessToken() }.exceptionOrNull() as ApiException

        assertTrue(error.isNetwork)
        assertTrue(session.state.value is SessionState.Active)
        assertNotNull(sessionStore().load())
        assertEquals(1, pendingStore().count())
    }

    @Test
    fun anInvalidRefreshTokenEndsTheSessionAndKeepsThePending() = runTest {
        val session = signedIn(pendingCount = 1)
        clock.advanceSeconds(901)
        api.refreshError = ApiException(401, "AUTH-016")

        runCatching { session.accessToken() }

        assertEquals(SessionState.Revoked(1, "AUTH-016"), session.state.value)
        assertEquals(1, pendingStore().count())
        assertNull(sessionStore().load())
    }
}
