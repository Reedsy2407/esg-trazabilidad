package pe.esgtrazabilidad.reciclador

/** The clock, injectable so expiry (access token, 7-day route cache) is tested with a controlled time. */
fun interface TimeSource {
    fun nowMillis(): Long

    companion object {
        val System = TimeSource { java.lang.System.currentTimeMillis() }
    }
}
