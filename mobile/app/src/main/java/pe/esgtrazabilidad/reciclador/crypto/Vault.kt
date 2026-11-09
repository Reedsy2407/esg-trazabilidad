package pe.esgtrazabilidad.reciclador.crypto

/**
 * Seals bytes with a key that never leaves the secure store.
 *
 * [open] fails in two different ways, and callers must not confuse them:
 *  - [VaultUnavailableException]: the key is gone, was invalidated, or the data was tampered with or restored
 *    without its key. It is permanent: what was sealed cannot be read by anyone, so callers treat it as
 *    "nothing stored" and ask for a new code (CA-18).
 *  - [VaultTransientException]: the Keystore itself failed this time (busy, restarting). Nothing is known about
 *    the data, so callers must NOT delete anything; trying again later may work.
 */
interface Vault {
    fun seal(plain: ByteArray): ByteArray

    @Throws(VaultUnavailableException::class, VaultTransientException::class)
    fun open(sealed: ByteArray): ByteArray

    /**
     * Deletes the key. Anything sealed with it is unreadable from then on. May throw [VaultTransientException]:
     * callers delete the sealed file first, so a key left behind protects nothing and does no harm.
     */
    @Throws(VaultTransientException::class)
    fun destroyKey()
}

class VaultUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

class VaultTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)
