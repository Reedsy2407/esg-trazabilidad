package pe.esgtrazabilidad.reciclador.field

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "workday")
internal data class WorkdayEntity(
    @PrimaryKey val clientId: String,
    val serverId: String?,
    val recyclerId: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val status: String,
    val recordingGps: Boolean,
    val nextSeq: Long,
    val rejectedAtMillis: Long?,
)

@Entity(tableName = "gps_point", indices = [Index(value = ["workdayClientId", "deviceSeq"], unique = true)])
internal data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workdayClientId: String,
    val deviceSeq: Long,
    val recordedAtMillis: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
)

@Dao
internal abstract class FieldDao {
    @Query("select * from workday where status in ('OPEN','CLOSED') limit 1")
    abstract suspend fun current(): WorkdayEntity?

    @Insert
    abstract suspend fun insertWorkday(row: WorkdayEntity)

    @Query("update workday set serverId = :serverId where clientId = :clientId")
    abstract suspend fun setServerId(clientId: String, serverId: String?)

    @Query("update workday set recyclerId = :recyclerId where clientId = :clientId")
    abstract suspend fun setRecyclerId(clientId: String, recyclerId: String)

    @Query("update workday set recordingGps = :recording where clientId = :clientId")
    abstract suspend fun setRecordingGps(clientId: String, recording: Boolean)

    @Query("update workday set status = 'CLOSED', endedAtMillis = :endedAt where clientId = :clientId and status = 'OPEN'")
    abstract suspend fun markClosed(clientId: String, endedAt: Long)

    @Query("update workday set status = 'REJECTED', rejectedAtMillis = :at where clientId = :clientId")
    abstract suspend fun markRejected(clientId: String, at: Long)

    @Query("select nextSeq from workday where clientId = :clientId")
    abstract suspend fun nextSeq(clientId: String): Long?

    @Query("update workday set nextSeq = :next where clientId = :clientId")
    abstract suspend fun setNextSeq(clientId: String, next: Long)

    @Insert
    abstract suspend fun insertPoint(point: PointEntity)

    /** The counter and the point change together or not at all. */
    @Transaction
    open suspend fun appendPoint(clientId: String, recordedAt: Long, lat: Double, lng: Double, accuracy: Double): Long {
        val seq = nextSeq(clientId) ?: throw IllegalStateException("no such workday")
        insertPoint(PointEntity(workdayClientId = clientId, deviceSeq = seq, recordedAtMillis = recordedAt, lat = lat, lng = lng, accuracyM = accuracy))
        setNextSeq(clientId, seq + 1)
        return seq
    }

    @Query("select * from gps_point where workdayClientId = :clientId order by deviceSeq limit :limit")
    abstract suspend fun nextBatch(clientId: String, limit: Int): List<PointEntity>

    @Query("select count(*) from gps_point where workdayClientId = :clientId")
    abstract suspend fun pointCount(clientId: String): Int

    @Query("delete from gps_point where id in (:ids)")
    abstract suspend fun deletePoints(ids: List<Long>)

    @Query("delete from workday where clientId = :clientId")
    abstract suspend fun deleteWorkday(clientId: String)

}

/**
 * Private to the app (no backups, see the manifest). NOT encrypted: a known limitation, written in the closing report;
 * no neighbour data is stored here, only the workday and the recycler's own GPS points.
 */
@Database(entities = [WorkdayEntity::class, PointEntity::class], version = 1, exportSchema = false)
internal abstract class FieldDatabase : RoomDatabase() {
    abstract fun dao(): FieldDao

    companion object {
        const val NAME = "field.db"
    }
}

class RoomFieldStore private constructor(private val db: FieldDatabase) : FieldStore {

    constructor(context: Context) : this(Room.databaseBuilder(context.applicationContext, FieldDatabase::class.java, FieldDatabase.NAME).build())

    /** A database that lives only in memory, for the instrumented tests. */
    companion object {
        fun inMemory(context: Context): RoomFieldStore =
            RoomFieldStore(Room.inMemoryDatabaseBuilder(context.applicationContext, FieldDatabase::class.java).build())
    }

    private val dao get() = db.dao()

    fun close() = db.close()

    private fun WorkdayEntity.toRow() = WorkdayRow(
        clientId, serverId, recyclerId, startedAtMillis, endedAtMillis, WorkdayStatus.valueOf(status), recordingGps, nextSeq, rejectedAtMillis,
    )

    override suspend fun current() = dao.current()?.toRow()

    override suspend fun create(row: WorkdayRow) = dao.insertWorkday(
        WorkdayEntity(
            row.clientId, row.serverId, row.recyclerId, row.startedAtMillis, row.endedAtMillis, row.status.name,
            row.recordingGps, row.nextSeq, row.rejectedAtMillis,
        ),
    )

    override suspend fun setServerId(clientId: String, serverId: String?) = dao.setServerId(clientId, serverId)

    override suspend fun setRecyclerId(clientId: String, recyclerId: String) = dao.setRecyclerId(clientId, recyclerId)

    override suspend fun setRecordingGps(clientId: String, recording: Boolean) = dao.setRecordingGps(clientId, recording)

    override suspend fun markClosed(clientId: String, endedAtMillis: Long) = dao.markClosed(clientId, endedAtMillis)

    override suspend fun markRejected(clientId: String, atMillis: Long) = dao.markRejected(clientId, atMillis)

    override suspend fun appendPoint(clientId: String, recordedAtMillis: Long, lat: Double, lng: Double, accuracyM: Double) =
        dao.appendPoint(clientId, recordedAtMillis, lat, lng, accuracyM)

    override suspend fun nextBatch(clientId: String, limit: Int) =
        dao.nextBatch(clientId, limit).map { PointRow(it.id, it.deviceSeq, it.recordedAtMillis, it.lat, it.lng, it.accuracyM) }

    override suspend fun pointCount(clientId: String) = dao.pointCount(clientId)

    override suspend fun deletePoints(ids: List<Long>) {
        // SQLite caps the number of variables in one statement; the batches here are at most 500.
        ids.chunked(500).forEach { dao.deletePoints(it) }
    }

    override suspend fun deleteWorkday(clientId: String) = dao.deleteWorkday(clientId)

}
