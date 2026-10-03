package ir.courseplanner.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [MhtHtmlExtractor] (MHTML single-file archives) and its wiring
 * into [PooyaHtmlParser.parsePortalHtml].
 * Plain JUnit (no Robolectric): both classes are pure Kotlin, zero Android
 * dependencies. Fixtures are ASCII-only so this file survives any editor
 * encoding round-trip.
 */
class MhtHtmlExtractorTest {

    @Test
    fun `plain html is not mhtml`() {
        val html = "<html><body><table><tr><td>x</td></tr></table></body></html>"
        val out = MhtHtmlExtractor.extract(html)
        assertTrue(out is MhtHtmlExtractor.Extraction.Missing)
        assertEquals(
            MhtHtmlExtractor.Reason.NOT_MHTML,
            (out as MhtHtmlExtractor.Extraction.Missing).reason
        )
        assertFalse(MhtHtmlExtractor.looksLikeMhtml(html, "page.html"))
    }

    @Test
    fun `picks courses frame out of the frameset and menu`() {
        // Outer save kept the frameset page + a menu frame + the courses
        // form; only the last one holds the courses table.
        val mht = """
            From: <Saved by Blink>
            MIME-Version: 1.0
            Content-Type: multipart/related;
            	type="text/html";
            	boundary="----B1----"


            ------B1----
            Content-Type: text/html; charset=UTF-8
            Content-Location: https://pooya.example.ir/gateway/PuyaMainFrame2.php

            <html><head><title>x</title></head><frameset rows="1,2">
            <frame src="menu.php"><frame src="courses.php"></frameset></html>
            ------B1----
            Content-Type: text/html; charset=UTF-8
            Content-Location: https://pooya.example.ir/gateway/PuyaRight.php

            <html><body><p>menu</p></body></html>
            ------B1----
            Content-Type: text/html; charset=UTF-8
            Content-Location: https://pooya.example.ir/educ/stu_portal/PresentedCoursesForm.php

            <html><body><table border="1"><tbody>
            <tr><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ظرفیت</th><th>دانشکده</th><th>نام استاد</th></tr>
            <tr><td>1</td><td>10103</td><td>1</td><td>ریاضی عمومی</td><td>3.00</td><td>35</td><td>دانشکده اصلي</td><td>احمدی</td></tr>
            </tbody></table></body></html>
            ------B1------
        """.trimIndent()
        val out = MhtHtmlExtractor.extract(mht)
        assertTrue(out is MhtHtmlExtractor.Extraction.Html)
        val html = (out as MhtHtmlExtractor.Extraction.Html).html
        assertTrue(html.contains("ریاضی عمومی"))
        assertFalse(html.contains("frameset"))
        assertTrue(MhtHtmlExtractor.looksLikeMhtml(mht, "page.mht"))
    }

    @Test
    fun `quoted-printable and base64 html parts decode`() {
        val mht = """
            From: <Saved by Blink>
            MIME-Version: 1.0
            Content-Type: multipart/related;
            	type="text/html";
            	boundary="----B2----"


            ------B2----
            Content-Type: text/html; charset=UTF-8
            Content-Transfer-Encoding: quoted-printable
            Content-Location: https://pooya.example.ir/educ/stu_portal/PresentedCoursesForm.php

            <html><body><table border=3D"1"><tbody>
            <tr><th>=D8=B1=D8=AF=DB=8C=D9=81</th><th>=D8=B4=D9=85=D8=A7=D8=B1=D9=87 =D8=AF=D8=B1=D8=B3</th><th>=DA=AF=D8=B1=D9=88=D9=87</th><th>=D9=86=D8=A7=D9=85 =D8=AF=D8=B1=D8=B3</th><th>=D9=88=D8=A7=D8=AD=D8=AF</th><th>=D8=B8=D8=B1=D9=81=DB=8C=D8=AA</th></tr>
            </tbody></table></body></html>
            ------B2----
            Content-Type: text/html; charset=UTF-8
            Content-Transfer-Encoding: base64
            Content-Location: https://pooya.example.ir/gateway/menu.php

            PGh0bWw+PGJvZHk+bWVudTwvYm9keT48L2h0bWw+
            ------B2------
        """.trimIndent()
        val out = MhtHtmlExtractor.extract(mht)
        assertTrue(out is MhtHtmlExtractor.Extraction.Html)
        val html = (out as MhtHtmlExtractor.Extraction.Html).html
        // QP part wins over the base64 menu part; =D8=B1… must decode to ردیف.
        assertTrue(html.contains("ردیف"))
        assertTrue(html.contains("شماره درس"))
    }

