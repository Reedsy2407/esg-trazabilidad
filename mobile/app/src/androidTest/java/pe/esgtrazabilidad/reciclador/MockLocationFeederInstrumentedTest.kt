package pe.esgtrazabilidad.reciclador

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A hand-test helper, not a check: while it runs it plays a walker on the phone's GPS provider (a test provider, so
 * the Honor can stay on a desk). It runs inside the app's process, so the foreground service listens to it as to the
 * real GPS. Needs `adb shell appops set pe.esgtrazabilidad.reciclador android:mock_location allow` and
 * `-e feed <seconds>`; without that argument it does nothing.
 */
class MockLocationFeederInstrumentedTest {

    @Test
    fun walkForTheGivenSeconds() {
        val seconds = InstrumentationRegistry.getArguments().getString("feed")?.toIntOrNull()
        assumeTrue("run with -e feed <seconds>", seconds != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = LocationManager.GPS_PROVIDER
        try {
            manager.removeTestProvider(provider)
        } catch (e: IllegalArgumentException) {
            // none to remove
        }
        @Suppress("DEPRECATION")
        manager.addTestProvider(provider, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(provider, true)
        try {
            val end = SystemClock.elapsedRealtime() + seconds!! * 1000L
            var step = 0
            while (SystemClock.elapsedRealtime() < end) {
                val location = Location(provider).apply {
                    // Walking north along a street of Miraflores, a few metres per step.
                    latitude = -12.1211 + step * 0.00003
                    longitude = -77.0297
                    accuracy = 8f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                manager.setTestProviderLocation(provider, location)
                step++
                Thread.sleep(STEP_MILLIS)
            }
        } finally {
            manager.removeTestProvider(provider)
        }
    }

    private companion object {
        const val STEP_MILLIS = 5_000L
    }
}
