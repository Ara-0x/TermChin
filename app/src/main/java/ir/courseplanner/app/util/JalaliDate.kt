package ir.courseplanner.app.util

import java.util.Calendar

/** A validated Jalali (Shamsi) calendar date. See [JalaliDate] for conversion. */
data class JalaliYmd(val year: Int, val month: Int, val day: Int) {
    override fun toString(): String = JalaliDate.format(year, month, day)
}

/**
 * Jalali (Shamsi) calendar math — pure Kotlin, no java.time (minSdk 24 has no
 * desugaring) and no extra dependencies.
 *
 * Conversion follows the well-known Birashk/jalaali-js algorithm:
 * Jalali <-> Julian Day Number, with epoch days derived as (JDN - 2440588).
 * Academic even/odd weeks are counted from the semester start, so every
 * function here works on plain epoch days and stays unit-testable.
 */
object JalaliDate {

    /** JDN of 1970-01-01, the epoch-day origin. */
    private const val JDN_EPOCH = 2440588L

    private val BREAKS = longArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    // NOTE: jalaali-style helpers use TRUNCATION toward zero (JS `~~`),
    // unlike Howard Hinnant's floor-based helpers below. Do not mix them.
    private fun div(a: Long, b: Long): Long = a / b
    private fun mod(a: Long, b: Long): Long = a % b

    private data class JalCal(val leap: Long, val gy: Long, val march: Long)

    private fun jalCal(jy: Long): JalCal? {
        val bl = BREAKS.size
        if (jy < BREAKS[0] || jy >= BREAKS[bl - 1]) return null
        val gy = jy + 621
        var leapJ = -14L
        var jp = BREAKS[0]
        var jump = 0L
        var i = 1
        while (i < bl) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4L && jump - n == 4L) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1L) leap = 4
        return JalCal(leap, gy, march)
    }

    /**
     * Gregorian date -> JDN, delegated to [daysFromCivil] (hand-verified:
     * JDN(2000-01-01) comes out as exactly 2451545).
     */
    private fun g2d(gy: Long, gm: Long, gd: Long): Long {
        return daysFromCivil(gy.toInt(), gm.toInt(), gd.toInt()) + JDN_EPOCH
    }

    private fun isLeapYearInternal(jy: Long): Boolean {
        return jalCal(jy)?.leap == 0L
    }

    fun isLeapYear(jy: Int): Boolean = isLeapYearInternal(jy.toLong())

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        jm == 12 -> if (isLeapYear(jy)) 30 else 29
        else -> 0
    }

    fun isValid(jy: Int, jm: Int, jd: Int): Boolean {
        if (jm !in 1..12) return false
        val len = monthLength(jy, jm)
        return len > 0 && jd in 1..len
    }

    /** Jalali -> epoch day (days since 1970-01-01). Null when invalid/out of range. */
    fun toEpochDay(jy: Int, jm: Int, jd: Int): Long? {
        if (!isValid(jy, jm, jd)) return null
        val r = jalCal(jy.toLong()) ?: return null
        val jdn = g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm.toLong(), 7) * (jm - 7) + jd - 1
        return jdn - JDN_EPOCH
    }

    /**
     * Epoch day -> Jalali, found by anchoring on 1 Farvardin (avoids the
     * error-prone direct JDN->Jalali branch math).
     */
    fun fromEpochDay(epochDay: Long): JalaliYmd? {
        // Rough estimate, then walk at most a couple of years.
        var jy = ((epochDay / 366) + 1349).toInt().coerceIn(1250, 1600)
        repeat(6) {
            val start = toEpochDay(jy, 1, 1) ?: return null
            val len = if (isLeapYear(jy)) 366 else 365
            when {
                epochDay < start -> jy--
                epochDay >= start + len -> jy++
                else -> {
                    var rest = (epochDay - start).toInt()
                    var jm = 1
                    while (jm <= 12) {
                        val ml = monthLength(jy, jm)
                        if (rest < ml) return JalaliYmd(jy, jm, rest + 1)
                        rest -= ml
                        jm++
                    }
                    return null
                }
            }
        }
        return null
    }

    /** Accepts "1404/07/05", "1404-7-5", Persian digits, extra spaces. Null if invalid. */
    fun parse(input: String): JalaliYmd? {
        val normalized = normalizeDigits(input).trim()
        if (normalized.isEmpty()) return null
        val parts = normalized.split(Regex("[/\\-.\\s]+")).filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y !in 1250..1600) return null
        if (!isValid(y, m, d)) return null
        return JalaliYmd(y, m, d)
    }

    fun format(jy: Int, jm: Int, jd: Int): String {
        return "%04d/%02d/%02d".format(jy, jm, jd)
    }

    /** Persian month name (1 = فروردین … 12 = اسفند). Empty when out of range. */
    fun monthNameFa(jm: Int): String = when (jm) {
        1 -> "فروردین"
        2 -> "اردیبهشت"
        3 -> "خرداد"
        4 -> "تیر"
        5 -> "مرداد"
        6 -> "شهریور"
        7 -> "مهر"
        8 -> "آبان"
        9 -> "آذر"
        10 -> "دی"
        11 -> "بهمن"
        12 -> "اسفند"
        else -> ""
    }

    private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Latin 0-9 → Persian digits; every other character passes through. */
    fun toFaDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch in '0'..'9') append(FA_DIGITS[ch - '0'])
            else append(ch)
        }
    }

    /** Latin 0-9 → Persian digits for integers (counts, minutes, week numbers). */
    fun toFaDigits(value: Int): String = toFaDigits(value.toString())

    /** Today as epoch day via Calendar (java.time needs API 26+). */
    fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        return daysFromCivil(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** App weekday index (Saturday = 0 … Friday = 6) for an epoch day. */
    fun appDayIndexOfEpochDay(epochDay: Long): Int {
        return (Math.floorMod(epochDay + 5, 7)).toInt()
    }

    /** Howard Hinnant's days_from_civil — Gregorian date to epoch day. */
    internal fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = Math.floorDiv(if (yy >= 0) yy else yy - 399, 400)
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
