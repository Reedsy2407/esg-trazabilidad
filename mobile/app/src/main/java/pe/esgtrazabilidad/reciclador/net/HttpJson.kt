package pe.esgtrazabilidad.reciclador.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** A successful answer: its status (200, 201...) and its body as text (empty when there is none). */
class HttpAnswer(val status: Int, val text: String)

/**
 * The one JSON-over-HTTP routine of the app (HttpURLConnection, no third-party client). Neither the request nor
 * the answer is ever written to a log: they carry tokens, coordinates and neighbours' names.
 * Any non-2xx becomes an [ApiException] with the service's `code`; no answer at all is status 0.
 */
internal object HttpJson {
    private const val TIMEOUT_MS = 10_000

    // Render's free plan wakes a sleeping service in up to a minute.
    private const val READ_TIMEOUT_MS = 60_000

    suspend fun request(baseUrl: String, method: String, path: String, body: String?, bearer: String?): HttpAnswer =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection)
            } catch (e: IOException) {
                throw ApiException(0, null, "no connection")
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw ApiException(status, errorCode(text))
                HttpAnswer(status, text)
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException(0, null, "no connection")
            } finally {
                connection.disconnect()
            }
        }

    private fun errorCode(body: String): String? = try {
        JSONObject(body).optString("code").takeIf { it.isNotEmpty() }
    } catch (e: JSONException) {
        null
    }
}
