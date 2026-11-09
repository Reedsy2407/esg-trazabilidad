package pe.esgtrazabilidad.reciclador

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CA-19 on the installed app: no backup, and clear traffic only to localhost in this debug build. */
class ManifestInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupIsOff() {
        val flags = context.packageManager.getApplicationInfo(context.packageName, 0).flags
        assertEquals("allowBackup must be false", 0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun theApplicationIdIsTheStableOne() {
        assertEquals("pe.esgtrazabilidad.reciclador", context.packageName)
    }

    @Test
    fun clearTrafficIsAllowedOnlyToLocalhostInTheDebugBuild() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("localhost"))
        assertFalse(policy.isCleartextTrafficPermitted("esg-auth-service.onrender.com"))
        assertFalse(policy.isCleartextTrafficPermitted("example.com"))
    }
}
