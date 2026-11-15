package pe.esgtrazabilidad.reciclador.net

import org.json.JSONException
import org.json.JSONObject

/**
 * The auth-service routes the app uses, over plain HttpURLConnection and org.json: no third-party client
 * and nothing that logs bodies. No request or answer is ever written to a log (they carry tokens).
 */
class HttpAuthApi(private val baseUrl: String) : AuthApi {

    override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String): Tokens {
        val body = JSONObject().put("code", code).put("deviceLabel", deviceLabel).put("deviceInstallId", deviceInstallId)
        val json = call("POST", "/auth/recycler/activate", body, bearer = null)
        return Tokens(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresInSeconds = json.getLong("expiresIn"),
            consentRequired = json.optBoolean("consentRequired", false),
            consentVersion = if (json.has("consentVersion") && !json.isNull("consentVersion")) json.getInt("consentVersion") else null,
        )
    }

    override suspend fun refresh(refreshToken: String): Tokens {
        val json = call("POST", "/auth/recycler/refresh", JSONObject().put("refreshToken", refreshToken), bearer = null)
        return Tokens(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresInSeconds = json.getLong("expiresIn"),
        )
    }

    override suspend fun consent(accessToken: String): Consent {
        val json = call("GET", "/auth/recycler/consent", body = null, bearer = accessToken)
        val retention = json.getJSONObject("retention")
        return Consent(
            version = json.getInt("version"),
            text = json.getString("text"),
            locationText = json.getString("locationText"),
            gpsDays = retention.getInt("gpsDays"),
            photoMonths = retention.getInt("photoMonths"),
        )
    }

    override suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean) {
        val body = JSONObject().put("version", version).put("accepted", accepted).put("locationAccepted", locationAccepted)
        call("POST", "/auth/recycler/consent", body, bearer = accessToken)
    }

    override suspend fun logout(accessToken: String, refreshToken: String) {
        call("POST", "/auth/recycler/logout", JSONObject().put("refreshToken", refreshToken), bearer = accessToken)
    }

    private suspend fun call(method: String, path: String, body: JSONObject?, bearer: String?): JSONObject {
        val answer = HttpJson.request(baseUrl, method, path, body?.toString(), bearer)
        return try {
            if (answer.text.isBlank()) JSONObject() else JSONObject(answer.text)
        } catch (e: JSONException) {
            throw ApiException(502, null, "unreadable answer")
        }
    }
}
