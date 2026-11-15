package pe.esgtrazabilidad.reciclador.field

/** The same contract as the Room store, in memory, for the JVM tests of the workday logic. */
class InMemoryFieldStore : FieldStore {
    val workdays = mutableMapOf<String, WorkdayRow>()
    val points = mutableMapOf<Long, Pair<String, PointRow>>()
    private var nextId = 1L

    override suspend fun current() = workdays.values.firstOrNull { it.status != WorkdayStatus.REJECTED }

    override suspend fun create(row: WorkdayRow) {
        workdays[row.clientId] = row
    }

    private fun update(clientId: String, change: (WorkdayRow) -> WorkdayRow) {
        workdays[clientId]?.let { workdays[clientId] = change(it) }
    }

    override suspend fun setServerId(clientId: String, serverId: String?) = update(clientId) { it.copy(serverId = serverId) }

    override suspend fun setRecyclerId(clientId: String, recyclerId: String) = update(clientId) { it.copy(recyclerId = recyclerId) }

    override suspend fun setRecordingGps(clientId: String, recording: Boolean) = update(clientId) { it.copy(recordingGps = recording) }

    override suspend fun markClosed(clientId: String, endedAtMillis: Long) =
        update(clientId) { if (it.status == WorkdayStatus.OPEN) it.copy(status = WorkdayStatus.CLOSED, endedAtMillis = endedAtMillis) else it }

    override suspend fun markRejected(clientId: String, atMillis: Long) =
        update(clientId) { it.copy(status = WorkdayStatus.REJECTED, rejectedAtMillis = atMillis) }

    override suspend fun appendPoint(clientId: String, recordedAtMillis: Long, lat: Double, lng: Double, accuracyM: Double): Long {
        val w = workdays[clientId] ?: throw IllegalStateException("no such workday")
        val seq = w.nextSeq
        workdays[clientId] = w.copy(nextSeq = seq + 1)
        val id = nextId++
        points[id] = clientId to PointRow(id, seq, recordedAtMillis, lat, lng, accuracyM)
        return seq
    }

    override suspend fun nextBatch(clientId: String, limit: Int) =
        points.values.filter { it.first == clientId }.map { it.second }.sortedBy { it.deviceSeq }.take(limit)

    override suspend fun pointCount(clientId: String) = points.values.count { it.first == clientId }

    override suspend fun deletePoints(ids: List<Long>) {
        ids.forEach { points.remove(it) }
    }

    override suspend fun deleteWorkday(clientId: String) {
        workdays.remove(clientId)
    }
}
