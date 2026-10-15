package com.naminghouse.app

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/** Check the installed APK, including manifest bindings, rather than only the source XML files. */
class BackupPolicyTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val privateDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    @Test
    fun installedAppKeepsNamingRecordsOutOfCloudBackupAndDeviceTransfer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse("Automatic backup must remain disabled",
            context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        var foundApplication = false
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { manifest ->
            while (manifest.next() != XmlPullParser.END_DOCUMENT) {
                if (manifest.eventType == XmlPullParser.START_TAG && manifest.name == "application") {
                    foundApplication = true
                    assertFalse(manifest.getAttributeBooleanValue(androidNamespace, "allowBackup", true))
                    assertEquals("Legacy backup must use our exclusion resource", R.xml.backup_rules,
                        manifest.getAttributeResourceValue(androidNamespace, "fullBackupContent", 0))
                    assertEquals("Android 12+ must use both cloud and device-transfer exclusions", R.xml.data_extraction_rules,
                        manifest.getAttributeResourceValue(androidNamespace, "dataExtractionRules", 0))
                }
            }
        }
        assertTrue("Installed app manifest was not read", foundApplication)

        assertEquals(mapOf("full-backup-content" to privateDomains),
            excludedDomains(context, R.xml.backup_rules))
        assertEquals(mapOf("cloud-backup" to privateDomains, "device-transfer" to privateDomains),
            excludedDomains(context, R.xml.data_extraction_rules))
    }

    private fun excludedDomains(context: Context, resource: Int): Map<String, Set<String>> {
        val groups = linkedMapOf<String, MutableSet<String>>()
        val sections = setOf("full-backup-content", "cloud-backup", "device-transfer")
        var current: String? = null
        context.resources.getXml(resource).use { rules ->
            while (rules.next() != XmlPullParser.END_DOCUMENT) {
                if (rules.eventType == XmlPullParser.START_TAG) {
                    when (val tag = rules.name) {
                        in sections -> {
                            assertTrue("Duplicate backup section: $tag", tag !in groups)
                            current = tag
                            groups[tag] = linkedSetOf()
                        }
                        "include" -> throw AssertionError("Explicit OS backup includes violate the local-only storage policy")
                        "exclude" -> {
                            val section = checkNotNull(current) { "Exclusion outside a backup section" }
                            val domain = rules.getAttributeValue(null, "domain")
                            assertEquals("Every private storage root must be excluded", ".", rules.getAttributeValue(null, "path"))
                            assertTrue("Unknown storage domain: $domain", domain in privateDomains)
                            assertTrue("Duplicate exclusion: $section/$domain", groups.getValue(section).add(domain))
                        }
                    }
                } else if (rules.eventType == XmlPullParser.END_TAG && rules.name == current) {
                    current = null
                }
            }
        }
        return groups
    }
}