    @Test
    fun `menu-only archive reports no course content`() {
        val mht = """
            From: <Saved by Blink>
            MIME-Version: 1.0
            Content-Type: multipart/related;
            	type="text/html";
            	boundary="----B3----"


            ------B3----
            Content-Type: text/html; charset=UTF-8
            Content-Location: https://pooya.example.ir/gateway/PuyaMainFrame2.php

            <html><head><title>x</title></head><frameset rows="1,2"></frameset></html>
            ------B3------
        """.trimIndent()
        val out = MhtHtmlExtractor.extract(mht)
        assertTrue(out is MhtHtmlExtractor.Extraction.Missing)
        assertEquals(
            MhtHtmlExtractor.Reason.NO_COURSE_CONTENT,
            (out as MhtHtmlExtractor.Extraction.Missing).reason
        )
    }

    @Test
    fun `archive without html reports no html part`() {
        val mht = """
            From: <Saved by Blink>
            MIME-Version: 1.0
            Content-Type: multipart/related;
            	type="text/html";
            	boundary="----B4----"


            ------B4----
            Content-Type: image/gif
            Content-Transfer-Encoding: base64
            Content-Location: https://pooya.example.ir/x.gif

            R0lGODlhAQABAIAAAP8=
            ------B4------
        """.trimIndent()
        val out = MhtHtmlExtractor.extract(mht)
        assertTrue(out is MhtHtmlExtractor.Extraction.Missing)
        assertEquals(
            MhtHtmlExtractor.Reason.NO_HTML_PART,
            (out as MhtHtmlExtractor.Extraction.Missing).reason
        )
    }

    @Test
    fun `garbage input never throws`() {
        val bad = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"----X----\"\r\n\r\nno parts here ------X------"
        val out = MhtHtmlExtractor.extract(bad)
        assertTrue(out is MhtHtmlExtractor.Extraction.Missing)
    }

