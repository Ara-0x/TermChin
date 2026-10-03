package ir.courseplanner.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * Real Chrome single-file save (.mht) of the Pooya presented-courses
     * page. Runs only when TERMCHIN_MHT_FIXTURE points at the file (local
     * verification); otherwise returns silently so CI stays hermetic and no
     * binary fixture is committed.
     */
    @Test
    fun `real chrome mht fixture unwraps and parses`() {
        val path = System.getenv("TERMCHIN_MHT_FIXTURE") ?: return
        val file = java.io.File(path)
        if (!file.isFile) return
        val out = MhtHtmlExtractor.extractFromBytes(file.readBytes())
        assertTrue(out is MhtHtmlExtractor.Extraction.Html)
        val html = (out as MhtHtmlExtractor.Extraction.Html).html
        assertTrue(html.contains("شماره درس"))
        assertTrue(html.contains("نام درس"))
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertTrue(items.size >= 100)
        val lang = items.first { it.course.code == "10014" }
        assertEquals("زبان خارجی", lang.course.name)
        val sessions = lang.sections.first { it.section.sectionCode == "1" }.sessions
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.dayOfWeek == 2 && it.startTime == "10:00" && it.endTime == "12:00" })
        assertTrue(sessions.any { it.dayOfWeek == 2 && it.startTime == "12:00" && it.endTime == "14:00" })
    }
}
