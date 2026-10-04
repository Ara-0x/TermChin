package ir.courseplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JUnit, like everything in the update pipeline: the reader is a pure
 * Kotlin parser with zero platform dependencies.
 */
class JsonParserTest {

    @Test
    fun `parses nested release-shape payloads`() {
        val root = JsonParser.parse(
            """{"a":1,"b":[true,{"c":"x"}],"d":null}"""
        ) as? Json.Obj ?: throw AssertionError("expected an object")
        assertEquals(1.0, (root.fields["a"] as? Json.Num)?.value)
        assertEquals(true, ((root.array("b")!![0]) as? Json.Bool)?.value)
        assertEquals("x", (((root.array("b")!![1]) as? Json.Obj)?.string("c")))
        assertTrue(root.fields["d"] is Json.Null)
    }

    @Test
    fun `decodes escapes and unicode`() {
        val root = JsonParser.parse("""{"s":"a\"b\\c\/d\n\u0041"}""") as? Json.Obj
        assertEquals("a\"b\\c/d\nA", root?.string("s"))
    }

    @Test
    fun `rejects malformed documents instead of guessing`() {
        listOf(
            "",
            "{",
            """{"a":}""",
            """{"a":1""",
            "[1,2",
            """{"a":"unterminated""",
            """{"a":tru}""",
            """{"a":nul}""",
            """{"a":"bad\qescape"}""",
            """{"a":"raw
                newline"}""",
            """{"a":1} garbage""",
            "{} {}",
            "[",
            "{,}"
        ).forEach { bad ->
            assertNull("should reject: $bad", JsonParser.parse(bad))
        }
    }
}