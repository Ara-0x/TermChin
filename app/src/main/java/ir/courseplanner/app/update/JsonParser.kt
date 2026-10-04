package ir.courseplanner.app.update

/**
 * A deliberately tiny JSON reader: just enough to navigate GitHub's
 * "latest release" payload (`tag_name`, `html_url`, `assets[]`).
 *
 * Why hand-rolled instead of `org.json` or kotlinx-serialization?
 *  - `org.json` ships with Android, but on the JVM *unit-test* classpath it is
 *    only a stub ("not mocked") — parsing could only ever be tested under
 *    Robolectric, not in the fast pure-JVM suite.
 *  - Adding kotlinx-serialization for one 2 KB response is a dependency the
 *    project promised not to take ("add the library only when you actually
 *    need one").
 *
 * The parser is strict rather than lenient: anything malformed returns null, and
 * [GitHubReleaseParser] treats null as "no update known". Unknown fields are
 * ignored, and a trailing-garbage check rejects concatenated responses.
 */
internal sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json
}

internal fun Json.Obj.string(key: String): String? =
    (fields[key] as? Json.Str)?.value

internal fun Json.Obj.array(key: String): List<Json>? =
    (fields[key] as? Json.Arr)?.items

internal object JsonParser {

    fun parse(text: String): Json? {
        val reader = Reader(text)
        val value = reader.readValue() ?: return null
        reader.skipWhitespace()
        // Concatenated or trailing garbage is not a valid response.
        if (!reader.atEnd()) return null
        return value
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\n\r") pos++
        }

        fun readValue(): Json? {
            skipWhitespace()
            if (atEnd()) return null
            return when (text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()?.let { Json.Str(it) }
                't' -> readLiteral("true")?.let { Json.Bool(true) }
                'f' -> readLiteral("false")?.let { Json.Bool(false) }
                'n' -> readLiteral("null")?.let { Json.Null }
                '-', in '0'..'9' -> readNumber()?.let { Json.Num(it) }
                else -> null
            }
        }

        private fun readObject(): Json.Obj? {
            pos++ // consume '{'
            val fields = LinkedHashMap<String, Json>()
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return Json.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[pos] != '"') return null
                val key = readString() ?: return null
                skipWhitespace()
                if (atEnd() || text[pos] != ':') return null
                pos++
                val value = readValue() ?: return null
                fields[key] = value
                skipWhitespace()
                if (atEnd()) return null
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return Json.Obj(fields)
                    }
                    else -> return null
                }
            }
        }

        private fun readArray(): Json.Arr? {
            pos++ // consume '['
            val items = ArrayList<Json>()
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return Json.Arr(items)
            }
            while (true) {
                val value = readValue() ?: return null
                items.add(value)
                skipWhitespace()
                if (atEnd()) return null
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return Json.Arr(items)
                    }
                    else -> return null
                }
            }
        }

        private fun readString(): String? {
            pos++ // consume opening quote
            val out = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos++]
                when (ch) {
                    '"' -> return out.toString()
                    // A backslash must be followed by a valid escape.
                    '\\' -> {
                        if (atEnd()) return null
                        when (val esc = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) return null
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: return null
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> return null
                        }
                    }
                    // Raw control characters are invalid inside a JSON string.
                    else -> {
                        if (ch < ' ') return null
                        out.append(ch)
                    }
                }
            }
            // Unterminated string.
            return null
        }

        private fun readLiteral(word: String): String? {
            if (!text.startsWith(word, pos)) return null
            pos += word.length
            return word
        }

        private fun readNumber(): Double? {
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            if (pos < text.length && text[pos] == '.') {
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val token = text.substring(start, pos)
            // Guard against "-", "1e", "1." and other partial numbers.
            if (token.isEmpty() || token == "-" || token.endsWith('e') ||
                token.endsWith('E') || token.endsWith('+') || token.endsWith('-') ||
                token.endsWith('.')
            ) {
                return null
            }
            return token.toDoubleOrNull()
        }
    }
}