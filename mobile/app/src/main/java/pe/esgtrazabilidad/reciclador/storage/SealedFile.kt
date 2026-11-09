package pe.esgtrazabilidad.reciclador.storage

import java.io.File
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/** A file that only ever holds sealed bytes: written next to itself and renamed, so a crash never leaves half a file. */
class SealedFile(private val file: File, private val vault: Vault) {

    private val temp = File(file.parentFile, file.name + ".tmp")

    /** Null when there is no file; throws when it exists but cannot be opened with its key. */
    @Throws(VaultUnavailableException::class)
    fun read(): ByteArray? = if (file.exists()) vault.open(file.readBytes()) else null

    fun write(plain: ByteArray) {
        file.parentFile?.mkdirs()
        temp.writeBytes(vault.seal(plain))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "could not replace ${file.name}" }
        }
    }

    fun delete() {
        file.delete()
        temp.delete()
    }
}
