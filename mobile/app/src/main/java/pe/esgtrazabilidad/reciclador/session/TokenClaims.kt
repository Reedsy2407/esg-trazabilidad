package pe.esgtrazabilidad.reciclador.session

import java.util.Base64
import org.json.JSONException
import org.json.JSONObject

/**
 * What the app reads from its own access token to decide which screen to show. It does NOT verify the
 * signature (the services do that on every call); it only reads `cv` (consent version accepted, absent when
 * a new consent is pending) and `lc` (location accepted).
 */
data class TokenClaims(val consentVersion: Int?, val locationAccepted: Boolean) {
    companion object {
        fun parse(accessToken: String): TokenClaims {
            return try {
                val payload = accessToken.split('.').getOrNull(1) ?: return TokenClaims(null, false)
                val json = JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
                TokenClaims(
                    consentVersion = if (json.has("cv") && !json.isNull("cv")) json.getInt("cv") else null,
                    locationAccepted = json.optBoolean("lc", false),
                )
            } catch (e: IllegalArgumentException) {
                TokenClaims(null, false)
            } catch (e: JSONException) {
                TokenClaims(null, false)
            }
        }
    }
}
