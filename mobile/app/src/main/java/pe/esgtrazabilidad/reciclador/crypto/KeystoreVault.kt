package pe.esgtrazabilidad.reciclador.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key from the Android Keystore: created non-exportable (the app never sees the key
 * bytes), randomized IV chosen by the Keystore, in StrongBox when the phone has one. A sealed value is
 * `[ivLength][iv][ciphertext+tag]`. Two instances with different aliases are independent: deleting one key
 * does not touch the other (the session key is wiped on logout; the pending-records key is not).
 */
class KeystoreVault(private val alias: String) : Vault {

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        try {
            if (sealed.size < 2) throw VaultUnavailableException("sealed value too short")
            val ivLength = sealed[0].toInt() and 0xFF
            if (ivLength != IV_BYTES || sealed.size <= 1 + ivLength) throw VaultUnavailableException("bad envelope")
            val key = existingKey() ?: throw VaultUnavailableException("key missing")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 1, ivLength))
            return cipher.doFinal(sealed, 1 + ivLength, sealed.size - 1 - ivLength)
        } catch (e: VaultUnavailableException) {
            throw e
        } catch (e: AEADBadTagException) {
            // Wrong key or tampered data.
            throw VaultUnavailableException("cannot open", e)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw VaultUnavailableException("key invalidated", e)
        } catch (e: UnrecoverableKeyException) {
            throw VaultUnavailableException("key unrecoverable", e)
        } catch (e: ProviderException) {
            // The Keystore failed this time: not a statement about the data. Nothing may be deleted because of it.
            throw VaultTransientException("keystore failed", e)
        } catch (e: KeyStoreException) {
            throw VaultTransientException("keystore failed", e)
        } catch (e: IOException) {
            throw VaultTransientException("keystore failed", e)
        } catch (e: Exception) {
            // Anything not known to be permanent is treated as transient: wrongly calling a passing failure
            // "permanent" would make the stores delete data that is still readable.
            throw VaultTransientException("cannot open right now", e)
        }
    }

    override fun destroyKey() {
        try {
            val store = keyStore()
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        } catch (e: Exception) {
            // Callers delete the sealed file first, so a key left behind is unreadable garbage, not a risk.
            throw VaultTransientException("could not delete the key", e)
        }
    }

    fun hasKey(): Boolean = keyStore().containsAlias(alias)

    /** Facts about the key for the evidence of CA-18; never the key itself. */
    data class KeyFacts(val exportable: Boolean, val insideSecureHardware: Boolean, val strongBox: Boolean?)

    fun keyFacts(): KeyFacts? {
        val key = existingKey() ?: return null
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val strongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val secure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            info.isInsideSecureHardware
        }
        return KeyFacts(exportable = key.encoded != null, insideSecureHardware = secure, strongBox = strongBox)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun createKey(): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(strongBox = true)
            } catch (e: ProviderException) {
                // No StrongBox on this phone (StrongBoxUnavailableException is a ProviderException): use the TEE.
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
