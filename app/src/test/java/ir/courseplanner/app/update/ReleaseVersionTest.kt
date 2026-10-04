package ir.courseplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit (no Robolectric): the version parser and comparator are plain Kotlin.
 *
 * These cases pin the "never nag, never miss an update" contract — a wrong
 * comparison is user-visible in both directions.
 */
class ReleaseVersionTest {

    @Test
    fun `parses a tagged version`() {
        assertEquals("2.7.3", ReleaseVersion.parse("v2.7.3")?.versionName)
        assertEquals("2.7.3", ReleaseVersion.parse("2.7.3")?.versionName)
        assertEquals("2.7.3", ReleaseVersion.parse("  v2.7.3  ")?.versionName)
    }

    @Test
    fun `parses short and long version forms`() {
        assertEquals("2.7", ReleaseVersion.parse("v2.7")?.versionName)
        assertEquals("2", ReleaseVersion.parse("2")?.versionName)
        assertEquals("2.7.3.1", ReleaseVersion.parse("v2.7.3.1")?.versionName)
    }

    @Test
    fun `rejects malformed versions instead of guessing`() {
        // A tag that is not a version (a mirror rename, an HTML error page, a
        // release named "nightly") must never be read as an update.
        assertNull(ReleaseVersion.parse(null))
        assertNull(ReleaseVersion.parse(""))
        assertNull(ReleaseVersion.parse("   "))
        assertNull(ReleaseVersion.parse("latest"))
        assertNull(ReleaseVersion.parse("v2.7.3-rc1"))
        assertNull(ReleaseVersion.parse("2.7.3.4.5"))
        assertNull(ReleaseVersion.parse("2..3"))
        assertNull(ReleaseVersion.parse("v2.7.3<script>"))
    }

    @Test
    fun `orders by component not lexicographically`() {
        val older = ReleaseVersion.parse("v2.7.9")!!
        val newer = ReleaseVersion.parse("v2.7.10")!!
        // "2.7.10" < "2.7.9" as strings; numerically it is the other way round.
        assertTrue(newer.isNewerThan(older))
        assertFalse(older.isNewerThan(newer))
    }

    @Test
    fun `treats missing components as zero so 2 7 equals 2 7 0`() {
        val short = ReleaseVersion.parse("v2.7")!!
        val long = ReleaseVersion.parse("v2.7.0")!!
        assertEquals(0, short.compareTo(long))
        assertFalse(short.isNewerThan(long))
        assertFalse(long.isNewerThan(short))
    }

    @Test
    fun `detects newer equal and older releases`() {
        val current = ReleaseVersion.parse("2.7.3")!!
        assertTrue(ReleaseVersion.parse("2.7.4")!!.isNewerThan(current))
        assertTrue(ReleaseVersion.parse("2.8.0")!!.isNewerThan(current))
        assertTrue(ReleaseVersion.parse("3.0.0")!!.isNewerThan(current))
        assertFalse(ReleaseVersion.parse("2.7.3")!!.isNewerThan(current))
        assertFalse(ReleaseVersion.parse("2.7.2")!!.isNewerThan(current))
        assertFalse(ReleaseVersion.parse("2.6.9")!!.isNewerThan(current))
    }
}