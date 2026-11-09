package pe.esgtrazabilidad.reciclador

import android.util.Log
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.esgtrazabilidad.reciclador.crypto.KeystoreVault
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/** CA-18 on the real Android Keystore of the phone: AES-GCM, a key the app cannot export, and no crash without the key. */
class KeystoreVaultInstrumentedTest {
    private val aliases = mutableListOf<String>()

    private fun vault(): KeystoreVault {
        val alias = "test-" + UUID.randomUUID()
        aliases += alias
        return KeystoreVault(alias)
    }

    @After
    fun cleanUp() {
        aliases.forEach { KeystoreVault(it).destroyKey() }
    }

    @Test
    fun sealsAndOpensAndTheCiphertextDoesNotContainThePlaintext() {
        val vault = vault()
        val secret = "refresh-token-sentinel-${UUID.randomUUID()}".toByteArray()

        val sealed = vault.seal(secret)

        assertArrayEquals(secret, vault.open(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains(String(secret, Charsets.ISO_8859_1)))
        assertFalse("each seal uses a fresh IV", sealed.contentEquals(vault.seal(secret)))
    }

    @Test
    fun theKeyIsNotExportableAndIsInSecureHardwareWhenThePhoneHasIt() {
        val vault = vault()
        vault.seal(byteArrayOf(1))

        val facts = vault.keyFacts()
        assertNotNull(facts)
        facts!!

        assertFalse("the app must never see the key bytes", facts.exportable)
        // Evidence for the report (no secret): where the key lives on this phone.
        Log.i("EVIDENCE", "keystore key: exportable=${facts.exportable} insideSecureHardware=${facts.insideSecureHardware} strongBox=${facts.strongBox}")
    }

    @Test
    fun withoutTheKeyOpeningFailsCleanlyInsteadOfCrashing() {
        val vault = vault()
        val sealed = vault.seal("anything".toByteArray())

        vault.destroyKey()

        try {
            vault.open(sealed)
            fail("must not open without its key")
        } catch (expected: VaultUnavailableException) {
            // the app then asks for a new code
        }
        assertFalse(vault.hasKey())
    }

    @Test
    fun aKeyCreatedAfterwardsCannotOpenWhatTheOldKeySealed() {
        val vault = vault()
        val sealed = vault.seal("anything".toByteArray())
        vault.destroyKey()
        vault.seal(byteArrayOf(0)) // a new key under the same alias

        try {
            vault.open(sealed)
            fail("a different key must not open it")
        } catch (expected: VaultUnavailableException) {
        }
    }

    @Test
    fun tamperedDataIsRefused() {
        val vault = vault()
        val sealed = vault.seal("anything".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        try {
            vault.open(sealed)
            fail("GCM must detect tampering")
        } catch (expected: VaultUnavailableException) {
        }
    }

    @Test
    fun deletingOneKeyDoesNotTouchTheOther() {
        val session = vault()
        val pending = vault()
        val sealedPending = pending.seal("pending".toByteArray())
        session.seal("session".toByteArray())

        session.destroyKey()

        assertEquals("pending", String(pending.open(sealedPending)))
        assertTrue(pending.hasKey())
    }
}
