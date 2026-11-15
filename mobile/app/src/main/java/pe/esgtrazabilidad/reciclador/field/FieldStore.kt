package pe.esgtrazabilidad.reciclador.field

/**
 * OPEN: being worked. CLOSED: the recycler closed it on the phone and the server has not confirmed it yet.
 * REJECTED: the server says the workday is closed (COL-017) and refuses its points; they are kept, never sent again.
 */
enum class WorkdayStatus { OPEN, CLOSED, REJECTED }

/**
 * The workday on the phone. [clientId] is generated once and kept, so a retried "open" can never create a second one.
 * [recyclerId] is the opaque id of the account that started it (never a name). [nextSeq] is the next `deviceSeq`:
 * it lives in the same row as the points, so it keeps growing even if the process dies and comes back.
 * [recordingGps]: whether this workday records points (the recycler accepted the location AND the system gives it).
 */
data class WorkdayRow(
    val clientId: String,
    val serverId: String?,
    val recyclerId: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val status: WorkdayStatus,
    val recordingGps: Boolean,
    val nextSeq: Long,
    val rejectedAtMillis: Long? = null,
)

data class PointRow(val id: Long, val deviceSeq: Long, val recordedAtMillis: Long, val lat: Double, val lng: Double, val accuracyM: Double)

/**
 * What the phone keeps about the workday and its GPS points. The neighbours' data never goes here (it stays in the
 * sealed RouteCache). The real implementation is Room ([RoomFieldStore]); the tests also use one in memory.
 */
interface FieldStore {
    /** The workday in progress or waiting for the server (OPEN or CLOSED), if any. At most one exists. */
    suspend fun current(): WorkdayRow?

    suspend fun create(row: WorkdayRow)

    suspend fun setServerId(clientId: String, serverId: String?)

    suspend fun setRecyclerId(clientId: String, recyclerId: String)

    suspend fun setRecordingGps(clientId: String, recording: Boolean)

    suspend fun markClosed(clientId: String, endedAtMillis: Long)

    suspend fun markRejected(clientId: String, atMillis: Long)

    /** Stores a point with the next `deviceSeq` of the workday, atomically; returns that number. */
    suspend fun appendPoint(clientId: String, recordedAtMillis: Long, lat: Double, lng: Double, accuracyM: Double): Long

    /** The oldest [limit] points by `deviceSeq`. */
    suspend fun nextBatch(clientId: String, limit: Int): List<PointRow>

    suspend fun pointCount(clientId: String): Int

    /** Removes exactly these points (only after the server confirmed them). */
    suspend fun deletePoints(ids: List<Long>)

    /** Removes a workday that the server confirmed closed and has no points left. */
    suspend fun deleteWorkday(clientId: String)
}
