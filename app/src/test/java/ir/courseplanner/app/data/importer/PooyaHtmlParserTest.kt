package ir.courseplanner.app.data.importer

import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [PooyaHtmlParser] against the real Pooya "Presented Courses" layout.
 * Plain JUnit (no Robolectric): the parser is pure Kotlin with zero Android deps.
 */
class PooyaHtmlParserTest {

    @Test
    fun `blank input fails with message`() {
        val result = PooyaHtmlParser.parsePortalHtml("   ")
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `html without courses table fails`() {
        val result = PooyaHtmlParser.parsePortalHtml("<html><body><p>سلام</p></body></html>")
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `parses pooya presented-courses rows into catalog items`() {
        val result = PooyaHtmlParser.parsePortalHtml(SAMPLE_HTML)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items

        // 3 distinct course codes in the sample.
        assertEquals(3, items.size)

        val web = items.first { it.course.code == "10309" }
        assertEquals("برنامه نویسی مبتنی بر وب", web.course.name)
        assertEquals(2, web.course.credits)
        assertEquals(2, web.sections.size)
        // Catalog-only: must NOT leak into "my courses" or the generator.
        assertFalse(web.course.isSelectedForGeneration)

        // Group 1 has an empty tooltip -> no sessions, but keeps instructor/capacity.
        val g1 = web.sections.first { it.section.sectionCode == "1" }
        assertEquals("فتاحی محبوبه", g1.section.instructor)
        assertEquals(15, g1.section.capacity)
        assertTrue(g1.sessions.isEmpty())

        // Group 2 tooltip carries two class sessions with parity/location.
        val g2 = web.sections.first { it.section.sectionCode == "2" }
        assertEquals(2, g2.sessions.size)

        val first = g2.sessions[0]
        assertEquals(2, first.dayOfWeek) // دوشنبه
        assertEquals("08:00", first.startTime)
        assertEquals("10:00", first.endTime)
        assertEquals("کارگاه کامپیوتر 2", first.location)
        assertEquals(WeekType.EVERY_WEEK, first.weekType)

        val second = g2.sessions[1]
        assertEquals(2, second.dayOfWeek)
        assertEquals("10:00", second.startTime)
        assertEquals("12:00", second.endTime)
        assertEquals(WeekType.ODD_WEEKS, second.weekType)

        // Dummy exam rows (hour 6, class 0, no date) are ignored.
        assertTrue(g2.section.examDate.isEmpty())
    }

    @Test
    fun `project rows with placeholder sessions are kept without class time`() {
        val result = PooyaHtmlParser.parsePortalHtml(SAMPLE_HTML)
        val items = (result as ImportResult.Success).items
        val project = items.first { it.course.code == "10318" }
        assertEquals("پروژه", project.course.name)
        val g31 = project.sections.first { it.section.sectionCode == "31" }
        assertEquals("شهریاری شیرزاد", g31.section.instructor)
        assertEquals(2, g31.section.capacity)
        // Duration-0 / day-less placeholder sessions must not pollute the timetable.
        assertTrue(g31.sessions.isEmpty())
    }

    @Test
    fun `persian digits are normalized`() {
        val result = PooyaHtmlParser.parsePortalHtml(SAMPLE_HTML)
        val items = (result as ImportResult.Success).items
        val adv = items.first { it.course.code == "10559" }
        assertEquals("برنامه سازی پیشرفته", adv.course.name)
        assertEquals(2, adv.course.credits)
        val g1 = adv.sections.first()
        assertEquals(10, g1.section.capacity)
        assertEquals(1, g1.sessions.size)
        assertEquals(2, g1.sessions[0].dayOfWeek)
        assertEquals("14:00", g1.sessions[0].startTime)
        assertEquals("16:00", g1.sessions[0].endTime)
    }

    @Test
    fun `reordered columns are resolved from the header row`() {
        val html = """
<html><body><table border="1"><tbody>
<tr><th>نام درس</th><th>کد درس</th><th>واحد</th><th>گروه</th><th>ظرفیت</th><th>ثبت نام شده</th><th>دانشکده</th><th>نام استاد</th><th>جزئیات</th></tr>
<tr><td>ریاضی عمومی</td><td>10103</td><td>3.00</td><td>1</td><td>35</td><td>27</td><td>دانشکده اصلي</td><td>ملااحمدیان</td><td><img src="info.gif" title=""></td></tr>
</tbody></table></body></html>
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertEquals("10103", items[0].course.code)
        assertEquals("ریاضی عمومی", items[0].course.name)
        assertEquals(3, items[0].course.credits)
        assertEquals("1", items[0].sections[0].section.sectionCode)
        assertEquals(35, items[0].sections[0].section.capacity)
    }

    @Test
    fun `missing optional column does not break parsing`() {
        val html = """
<html><body><table border="1"><tbody>
<tr><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ثبت نام شده</th><th>دانشکده</th><th>نام استاد</th><th>&nbsp;</th></tr>
<tr><td>1</td><td>10103</td><td>1</td><td>ریاضی عمومی</td><td>3.00</td><td>27</td><td>دانشکده اصلي</td><td>ملااحمدیان</td><td><img src="info.gif" title=""></td></tr>
</tbody></table></body></html>
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertEquals(0, items[0].sections[0].section.capacity)
    }

    @Test
    fun `invalid rows are skipped with warnings instead of silent defaults`() {
        val html = """
<html><body><table border="1"><tbody>
<tr><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ثبت نام شده</th><th>ظرفیت</th><th>دانشکده</th><th>نام استاد</th><th>&nbsp;</th></tr>
<tr><td>1</td><td></td><td>1</td><td>بی‌کد</td><td>3.00</td><td>0</td><td>10</td><td>دانشکده اصلي</td><td>استاد</td><td></td></tr>
<tr><td>2</td><td>99999</td><td>1</td><td>واحد خراب</td><td>abc</td><td>0</td><td>10</td><td>دانشکده اصلي</td><td>استاد</td><td></td></tr>
<tr><td>3</td><td>10103</td><td>1</td><td>ریاضی عمومی</td><td>3.00</td><td>27</td><td>35</td><td>دانشکده اصلي</td><td>ملااحمدیان</td><td></td></tr>
</tbody></table></body></html>
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1, success.items.size)
        assertEquals("10103", success.items[0].course.code)
        assertEquals(2, success.warnings.size)
        assertTrue(success.message.contains("نیاز به بررسی"))
    }

    @Test
    fun `out of range session time is skipped not stored`() {
        val html = """
<html><body><table border="1"><tbody>
<tr><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ثبت نام شده</th><th>ظرفیت</th><th>دانشکده</th><th>نام استاد</th><th>&nbsp;</th></tr>
<tr><td>1</td><td>10103</td><td>1</td><td>ریاضی عمومی</td><td>3.00</td><td>0</td><td>10</td><td>دانشکده اصلي</td><td>استاد</td>
<td><img src="info.gif" title="header=[x] body=[مقطع: کارشناسی گروه آموزشی: كامپيوتر جلسه اول روز: دوشنبه ساعت 25(هر هفته به مدت 120 دقیقه در کلاس 1) شروع زوج]"></td></tr>
</tbody></table></body></html>
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertTrue(items[0].sections[0].sessions.isEmpty())
    }

    @Test
    fun `ninety minute session keeps its real end time`() {
        val sessions = PooyaHtmlParser.parseSessions(
            "جلسه اول روز: شنبه ساعت 8(هر هفته به مدت 90 دقیقه در کلاس 1)"
        )

        assertEquals(1, sessions.size)
        assertEquals("08:00", sessions[0].startTime)
        assertEquals("09:30", sessions[0].endTime)
        assertEquals(90, sessions[0].endMinutes - sessions[0].startMinutes)
    }

    @Test
    fun `exam duration produces a real end time`() {
        val html = """
<html><body><table border="1"><tbody>
<tr><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ثبت نام شده</th><th>ظرفیت</th><th>دانشکده</th><th>نام استاد</th><th>&nbsp;</th></tr>
<tr><td>1</td><td>10103</td><td>1</td><td>ریاضی عمومی</td><td>3.00</td><td>0</td><td>10</td><td>دانشکده اصلي</td><td>استاد</td>
<td><img src="info.gif" title="header=[x] body=[مقطع: کارشناسی گروه آموزشی: كامپيوتر امتحان روز: شنبه ساعت 9 به مدت 120 دقیقه در کلاس 10 به تاریخ 1403/10/20]"></td></tr>
</tbody></table></body></html>
        """.trimIndent()
        val result = PooyaHtmlParser.parsePortalHtml(html)
        assertTrue(result is ImportResult.Success)
        val sec = (result as ImportResult.Success).items[0].sections[0].section
        assertEquals("1403/10/20", sec.examDate)
        assertEquals("09:00", sec.examStartTime)
        assertEquals("11:00", sec.examEndTime)
    }

    @Test
    fun `malformed html never throws`() {
        val result = PooyaHtmlParser.parsePortalHtml("<table><tr><td>ناقص")
        assertTrue(result is ImportResult.Failure || result is ImportResult.Success)
    }

    @Test
    fun `reimport message reports course and group counts`() {
        val result = PooyaHtmlParser.parsePortalHtml(SAMPLE_HTML)
        val message = (result as ImportResult.Success).message
        assertTrue(message.contains("3"))
        assertTrue(message.contains("4"))
    }

    companion object {
        // Representative fragment of a real Pooya PresentedCoursesForm page:
        // a decoy filter table, the bordered course table, empty/full tooltips,
        // a project placeholder row and Persian digits.
        private const val SAMPLE_HTML = """
<html dir="rtl"><head><title>Presented Courses Form</title></head><body>
<form><table border="0"><tbody>
<tr><td><b>دانشکده ارائه دهنده</b></td><td>دانشکده اصلي</td></tr>
</tbody></table></form>
<table border="1" cellpadding="1" cellspacing="0"><tbody>
<tr bgcolor="#FFFF40"><th>ردیف</th><th>شماره درس</th><th>گروه</th><th>نام درس</th><th>واحد</th><th>ثبت نام شده</th><th>ظرفیت</th><th>دانشکده</th><th>نام استاد</th><th>کد درس اصلی</th><th>نام درس اصلی</th><th>ثبت نام شده کل</th><th>رزرو</th><th>&nbsp;</th></tr>
<tr bgcolor="#FFDCB9"><td align="center"><b>6</b></td>
<td align="center"><a href="https://pooya.khorasan.ac.ir/educ/stu_portal/PresentedCoursesForm.php?LesCode=10309" target="_blank">10309</a></td>
<td align="center">1</td>
<td align="center">برنامه نویسی مبتنی بر وب</td>
<td align="center">2.00</td>
<td align="center">8</td>
<td align="center">15</td>
<td align="center">دانشکده اصلي</td>
<td align="center">فتاحی محبوبه&nbsp;&nbsp;<img src="file_apply.gif" title="طرح درس"></td>
<td align="center"> </td><td align="center"> </td><td align="center">12</td><td align="center">0</td>
<td align="center"><img src="info.gif" title=""></td>
</tr>
<tr bgcolor="#FFDCB9"><td align="center"><b>7</b></td>
<td align="center"><a href="https://pooya.khorasan.ac.ir/educ/stu_portal/PresentedCoursesForm.php?LesCode=10309" target="_blank">10309</a></td>
<td align="center">2</td>
<td align="center" bgcolor="#9eeb41">برنامه نویسی مبتنی بر وب</td>
<td align="center">2.00</td>
<td align="center">4</td>
<td align="center">15</td>
<td align="center">دانشکده اصلي</td>
<td align="center">فتاحی محبوبه&nbsp;&nbsp;<img src="file_apply.gif" title="طرح درس"></td>
<td align="center">10309(1)</td>
<td align="center">برنامه نویسی مبتنی بر وب</td>
<td align="center">12</td><td align="center">0</td>
<td align="center"><img src="info.gif" title="cssheader=[coursedetailhead]  cssbody=[coursedetail] header=[برنامه نویسی مبتنی بر وب گروه 2] body=[&lt;b&gt;شرح درس: &lt;/b&gt;برنامه نویسی مبتنی بر وب-&lt;br&gt;&lt;b&gt;مقطع:&lt;/b&gt; کاردانی پیوسته&lt;br&gt;&lt;b&gt;گروه آموزشی:&lt;/b&gt; كامپيوتر&lt;br&gt;&lt;b&gt;جلسه اول روز:&lt;/b&gt; دوشنبه ساعت 8(هر هفته به مدت 120 دقیقه در  کارگاه کامپیوتر 2) شروع زوج&lt;br&gt;&lt;b&gt;جلسه دوم روز:&lt;/b&gt; دوشنبه ساعت 10(هفته در میان به مدت 120 دقیقه در  کارگاه کامپیوتر 2) شروع فرد&lt;br&gt;&lt;b&gt;امتحان روز:&lt;/b&gt;  ساعت 6 به مدت 0 دقیقه در کلاس 0 به تاریخ&lt;br&gt;&lt;b&gt;قابل انتخاب برای دانشجویان:&lt;/b&gt; مرد و زن دوره همه دوره ها  (ورودی 1404 و ماقبل)]  "></td>
</tr>
<tr bgcolor="#FF9F9F"><td align="center"><b>10</b></td>
<td align="center"><a href="https://pooya.khorasan.ac.ir/educ/stu_portal/PresentedCoursesForm.php?LesCode=10318" target="_blank">10318</a></td>
<td align="center">31</td>
<td align="center">پروژه</td>
<td align="center">2.00</td>
<td align="center">2</td>
<td align="center">2</td>
<td align="center">دانشکده اصلي</td>
<td align="center">شهریاری شیرزاد</td>
<td align="center"> </td><td align="center"> </td><td align="center">2</td><td align="center">0</td>
<td align="center"><img src="info.gif" title="cssheader=[coursedetailhead]  cssbody=[coursedetail] header=[پروژه گروه 31] body=[&lt;b&gt;شرح درس: &lt;/b&gt;پروژه-&lt;br&gt;&lt;b&gt;مقطع:&lt;/b&gt; کارشناسی&lt;br&gt;&lt;b&gt;گروه آموزشی:&lt;/b&gt; كامپيوتر&lt;br&gt;&lt;b&gt;جلسه اول روز:&lt;/b&gt;  ساعت 6(هر هفته به مدت 0 دقیقه در  0) شروع فرد&lt;br&gt;&lt;b&gt;امتحان روز:&lt;/b&gt;  ساعت 6 به مدت 0 دقیقه در کلاس 0 به تاریخ&lt;br&gt;&lt;b&gt;قابل انتخاب برای دانشجویان:&lt;/b&gt; مرد و زن دوره همه دوره ها  (ورودی 1402 و ماقبل)]  "></td>
</tr>
<tr bgcolor="#FFDCB9"><td align="center"><b>21</b></td>
<td align="center"><a href="https://pooya.khorasan.ac.ir/educ/stu_portal/PresentedCoursesForm.php?LesCode=10559" target="_blank">۱۰۵۵۹</a></td>
<td align="center">۱</td>
<td align="center">برنامه سازی پیشرفته</td>
<td align="center">۲.۰۰</td>
<td align="center">۰</td>
<td align="center">۱۰</td>
<td align="center">دانشکده اصلي</td>
<td align="center">سرایی مریم</td>
<td align="center"> </td><td align="center"> </td><td align="center">۲۱</td><td align="center">۰</td>
<td align="center"><img src="info.gif" title="cssheader=[coursedetailhead]  cssbody=[coursedetail] header=[برنامه سازی پیشرفته گروه 1] body=[&lt;b&gt;شرح درس: &lt;/b&gt;برنامه سازی پیشرفته-&lt;br&gt;&lt;b&gt;مقطع:&lt;/b&gt; کاردانی پیوسته&lt;br&gt;&lt;b&gt;گروه آموزشی:&lt;/b&gt; كامپيوتر&lt;br&gt;&lt;b&gt;جلسه اول روز:&lt;/b&gt; دوشنبه ساعت 14(هر هفته به مدت 120 دقیقه در  کارگاه کامپیوتر 1) شروع زوج&lt;br&gt;&lt;b&gt;امتحان روز:&lt;/b&gt;  ساعت 6 به مدت 0 دقیقه در کلاس 0 به تاریخ&lt;br&gt;&lt;b&gt;قابل انتخاب برای دانشجویان:&lt;/b&gt; مرد و زن دوره همه دوره ها  (ورودی 1405 و ماقبل)]  "></td>
</tr>
</tbody></table>
</body></html>
        """
    }
}
