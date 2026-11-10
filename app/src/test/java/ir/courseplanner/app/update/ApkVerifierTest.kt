package ir.courseplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The archive gate that runs after the bytes arrive and before the installer is
 * ever opened. Pure JVM: [ApkVerifier.validate] takes the metadata, so every
 * rejection reason is testable without an APK file.
 */
class ApkVerifierTest {

    private val packageName = "ir.courseplanner.app"

    @Test
    fun `an archive the platform cannot read is rejected`() {
        assertEquals(
            UpdateFailure.NOT_AN_APK,
            ApkVerifier.validate(info = null, expectedPackage = packageName, currentVersionCode = 38L)
        )
    }

    @Test
    fun `an archive for another app is rejected`() {
        assertEquals(
            UpdateFailure.WRONG_PACKAGE,
            ApkVerifier.validate(
                info = InstalledApkInfo("com.example.other", 999L),
                expectedPackage = packageName,
                currentVersionCode = 38L
            )
        )
    }

    @Test
    fun `an equal or older versionCode is rejected`() {
        for (versionCode in listOf(38L, 37L, 1L)) {
            assertEquals(
                UpdateFailure.OLD_VERSION,
                ApkVerifier.validate(
                    info = InstalledApkInfo(packageName, versionCode),
                    expectedPackage = packageName,
                    currentVersionCode = 38L
                )
            )
        }
    }

    @Test
    fun `a newer build of this app is accepted`() {
        assertNull(
            ApkVerifier.validate(
                info = InstalledApkInfo(packageName, 39L),
                expectedPackage = packageName,
                currentVersionCode = 38L
            )
        )
    }
}
