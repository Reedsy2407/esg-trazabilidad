package pe.esgtrazabilidad.reciclador.storage

import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/**
 * What the recycler recorded and the server has not confirmed yet. Only an opaque neighbour id: never the
 * neighbour's name, address, district or phone (CA-24). Sealed with its OWN key, which logging out or being
 * revoked does not touch: the records survive a session wipe and are deleted only after the server confirmed
 * them or after the recycler discarded them with the double confirmation (R18).
 */
data class PendingRecord(
    val id: String,
    val recordedAtMillis: Long,
    val weightKg: String,
    val wasteType: String,
    val neighborId: String,
)

class PendingStore(dir: File, private val vault: Vault) {

    private val file = SealedFile(File(dir, FILE_NAME), vault)

    /**
     * The pending records. An unreadable file (its key is gone) cannot be recovered by anyone, so it is
     * removed and reads as empty.
     */
    @Synchronized
    fun list(): List<PendingRecord> = try {
        val bytes = file.read()
        if (bytes == null) {
            emptyList()
        } else {
            val array = JSONArray(String(bytes, Charsets.UTF_8))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                PendingRecord(
                    id = o.getString("id"),
                    recordedAtMillis = o.getLong("recordedAt"),
                    weightKg = o.getString("weightKg"),
                    wasteType = o.getString("wasteType"),
                    neighborId = o.getString("neighborId"),
                )
            }
        }
    } catch (e: VaultUnavailableException) {
        file.delete()
        emptyList()
    } catch (e: JSONException) {
        file.delete()
        emptyList()
    }

    fun count(): Int = list().size

    @Synchronized
    fun add(record: PendingRecord) = write(list() + record)

    /** Removes exactly the records the server confirmed; the others stay. */
    @Synchronized
    fun removeConfirmed(confirmedIds: Set<String>) {
        val remaining = list().filterNot { it.id in confirmedIds }
        if (remaining.isEmpty()) discardAll() else write(remaining)
    }

    /** Deletes the records and their key. Callers reach this only after a confirmation (see SessionManager). */
    @Synchronized
    fun discardAll() {
        file.delete()
        try {
            vault.destroyKey()
        } catch (e: VaultTransientException) {
            // The records are already gone, so the key left behind opens nothing.
        }
    }

    private fun write(records: List<PendingRecord>) {
        val array = JSONArray()
        records.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("recordedAt", it.recordedAtMillis)
                    .put("weightKg", it.weightKg)
                    .put("wasteType", it.wasteType)
                    .put("neighborId", it.neighborId),
            )
        }
        file.write(array.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val FILE_NAME = "pending.bin"
    }
}
