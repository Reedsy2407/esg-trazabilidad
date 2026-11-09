package pe.esgtrazabilidad.reciclador.session

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
import pe.esgtrazabilidad.reciclador.FlakyVault
import pe.esgtrazabilidad.reciclador.SoftwareVault
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.offline
import pe.esgtrazabilidad.reciclador.revoked
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/** CA-16 (activation, consent, staying signed in), CA-18 (nothing readable without the key). */
class SessionActivationTest {
    private lateinit var dir: File
    private val api = FakeAuthApi()
    private val clock = FakeClock()
    private val sessionVault = SoftwareVault()

    @Before
    fun setUp() {
        dir = createTempDir("reciclador-test")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val pendingVault = SoftwareVault()

    private fun manager(): SessionManager = SessionManager(
        api = api,
        sessionStore = SessionStore(dir, sessionVault),
        routeCache = RouteCache(dir, sessionVault, clock),
        pending = PendingStore(dir, pendingVault),
        uploader = NothingUploadable,
        time = clock,
        deviceLabel = "Honor de prueba",
        appDir = dir,
    )

    private fun allFileText(): String = dir.listFiles().orEmpty().joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }

    @Test
    fun withNothingStoredTheAppAsksForTheCode() = runTest {
        val session = manager()
        session.start()
        assertTrue(session.state.value is SessionState.NeedsCode)
    }

    @Test
    fun activatingAsksForTheConsentAndAcceptingItOpensTheSessionWithTheLocationChoice() = runTest {
        val session = manager()
        session.start()
        session.activate(" CODIGO12345 ")

        val needs = session.state.value as SessionState.NeedsConsent
        assertEquals(90, needs.consent.gpsDays)
        assertEquals(12, needs.consent.photoMonths)

        session.acceptConsent(locationAccepted = true)
        assertEquals(SessionState.Active(offline = false, locationAccepted = true), session.state.value)
    }

    @Test
    fun refusingTheLocationStillOpensTheSessionButSaysSo() = runTest {
        val session = manager()
        session.activate("CODIGO12345")
        session.acceptConsent(locationAccepted = false)
        assertEquals(SessionState.Active(offline = false, locationAccepted = false), session.state.value)
    }

    @Test
    fun theRefreshTokenReachesTheDiskOnlySealed() = runTest {
        val session = manager()
        session.activate("CODIGO12345")
        session.acceptConsent(locationAccepted = true)

        val files = dir.listFiles().orEmpty().map { it.name }
        assertTrue(files.contains(SessionStore.FILE_NAME))
        assertFalse("the token must not be on disk in clear", allFileText().contains(api.tokenPrefix))
        assertFalse("no access token on disk either", allFileText().contains("RECYCLER"))
        assertFalse(allFileText().contains("eyJ"))
    }

    @Test
    fun reopeningTheAppKeepsTheSessionWithoutAskingForTheCode() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        val reopened = manager()
        reopened.start()

