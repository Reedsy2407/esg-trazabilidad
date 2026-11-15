package pe.esgtrazabilidad.reciclador.net

import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** collection-service over [HttpJson]; nothing it sends or receives is logged. */
class HttpCollectionApi(private val baseUrl: String) : CollectionApi {

    override suspend fun route(accessToken: String): List<RouteStop> {
        val answer = HttpJson.request(baseUrl, "GET", "/me/route", body = null, bearer = accessToken)
        return try {
            RouteJson.parse(answer.text)
        } catch (e: JSONException) {
            throw ApiException(502, null, "unreadable answer")
        }
    }

    override suspend fun openWorkday(accessToken: String, startedAtMillis: Long, clientId: String): OpenedWorkday {
        val body = JSONObject().put("startedAt", Instant.ofEpochMilli(startedAtMillis).toString()).put("clientId", clientId)
        val answer = HttpJson.request(baseUrl, "POST", "/workdays", body.toString(), accessToken)
        return try {
            OpenedWorkday(JSONObject(answer.text).getString("workdayId"), created = answer.status == 201)
        } catch (e: JSONException) {
            throw ApiException(502, null, "unreadable answer")
        }
    }

    override suspend fun sendPoints(accessToken: String, workdayId: String, points: List<GpsPointDto>): PointsAck {
        val array = JSONArray()
        points.forEach {
            array.put(
                JSONObject()
                    .put("lat", it.lat)
                    .put("lng", it.lng)
                    .put("accuracyM", it.accuracyM)
                    .put("recordedAt", Instant.ofEpochMilli(it.recordedAtMillis).toString())
                    .put("deviceSeq", it.deviceSeq),
            )
        }
        val answer = HttpJson.request(baseUrl, "POST", "/workdays/$workdayId/points", JSONObject().put("points", array).toString(), accessToken)
        return try {
            val json = JSONObject(answer.text)
            PointsAck(json.getInt("accepted"), json.getInt("duplicates"))
        } catch (e: JSONException) {
            throw ApiException(502, null, "unreadable answer")
        }
    }

    override suspend fun closeWorkday(accessToken: String, workdayId: String, endedAtMillis: Long) {
        val body = JSONObject().put("endedAt", Instant.ofEpochMilli(endedAtMillis).toString())
        HttpJson.request(baseUrl, "POST", "/workdays/$workdayId/close", body.toString(), accessToken)
    }
}

/** The route as JSON text: the server's answer and, identical, what the sealed cache keeps. */
object RouteJson {
    fun parse(text: String): List<RouteStop> {
        val array = JSONArray(text)
        return List(array.length()) { i ->
            val stop = array.getJSONObject(i)
            val neighbor = stop.getJSONObject("neighbor")
            RouteStop(
                scheduleId = stop.getString("scheduleId"),
                dayOfWeek = stop.getString("dayOfWeek"),
                time = hourAndMinute(stop.get("time")),
                neighbor = RouteNeighbor(
                    id = neighbor.getString("id"),
                    fullName = neighbor.getString("fullName"),
                    address = neighbor.getString("address"),
                    district = neighbor.getString("district"),
                ),
            )
        }
    }

    /** "08:30:00" or "08:30" (or the array form [8,30]) become "08:30". */
    private fun hourAndMinute(value: Any): String =
        if (value is JSONArray) "%02d:%02d".format(value.getInt(0), value.getInt(1)) else value.toString().take(5)

    fun write(stops: List<RouteStop>): String {
        val array = JSONArray()
        stops.forEach {
            array.put(
                org.json.JSONObject()
                    .put("scheduleId", it.scheduleId)
                    .put("dayOfWeek", it.dayOfWeek)
                    .put("time", it.time)
                    .put(
                        "neighbor",
                        org.json.JSONObject()
                            .put("id", it.neighbor.id)
                            .put("fullName", it.neighbor.fullName)
                            .put("address", it.neighbor.address)
                            .put("district", it.neighbor.district),
                    ),
            )
        }
        return array.toString()
    }
}
