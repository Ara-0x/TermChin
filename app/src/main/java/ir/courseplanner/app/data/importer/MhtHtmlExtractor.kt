package ir.courseplanner.app.data.importer

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Extracts the main HTML document from an MHTML (.mht/.mhtml) archive.
 *
 * Chrome/Edge "Save as → Webpage, Single File" produces an RFC 2046
 * `multipart/related` document: one MIME part per resource (the courses
 * frame, the menu frame, CSS, images, …).
 *
 * The TermChin portal import ([PooyaHtmlParser]) needs only the frame that
 * holds the courses table. Feeding it raw .mht bytes parses MIME
 * headers/boundaries as courses, so this extractor runs first and hands the
 * inner HTML to the parser unchanged.
 *
 * Design notes:
 * - Pure Kotlin/Java, zero new dependencies: boundary splitting, header
 *   parsing, quoted-printable and base64 decoding use string ops plus a
 *   manual base64 loop (java.util.Base64 needs API 26; minSdk is 24).
 * - The winner is picked by scoring, not position: location hints for the
 *   presented-courses form plus body keywords/table count. Frame pages that
 *   carry only a frameset and no courses lose on the content score.
 * - Binary parts decode as UTF-8 with a Windows-1256 fallback: Blink writes
 *   the portal's UTF-8 bytes through a `binary` part, and strict UTF-8
 *   decoding recovers clean Persian text. Real HTML files never reach this
 *   file: it is only consulted for MHTML input.
 * - Never throws for malformed archives: [Extraction] is either the decoded
 *   HTML or a short machine-checkable [Reason] the UI turns into a Persian
 *   message (see CoursePlannerViewModel).
 */
object MhtHtmlExtractor {

    /** Portal location fragments that identify the presented-courses frame. */
    private val coursesLocationHints = listOf(
        "presentedcourses",
        "presented-courses",
        "dorus_erae",
        "offeredcourses",
    )

    /** Body keywords that distinguish the courses frame from menu frames. */
    private val coursesBodyHints = listOf(
        "شماره درس",
        "نام درس",
        "ثبت نام شده",
        "ظرفیت",
        "نام استاد",
        "دانشکده",
    )

    /** Hard cap on one decoded MIME part (MHT files embed images). */
    private const val MAX_PART_CHARS = 8_000_000

    /** Result of scanning one MHTML archive. */
    sealed class Extraction {
        /** The inner HTML document, ready for [PooyaHtmlParser.parsePortalHtml]. */
        data class Html(val html: String) : Extraction()

        /** No usable document was found; [reason] is machine-checkable. */
        data class Missing(val reason: Reason) : Extraction()
    }

    enum class Reason {
        /** Input is not an MHTML archive at all. */
        NOT_MHTML,

        /** The archive is valid MHTML but holds no text/html part. */
        NO_HTML_PART,

        /** The html parts carry no portal course content (menu-only archive). */
        NO_COURSE_CONTENT,
    }

    /** Extracts the courses HTML from raw MHTML text (used by unit tests). */
    fun extract(raw: String): Extraction {
        val boundary = findBoundary(raw) ?: return Extraction.Missing(Reason.NOT_MHTML)
        val parts = splitParts(raw, boundary)
        if (parts.isEmpty()) return Extraction.Missing(Reason.NOT_MHTML)
        return pickCoursesHtml(parts)
    }

    /**
     * Entry point for real files: strict-decodes the bytes as UTF-8 and
     * falls back to Windows-1256 only when they are not valid UTF-8.
     * (MHTML headers are ASCII, so this never corrupts the boundary scan.)
     */
    fun extractFromBytes(bytes: ByteArray): Extraction {
        if (bytes.isEmpty()) return Extraction.Missing(Reason.NOT_MHTML)
        val asUtf8 = decodeSafely(bytes, Charsets.UTF_8) ?: decodeWith1256(bytes)
        return extract(asUtf8)
    }

