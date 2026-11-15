package pe.esgtrazabilidad.reciclador.net

/** A neighbour as the recycler sees them: id, name, address and district. Never a phone (CA-32). */
data class RouteNeighbor(val id: String, val fullName: String, val address: String, val district: String)

/** One stop of the route: a schedule assigned to this recycler. [dayOfWeek] is MONDAY..SUNDAY, [time] is HH:mm. */
data class RouteStop(val scheduleId: String, val dayOfWeek: String, val time: String, val neighbor: RouteNeighbor)

/** One GPS reading, numbered by the phone ([deviceSeq], strictly increasing per workday and device). */
data class GpsPointDto(val deviceSeq: Long, val recordedAtMillis: Long, val lat: Double, val lng: Double, val accuracyM: Double)

/** [created] is true for a 201 (new workday) and false for a 200 (that clientId was already open). */
data class OpenedWorkday(val workdayId: String, val created: Boolean)

data class PointsAck(val accepted: Int, val duplicates: Int)

/** The collection-service routes the app uses (spec B2). Every call takes the recycler's access token. */
interface CollectionApi {
    @Throws(ApiException::class)
    suspend fun route(accessToken: String): List<RouteStop>

    /** POST /workdays, idempotent by [clientId]. */
    @Throws(ApiException::class)
    suspend fun openWorkday(accessToken: String, startedAtMillis: Long, clientId: String): OpenedWorkday

    /** POST /workdays/{id}/points, at most 500 points (CA-34); a 409 COL-017 means the workday is closed. */
    @Throws(ApiException::class)
    suspend fun sendPoints(accessToken: String, workdayId: String, points: List<GpsPointDto>): PointsAck

    /** POST /workdays/{id}/close; closing twice answers the same. */
    @Throws(ApiException::class)
    suspend fun closeWorkday(accessToken: String, workdayId: String, endedAtMillis: Long)
}
