package pe.esgtrazabilidad.reciclador

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.session.DiscardTicket
import pe.esgtrazabilidad.reciclador.session.NothingUploadable
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.PendingRecord
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore
import pe.esgtrazabilidad.reciclador.ui.LogoutDialog
import pe.esgtrazabilidad.reciclador.ui.LogoutDialogs
import pe.esgtrazabilidad.reciclador.ui.RecicladorTheme
import pe.esgtrazabilidad.reciclador.ui.RevokedScreen

/** CA-20 and CA-21 on screen: the count, the three ways out, and the second confirmation that repeats the number. */
class LogoutScreensInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val tag = UUID.randomUUID().toString().take(8)
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "screens-$tag").apply { mkdirs() }
    private val sessionVault = KeystoreVault("test-screens-session-$tag")
    private val pendingVault = KeystoreVault("test-screens-pending-$tag")

    @After
    fun tearDown() {
        dir.deleteRecursively()
        sessionVault.destroyKey()
        pendingVault.destroyKey()
    }

    /** A real ticket, as the first step of a discard hands it out: only the manager can make one. */
    private fun ticketFor(count: Int): DiscardTicket {
        val pending = PendingStore(dir, pendingVault)
        repeat(count) { pending.add(PendingRecord("c-$it", 1L + it, "1.0", "T", "v-$it")) }
        val unused = object : AuthApi {
            override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String): Tokens = error("unused")
            override suspend fun refresh(refreshToken: String): Tokens = error("unused")
            override suspend fun consent(accessToken: String): Consent = error("unused")
            override suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean) = error("unused")
            override suspend fun logout(accessToken: String, refreshToken: String) = error("unused")
        }
        val manager = SessionManager(
            unused, SessionStore(dir, sessionVault), RouteCache(dir, sessionVault, TimeSource.System), pending,
            NothingUploadable, TimeSource.System, "Honor", dir,
        )
        return runBlocking { manager.beginDiscard() }
    }

    /** Shows the dialog and waits, by condition, until its window has a hierarchy that can be queried. */
    private fun dialogs(dialog: LogoutDialog?, busy: Boolean = false, error: String? = null) {
        compose.setContent {
            RecicladorTheme {
                LogoutDialogs(
                    dialog = dialog,
                    busy = busy,
                    error = error,
                    onSync = { calls += "sync" },
                    onCancel = { calls += "cancel" },
                    onAskDiscard = { calls += "askDiscard" },
                    onConfirmDiscard = { calls += "confirmDiscard(${it.count})" },
                    onBackFromDiscard = { calls += "back" },
                    onDismissFailure = { calls += "dismiss" },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    @Test
    fun withPendingRecordsItShowsTheCountAndThreeOptionsAndDeletesNothingByItself() {
        dialogs(LogoutDialog.Decision(3))

        compose.onNodeWithText("3 registros sin subir").assertExists()
        compose.onNodeWithText("Sincronizar ahora").assertExists()
        compose.onNodeWithText("Cancelar").assertExists()
        compose.onNodeWithText("Descartar…").performClick()

        assertEquals(listOf("askDiscard"), calls)
    }

    @Test
    fun oneRecordIsWrittenInTheSingular() {
        dialogs(LogoutDialog.Decision(1))
        compose.onNodeWithText("1 registro sin subir").assertExists()
    }

    @Test
    fun theSecondConfirmationRepeatsTheNumberAndSaysItCannotBeUndone() {
        dialogs(LogoutDialog.ConfirmDiscard(ticketFor(3)))

        compose.onNodeWithText("¿Descartar 3 registros?").assertExists()
        compose.onNodeWithText("Vas a borrar 3 registros sin subir de este teléfono. Esto no se puede deshacer.").assertExists()
        compose.onNodeWithText("Sí, descartar 3 registros").performClick()
        compose.onNodeWithText("No, volver").performClick()

        assertEquals(listOf("confirmDiscard(3)", "back"), calls)
    }

    @Test
    fun anErrorShowsInsideTheDialogNotBehindIt() {
        dialogs(LogoutDialog.ConfirmDiscard(ticketFor(2)), error = "No se pudo guardar o leer los datos seguros del teléfono.")
        compose.onNodeWithText("No se pudo guardar o leer los datos seguros del teléfono.").assertExists()
    }

    @Test
    fun whileSyncingTheOptionsCannotBePressedAgain() {
        dialogs(LogoutDialog.Decision(2), busy = true)

        compose.onNodeWithText("Sincronizando…").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        compose.onNodeWithText("Descartar…").assertIsNotEnabled()
    }

    @Test
    fun aFailedSyncSaysHowManyRemain() {
        dialogs(LogoutDialog.SyncFailed(2, "No hay conexión con el servidor."))

        compose.onNodeWithText("Quedan 2 registros sin subir. No hay conexión con el servidor.").assertExists()
        compose.onNodeWithText("Entendido").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun theRevokedNoticeIsBlockingAndOffersTwoWaysOutNeverAnAutomaticDiscard() {
        compose.setContent {
            RecicladorTheme {
                RevokedScreen(count = 4, error = "La cantidad de registros cambió. Vuelve a intentarlo.", onNewCode = { calls += "newCode" }, onDiscard = { calls += "discard" })
            }
        }

        compose.onNodeWithText("4 registros sin subir; la sesión fue revocada").assertExists()
        compose.onNodeWithText("Nada se descarta de forma automática.").assertExists()
        compose.onNodeWithText("La cantidad de registros cambió. Vuelve a intentarlo.").assertExists()
        compose.onNodeWithText("Pedir un código nuevo al personal").performClick()
        compose.onNodeWithText("Descartar…").performClick()

        assertEquals(listOf("newCode", "discard"), calls)
    }
}