    /**
     * Best-effort MHTML detector for a picked file. True when the content
     * opens with the Save-as MIME preamble and declares multipart/related,
     * or when the file name says .mht/.mhtml. Never throws.
     */
    fun looksLikeMhtml(contentStart: String, displayName: String? = null): Boolean {
        val head = contentStart.take(2048)
        if (head.contains("multipart/related", ignoreCase = true)) return true
        if (head.startsWith("From: <Saved by", ignoreCase = true)) return true
        if (head.startsWith("MIME-Version:", ignoreCase = true)) return true
        if (displayName != null) {
            val lower = displayName.lowercase(Locale.ROOT)
            if (lower.endsWith(".mht") || lower.endsWith(".mhtml")) return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // MIME plumbing (headers are ASCII; only the body carries Persian text).
    // ------------------------------------------------------------------

    private class Part(val headers: Map<String, String>, val rawBody: String)

    private fun findBoundary(raw: String): String? {
        val head = raw.take(8192)
        // Content-Type: multipart/related; type="text/html"; boundary="----…"
        val match = Regex(
            "(?i)boundary\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s;]+))"
        ).find(head) ?: return null
        val boundary = match.groupValues[1]
            .ifEmpty { match.groupValues[2] }
            .ifEmpty { match.groupValues[3] }
            .trim()
        if (boundary.isEmpty()) return null
        if (!head.contains("multipart/", ignoreCase = true)) return null
        return boundary
    }

    private fun splitParts(raw: String, boundary: String): List<Part> {
        // Boundaries sit alone on a line ("--boundary" / "--boundary--").
        // The value itself may contain regex chars (Chrome emits '-').
        val delimiter = Regex(
            "(?m)^[ \\t]*" + Regex.escape("--" + boundary) + "(?:--[ \\t]*)?[ \\t]*$"
        )
        val chunks = delimiter.split(raw)
        if (chunks.size < 2) return emptyList()
        val parts = mutableListOf<Part>()
        // Chunk[0] is the MIME preamble ("From: <Saved by Blink>" …); the
        // rest each hold one MIME part ("headers\r\n\r\nbody").
        for (chunk in chunks.drop(1)) {
            if (chunk.isBlank() || chunk.trim() == "--") continue
            parts += parsePart(chunk)
        }
        return parts
    }

    private fun parsePart(chunk: String): Part {
        val normalized = chunk.replace("\r\n", "\n")
        val splitAt = normalized.indexOf("\n\n")
        if (splitAt < 0) return Part(emptyMap(), normalized.trim())
        val headerBlock = normalized.substring(0, splitAt)
        val body = normalized.substring(splitAt + 2)
        val headers = mutableMapOf<String, String>()
        var currentName: String? = null
        for (line in headerBlock.split('\n')) {
            if (line.isBlank()) continue
            if (line[0] == ' ' || line[0] == '\t') {
                // RFC 2822 folding: continuation of the previous header.
                if (currentName != null) {
                    headers[currentName] = headers[currentName] + " " + line.trim()
                }
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            currentName = name
            headers[name] = if (headers.containsKey(name)) {
                headers[name] + " " + value
            } else {
                value
            }
        }
        return Part(headers, body)
    }

    // ------------------------------------------------------------------
    // Picking the courses frame.
    // ------------------------------------------------------------------

    private data class Decoded(val part: Part, val html: String)

    private fun pickCoursesHtml(parts: List<Part>): Extraction {
        val htmlParts = parts.mapNotNull { part ->
            val contentType = part.headers["content-type"].orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) return@mapNotNull null
            val decoded = decodeBody(part) ?: return@mapNotNull null
            if (decoded.length > MAX_PART_CHARS) {
                Decoded(part, decoded.take(MAX_PART_CHARS))
            } else {
                Decoded(part, decoded)
            }
        }
        if (htmlParts.isEmpty()) return Extraction.Missing(Reason.NO_HTML_PART)

        val scored = htmlParts.map { decoded -> decoded to scorePart(decoded) }
        val best = scored.maxByOrNull { it.second }
            ?: return Extraction.Missing(Reason.NO_HTML_PART)
        // A menu-only archive (frameset + links, no table) must not parse as
        // "zero courses found" downstream — report the archive, not the table.
        if (best.second <= 0) return Extraction.Missing(Reason.NO_COURSE_CONTENT)
        return Extraction.Html(best.first.html)
    }

    private fun scorePart(decoded: Decoded): Int {
        var score = 0
        val location = (
            decoded.part.headers["content-location"].orEmpty() + " " +
                decoded.part.headers["content-id"].orEmpty()
            ).lowercase(Locale.ROOT)
        if (coursesLocationHints.any { location.contains(it) }) score += 100
        val body = decoded.html
        score += coursesBodyHints.count { body.contains(it) } * 10
        // The courses frame is a table-heavy page; a bare frameset scores 0.
        val tables = Regex("(?i)<table").findAll(body).count()
        score += minOf(tables, 5) * 2
        if (body.contains("<frameset", ignoreCase = true)) score -= 50
        return score
    }

    // ------------------------------------------------------------------
    // Body decoding (only text/html parts reach this).
    // ------------------------------------------------------------------

    private fun decodeBody(part: Part): String? {
        val encoding = part.headers["content-transfer-encoding"]
            .orEmpty().trim().lowercase(Locale.ROOT)
        val declaredCharset = part.headers["content-type"]?.let { charsetOf(it) }
        return when (encoding) {
            "base64" -> decodeBase64Body(part.rawBody, declaredCharset)
            "quoted-printable" -> decodeQuotedPrintableBody(part.rawBody, declaredCharset)
            // "binary", "8bit", "7bit" or absent: raw text, trimmed.
            else -> decodeRawBody(part.rawBody, declaredCharset)
        }
    }

    private fun charsetOf(contentType: String): String? {
        val match = Regex("(?i)charset\\s*=\\s*\"?([^\\s;\"]+)\"?").find(contentType)
            ?: return null
        return match.groupValues[1].trim().trim('"', '\'').ifEmpty { null }
    }

    private fun resolveCharset(declared: String?): java.nio.charset.Charset? {
        if (declared.isNullOrBlank()) return null
        return try {
            // Windows-1256 matters: some portals label the frame windows-1256.
            charset(declared.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeRawBody(rawBody: String, declared: String?): String? {
        // The file bytes were already interpreted once (extractFromBytes), so
        // the body text is only wrong when a real charset was declared. Then
        // re-interpret the code points as raw bytes (Latin-1 round-trip is
        // lossless for 0x00-0xFF) and decode with the declared charset.
        val charset = resolveCharset(declared)
        if (charset != null && charset != Charsets.UTF_8 && charset != Charsets.US_ASCII) {
            return try {
                val bytes = rawBody.map { (it.code and 0xFF).toByte() }.toByteArray()
                decodeSafely(bytes, charset) ?: rawBody.trim()
            } catch (_: Exception) {
                rawBody.trim()
            }
        }
        return rawBody.trim().ifEmpty { null }
    }

    private fun decodeBase64Body(rawBody: String, declared: String?): String? {
        val compact = rawBody.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return null
        val bytes = decodeBase64Manual(compact) ?: return null
        if (bytes.isEmpty()) return null
        val charset = resolveCharset(declared) ?: Charsets.UTF_8
        return decodeSafely(bytes, charset)
            ?: decodeSafely(bytes, Charsets.UTF_8)
            ?: decodeWith1256(bytes)
    }

    /** Manual base64: java.util.Base64 needs API 26, minSdk is 24. */
    private fun decodeBase64Manual(compact: String): ByteArray? {
        return try {
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = ByteArrayOutputStream((compact.length * 3) / 4 + 4)
            var buffer = 0
            var bits = 0
            var pad = 0
            for (ch in compact) {
                if (ch == '=') {
                    pad++
                    buffer = buffer shl 6
                    bits += 6
                    continue
                }
                val value = table.indexOf(ch)
                if (value < 0) return null
                buffer = (buffer shl 6) or value
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            if (pad > 2) return null
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeQuotedPrintableBody(rawBody: String, declared: String?): String? {
        val flattened = rawBody.replace("\r\n", "\n")
        val bytes = ByteArrayOutputStream(flattened.length + 16)
        var i = 0
        while (i < flattened.length) {
            val ch = flattened[i]
            if (ch == '=' && i + 1 < flattened.length &&
                (flattened[i + 1] == '\n' || flattened[i + 1] == '\r')
            ) {
                // Soft line break: skip it (and a \n following a \r).
                i += 1
                if (i < flattened.length && flattened[i] == '\r') i += 1
                if (i < flattened.length && flattened[i] == '\n') i += 1
                continue
            }
            if (ch == '=' && i + 2 < flattened.length) {
                val value = flattened.substring(i + 1, i + 3).toIntOrNull(16)
                if (value != null) {
                    bytes.write(value)
                    i += 3
                    continue
                }
            }
            // Raw byte: QP bodies are an ASCII superset; Latin-1 keeps it raw.
            bytes.write(ch.code and 0xFF)
            i += 1
        }
        val data = bytes.toByteArray()
        if (data.isEmpty()) return null
        val charset = resolveCharset(declared) ?: Charsets.UTF_8
        return decodeSafely(data, charset)
            ?: decodeSafely(data, Charsets.UTF_8)
            ?: decodeWith1256(data)
    }

    /** Strict decode: null when the bytes are not valid in [charset]. */
    private fun decodeSafely(bytes: ByteArray, charset: java.nio.charset.Charset): String? {
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWith1256(bytes: ByteArray): String {
        return try {
            bytes.toString(charset("windows-1256"))
        } catch (_: Exception) {
            // Last resort: Latin-1 never fails but may garble Persian text.
            bytes.toString(Charsets.ISO_8859_1)
        }
    }
}