    @Test
    fun `mht unwraps through the portal parser`() {
        val mht = """
            From: <Saved by Blink>
            MIME-Version: 1.0
            Content-Type: multipart/related;
            	type="text/html";
            	boundary="----B5----"


            ------B5----
            Content-Type: text/html; charset=UTF-8
            Content-Location: https://pooya.example.ir/educ/stu_portal/PresentedCoursesForm.php

            <html><body><table border="1"><tbody>
            <tr><th>نام درس</th><th>کد درس</th><th>واحد</th><th>گروه</th><th>ظرفیت</th><th>ثبت نام شده</th><th>دانشکده</th><th>نام استاد</th><th>جزئیات</th></tr>
            <tr><td>ریاضی عمومی</td><td>10103</td><td>3.00</td><td>1</td><td>35</td><td>27</td><td>دانشکده اصلي</td><td>احمدی</td><td><img src="info.gif" title=""></td></tr>
            </tbody></table></body></html>
            ------B5------
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(mht)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertEquals("10103", items[0].course.code)
        assertEquals("ریاضی عمومی", items[0].course.name)
    }

    @Test
    fun `mht without courses fails with archive message`() {
        // Outer boundary is present but the archive holds no html part.
        val mht = "From: <Saved by Blink>\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: multipart/related;\r\n" +
            "\ttype=\"text/html\";\r\n" +
            "\tboundary=\"----B6----\"\r\n" +
            "\r\n\r\n" +
            "------B6----\r\n" +
            "Content-Type: image/gif\r\n" +
            "Content-Location: https://pooya.example.ir/x.gif\r\n" +
            "\r\n" +
            "R0lGODlhAQABAIAAAP8=\r\n" +
            "------B6------"
        val result = PooyaHtmlParser.parsePortalHtml(mht)
        assertTrue(result is ImportResult.Failure)
        val message = (result as ImportResult.Failure).errorMessage
        assertTrue(message.contains(".mht"))
    }

    /**
     * Real Chrome single-file saves (.mht) — raw binary image parts included,
     * the exact shape that broke on device (whole-file UTF-8 decode fails and
     * a windows-1256 fallback then corrupts the Persian table, so the app
     * reported "no courses"). Runs only when TERMCHIN_MHT_FIXTURE points at
     * file(s) separated by ';'; otherwise returns silently so CI stays
     * hermetic and no binary fixture is committed. Look for "MHT_FIXTURE_USED"
     * in the JUnit XML to prove the assertions really ran.
     */
    @Test
    fun `real chrome mht fixture unwraps and parses`() {
        val rawPaths = System.getenv("TERMCHIN_MHT_FIXTURE") ?: return
        val files = rawPaths.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { java.io.File(it) }
        assertTrue("TERMCHIN_MHT_FIXTURE points at no existing file", files.isNotEmpty())
        var lastResult: ImportResult? = null
        for (file in files) {
            val out = MhtHtmlExtractor.extractFromBytes(file.readBytes())
            assertTrue("expected courses HTML from ${file.name}", out is MhtHtmlExtractor.Extraction.Html)
            val html = (out as MhtHtmlExtractor.Extraction.Html).html
            assertTrue(html.contains("شماره درس"))
            assertTrue(html.contains("نام درس"))
            val result = PooyaHtmlParser.parsePortalHtml(html)
            assertTrue("expected parse success from ${file.name}", result is ImportResult.Success)
            val items = (result as ImportResult.Success).items
            assertTrue("expected >=100 courses from ${file.name}, got ${items.size}", items.size >= 100)
            println("MHT_FIXTURE_USED ${file.name} courses=${items.size}")
            lastResult = result
        }
        val items = (lastResult as ImportResult.Success).items
        val lang = items.first { it.course.code == "10014" }
        assertEquals("زبان خارجی", lang.course.name)
        val sessions = lang.sections.first { it.section.sectionCode == "1" }.sessions
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.dayOfWeek == 2 && it.startTime == "10:00" && it.endTime == "12:00" })
        assertTrue(sessions.any { it.dayOfWeek == 2 && it.startTime == "12:00" && it.endTime == "14:00" })
    }

    /**
     * Regression: an archive whose image part is RAW binary (JPEG magic with
     * bytes that are not valid UTF-8). Decoding the whole file as text fails
     * there and a charset fallback would corrupt the Persian table — the
     * byte-level pipeline must still yield a parseable courses frame.
     */
    @Test
    fun `raw binary image part does not corrupt the courses table`() {
        val jpegLike = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x80.toByte(), 0x90.toByte(), 0xFE.toByte(),
        )
        val head =
            "From: <Saved by Blink>\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/related;\r\n" +
                "\ttype=\"text/html\";\r\n" +
                "\tboundary=\"----BB2----\"\r\n" +
                "\r\n\r\n"
        val imagePart =
            "------BB2----\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Transfer-Encoding: binary\r\n" +
                "Content-Location: https://pooya.example.ir/serial/photo.jpg\r\n" +
                "\r\n"
        val htmlPart =
            "------BB2----\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: binary\r\n" +
                "Content-Location: https://pooya.example.ir/educ/stu_portal/PresentedCoursesForm.php\r\n" +
                "\r\n" +
                "<html><body><table border=\"1\"><tbody>\n" +
                "<tr><th>نام درس</th><th>کد درس</th><th>واحد</th><th>گروه</th><th>ظرفیت</th><th>ثبت نام شده</th><th>دانشکده</th><th>نام استاد</th><th>جزئیات</th></tr>\n" +
                "<tr><td>آمار و احتمالات مهندسی</td><td>10234</td><td>3.00</td><td>1</td><td>30</td><td>11</td><td>دانشکده اصلي</td><td>محمدی</td><td><img src=\"info.gif\" title=\"\"></td></tr>\n" +
                "</tbody></table></body></html>\r\n" +
                "------BB2------"
        val bytes = head.toByteArray(Charsets.ISO_8859_1) +
            imagePart.toByteArray(Charsets.ISO_8859_1) + jpegLike +
            "\r\n".toByteArray(Charsets.ISO_8859_1) +
            htmlPart.toByteArray(Charsets.UTF_8)

        // Sanity: this fixture must NOT be decodable as whole-file UTF-8 —
        // otherwise the regression is no longer reproduced by this test.
        val wholeFileUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        assertNull("sanity: raw image bytes must break whole-file UTF-8", wholeFileUtf8)

        val out = MhtHtmlExtractor.extractFromBytes(bytes)
        assertTrue(out is MhtHtmlExtractor.Extraction.Html)
        val result = PooyaHtmlParser.parsePortalHtml((out as MhtHtmlExtractor.Extraction.Html).html)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertEquals("10234", items[0].course.code)
        assertEquals("آمار و احتمالات مهندسی", items[0].course.name)
    }
}