        assertEquals(SessionState.Active(offline = false, locationAccepted = true), reopened.state.value)
        assertEquals("the stored token was rotated on the refresh", 3, api.refreshCounter)
    }

    @Test
    fun aNewConsentVersionAsksAgainWhenTheAppReopens() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        // The administrator changed a period: the next token has no `cv` until the new text is accepted.
        api.consentVersionServerSide = 2
        api.consentAccepted = false

        val reopened = manager()
        reopened.start()
        assertEquals(2, (reopened.state.value as SessionState.NeedsConsent).consent.version)
    }

    @Test
    fun withoutTheKeyTheAppDoesNotCrashAndAsksForANewCode() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        assertNotNull(File(dir, SessionStore.FILE_NAME).takeIf { it.exists() })
        // The storage came back (a restore, an invalidated key) without the key that sealed it.
        sessionVault.destroyKey()

        val reopened = manager()
        reopened.start()

        assertTrue(reopened.state.value is SessionState.NeedsCode)
        assertFalse(File(dir, SessionStore.FILE_NAME).exists())
    }

    @Test
    fun ifTheConsentCannotBeFetchedAfterActivatingTheSessionSurvivesAndTheConsentIsRetried() = runTest {
        val session = manager()
        api.consentFetchError = offline()

        session.activate("CODIGO12345")

        assertTrue("the code is spent: do not ask for another", session.state.value is SessionState.ConsentUnavailable)
        assertNotNull(SessionStore(dir, sessionVault).load())

        api.consentFetchError = null
        session.retryConsent()
        assertTrue(session.state.value is SessionState.NeedsConsent)
        session.acceptConsent(locationAccepted = false)
        assertEquals(SessionState.Active(offline = false, locationAccepted = false), session.state.value)
    }

    @Test
    fun aRateLimitOrAServerErrorAtStartupDoesNotEndTheSession() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        for (error in listOf(ApiException(429, null), ApiException(404, null), ApiException(400, null), ApiException(503, null))) {
            api.refreshError = error
            val reopened = manager()
            reopened.start()
            assertEquals("status ${error.status}", SessionState.Active(offline = true), reopened.state.value)
            assertNotNull("status ${error.status}", SessionStore(dir, sessionVault).load())
        }
    }

    @Test
    fun aTransientKeystoreFailureDeletesNothingAndTheNextStartWorks() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        val flaky = FlakyVault(sessionVault)
        fun flakyManager() = SessionManager(
            api, SessionStore(dir, flaky), RouteCache(dir, flaky, clock), PendingStore(dir, pendingVault),
            NothingUploadable, clock, "Honor de prueba", dir,
        )

        flaky.failOpen = true
        val busy = flakyManager()
        busy.start()

        val state = busy.state.value as SessionState.NeedsCode
        assertEquals(SessionManager.KEYSTORE_BUSY, state.notice)
        assertTrue("nothing was deleted", File(dir, SessionStore.FILE_NAME).exists())
        assertTrue("the key was not destroyed", sessionVault.hasKey)

        flaky.failOpen = false
        val later = flakyManager()
        later.start()
        assertEquals(SessionState.Active(offline = false, locationAccepted = true), later.state.value)
    }

    @Test
    fun aLostKeyIsSaidNotSilentlyTreatedAsAFirstRun() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        sessionVault.destroyKey()

        val reopened = manager()
        reopened.start()

        assertEquals(SessionManager.SESSION_ENDED, (reopened.state.value as SessionState.NeedsCode).notice)
    }

    @Test
    fun decliningTheConsentDropsTheSessionAndAsksForACode() = runTest {
        val session = manager()
        session.activate("CODIGO12345")
        assertTrue(session.state.value is SessionState.NeedsConsent)

        session.abandon()

        assertEquals(SessionState.NeedsCode(), session.state.value)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertFalse(sessionVault.hasKey)
    }

    @Test
    fun anInvalidRefreshTokenEndsTheSessionWithANotice() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        api.refreshError = ApiException(401, "AUTH-016")

        val reopened = manager()
        reopened.start()

        val state = reopened.state.value as SessionState.NeedsCode
        assertNotNull(state.notice)
        assertNull(SessionStore(dir, sessionVault).load())
    }

    @Test
    fun withoutNetworkTheStoredSessionIsKeptAndShownAsOffline() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        api.refreshError = offline()

        val reopened = manager()
        reopened.start()

        assertEquals(SessionState.Active(offline = true), reopened.state.value)
        assertNotNull("nothing was dropped", SessionStore(dir, sessionVault).load())
    }

    @Test
    fun aWrongCodeLeavesTheAppAskingForTheCode() = runTest {
        val session = manager()
        session.start()
        api.activateError = ApiException(400, ApiException.CODE_INVALID_ACTIVATION)

        val error = runCatching { session.activate("MALOMALOMA") }.exceptionOrNull() as ApiException

        assertEquals("AUTH-014", error.code)
        assertTrue(session.state.value is SessionState.NeedsCode)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aRevocationAtStartupDropsTheSession() = runTest {
        manager().apply {
            activate("CODIGO12345")
            acceptConsent(locationAccepted = true)
        }
        api.refreshError = revoked()

        val reopened = manager()
        reopened.start()

        assertTrue(reopened.state.value is SessionState.NeedsCode)
        assertNull(SessionStore(dir, sessionVault).load())
    }
}
