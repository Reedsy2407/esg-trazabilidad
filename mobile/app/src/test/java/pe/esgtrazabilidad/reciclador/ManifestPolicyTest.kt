package pe.esgtrazabilidad.reciclador

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CA-19 as a check of the build itself: the manifest and the extraction rules are read from the sources, so a change
 * that turns backup on, opens clear traffic in the release build, or stops excluding a storage domain fails here
 * (unit tests run with the module folder as working directory).
 */
class ManifestPolicyTest {
    private fun source(path: String): String = File(path).also { assertTrue("missing $path", it.exists()) }.readText()

    private val domains = listOf("root", "file", "database", "sharedpref", "external")

    @Test
    fun mainManifestHasNoBackupAndNoClearTraffic() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertFalse("clear traffic must not be enabled in main", manifest.contains("usesCleartextTraffic=\"true\""))
        assertFalse("network config belongs to the debug build only", manifest.contains("networkSecurityConfig"))
        assertTrue(manifest.contains("@xml/data_extraction_rules"))
        assertTrue(manifest.contains("@xml/backup_rules"))
    }

    @Test
    fun bothExtractionRuleFilesExcludeEveryDomain() {
        for (file in listOf("src/main/res/xml/data_extraction_rules.xml", "src/main/res/xml/backup_rules.xml")) {
            val rules = source(file)
            domains.forEach { assertTrue("$file must exclude $it", rules.contains("""<exclude domain="$it" />""")) }
            assertFalse("$file must not include anything", rules.contains("<include"))
        }
        // The data extraction rules cover both the cloud backup and the device transfer.
        val dataRules = source("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(dataRules.contains("<cloud-backup>") && dataRules.contains("<device-transfer>"))
    }

    @Test
    fun clearTrafficIsAllowedOnlyToLocalhostAndOnlyInDebug() {
        val debug = source("src/debug/res/xml/network_security_config.xml")
        assertTrue(debug.contains("<domain includeSubdomains=\"false\">localhost</domain>"))
        assertFalse(debug.contains("<base-config"))
        assertFalse("no other host may be opened", Regex("<domain[^>]*>(?!localhost)[^<]+</domain>").containsMatchIn(debug))
        assertFalse(File("src/release").resolve("res/xml/network_security_config.xml").exists())
    }

    @Test
    fun theReleaseBuildPointsToHttpsAndDebugToLocalhost() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("https://esg-auth-service.onrender.com"))
        assertTrue(gradle.contains("http://localhost:8084"))
        assertFalse("no clear URL besides localhost", Regex("http://(?!localhost)").containsMatchIn(gradle))
    }
}
