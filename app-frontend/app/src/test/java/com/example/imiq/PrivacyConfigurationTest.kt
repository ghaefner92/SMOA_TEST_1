package com.example.imiq

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConfigurationTest {
    private fun source(relativeToApp: String): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: error("Missing user.dir"))
        while (candidate != null) {
            val direct = File(candidate, relativeToApp)
            if (direct.exists()) return direct
            candidate = candidate.parentFile
        }
        error("Unable to locate $relativeToApp from ${System.getProperty("user.dir")}")
    }

    @Test fun productionManifestDisablesCleartextWhileLocalFlavorsScopeIt() {
        val main = source("app-frontend/app/src/main/AndroidManifest.xml").readText()
        val localUsb = source("app-frontend/app/src/localUsb/AndroidManifest.xml").readText()
        val localEmulator = source("app-frontend/app/src/localEmulator/AndroidManifest.xml").readText()

        assertTrue(main.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(localUsb.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue(localEmulator.contains("android:usesCleartextTraffic=\"true\""))
        assertFalse(main.contains("android:usesCleartextTraffic=\"true\""))
    }

    @Test fun passportFilesAreExcludedFromCloudBackupAndDeviceTransfer() {
        val backup = source("app-frontend/app/src/main/res/xml/backup_rules.xml").readText()
        val extraction = source("app-frontend/app/src/main/res/xml/data_extraction_rules.xml").readText()
        val names = listOf("cognitive_passport.json", "cognitive_passport_history")

        names.forEach { name ->
            assertTrue(backup.contains("path=\"$name\""))
            assertTrue(extraction.contains("<cloud-backup>") && extraction.contains("path=\"$name\""))
            assertTrue(extraction.contains("<device-transfer>") && extraction.contains("path=\"$name\""))
        }
    }

    @Test fun passportClearRemovesCurrentAndHistoryWithoutTouchingIndependentNicknameContract() {
        val root = Files.createTempDirectory("passport-clear-test").toFile()
        try {
            File(root, "cognitive_passport.json").writeText("current")
            File(root, "cognitive_passport_history").apply { mkdirs() }
                .resolve("revision_000001.json").writeText("history")
            File(root, "digital_companion_name").writeText("Nova")

            PassportStore.clearPassportFiles(root)

            assertFalse(File(root, "cognitive_passport.json").exists())
            assertFalse(File(root, "cognitive_passport_history").exists())
            assertTrue(File(root, "digital_companion_name").exists())
            assertTrue(DigitalCompanionStore.preferenceKey.isNotBlank())
        } finally {
            root.deleteRecursively()
        }
    }
}
