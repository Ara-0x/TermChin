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
 * - Parsing is BYTE-LEVEL: the archive is never decoded as one text blob
 *   (Blink embeds raw binary images that are not valid UTF-8, and a whole
 *   file charset fallback would corrupt the Persian HTML). Bytes are mapped
 *   1:1 to ISO-8859-1 for structure scanning, and each part's body is decoded
 *   individually by its transfer encoding + charset (UTF-8 with a
 *   Windows-1256 fallback for legacy portals).
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

    /** Extracts the courses HTML from raw MHTML text (fixtures/paste path). */
    fun extract(raw: String): Extraction = extractFromBytes(raw.toByteArray(Charsets.UTF_8))

    /**
     * PRIMARY entry point: byte-level MHTML parsing.
     *
     * The whole file must NEVER be decoded as text: Blink archives embed raw
     * binary images (invalid UTF-8), so decoding the file as UTF-8 fails and a
     * charset fallback (windows-1256) then corrupts the Persian HTML body —
     * the parser would report "no courses". Instead the bytes are mapped
     * 1:1 to ISO-8859-1 (char index == byte index, so slicing is byte-exact),
     * headers/boundaries are scanned as ASCII, and each part's body is decoded
     * on its own by transfer encoding + charset.
     */
    fun extractFromBytes(bytes: ByteArray): Extraction {
        if (bytes.isEmpty()) return Extraction.Missing(Reason.NOT_MHTML)
        // Lossless 1 byte <-> 1 char: structure work stays byte-exact; only the
        // per-part body decoders below interpret character encoding.
        val archive = String(bytes, Charsets.ISO_8859_1)
        val boundary = findBoundary(archive) ?: return Extraction.Missing(Reason.NOT_MHTML)
        val parts = splitParts(archive, boundary)
        if (parts.isEmpty()) return Extraction.Missing(Reason.NOT_MHTML)
        return pickCoursesHtml(parts)
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

    /**
     * One MIME part. [rawBody] is an ISO-8859-1 view of the part's bytes:
     * every char maps 1:1 to one byte, so the body decoders below can slice it
     * byte-exactly and only interpret encoding at decode time.
     */
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

    /**
     * Decodes a raw ("binary"/"8bit"/"7bit") part — the encoding Blink uses
     * for its HTML frames. [rawBody] is the byte-exact ISO-8859-1 view, so it
     * is turned back into bytes first, then decoded with the part's declared
     * charset: strict UTF-8 by default (Pooya saves are UTF-8), falling back
     * to Windows-1256 for legacy portals, Latin-1 as the never-failing last
     * resort. Blank bodies yield null.
     */
    private fun decodeRawBody(rawBody: String, declared: String?): String? {
        if (rawBody.isBlank()) return null
        val bytes = rawBody.toByteArray(Charsets.ISO_8859_1)
        val declaredCharset = resolveCharset(declared)
        if (declaredCharset != null) {
            // A wrong/deceptive label must not lose the content: fall through
            // to the UTF-8 attempt when the declared charset rejects the bytes.
            decodeSafely(bytes, declaredCharset)?.let { decoded ->
                decoded.trim().ifEmpty { null }?.let { return it }
            }
        }
        return decodeSafely(bytes, Charsets.UTF_8)?.trim()?.ifEmpty { null }
            ?: decodeWith1256(bytes).trim().ifEmpty { null }
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
