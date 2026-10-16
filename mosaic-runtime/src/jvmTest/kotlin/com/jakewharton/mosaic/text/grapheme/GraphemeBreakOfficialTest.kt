package com.jakewharton.mosaic.text.grapheme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates [CharSequence.graphemeClusters] against every case in the official Unicode
 * GraphemeBreakTest.txt file (checked in under jvmTest resources), which exercises all
 * UAX #29 boundary rules pair-wise.
 */
class GraphemeBreakOfficialTest {

	@Test fun officialGraphemeBreakSuite() {
		val stream = checkNotNull(javaClass.getResourceAsStream("/grapheme/GraphemeBreakTest.txt"))
		var cases = 0
		stream.bufferedReader().useLines { lines ->
			for (rawLine in lines) {
				val line = rawLine.trim()
				if (line.isEmpty() || line.startsWith("#")) continue
				val data = line.substringBefore('#').trim()
				if (data.isEmpty()) continue

				val tokens = data.split(Regex("\\s+"))
				check(tokens[0] == "÷") { "Unexpected leading token in: $line" }
				check(tokens.size % 2 == 1) { "Even token count in: $line" }
				check(tokens.last() == "÷") { "Missing trailing ÷ in: $line" }

				val input = StringBuilder()
				val expected = mutableListOf<String>()
				val current = StringBuilder()

				var i = 1
				while (i < tokens.size) {
					val codepoint = Integer.parseInt(tokens[i], 16)
					appendCodepoint(input, codepoint)
					appendCodepoint(current, codepoint)
					i++
					// Boundary following this code point (always present; last is a trailing ÷).
					val boundary = tokens[i]
					check(boundary == "÷" || boundary == "×") { "Unexpected boundary in: $line" }
					if (boundary == "÷") {
						expected += current.toString()
						current.setLength(0)
					}
					i++
				}

				val actual = input.toString().graphemeClusters().toList()
				assertEquals(expected, actual, "break mismatch: $line")
				cases++
			}
		}
		assertTrue(cases > 1000, "exhausted official suite unexpectedly: $cases cases")
	}

	private fun appendCodepoint(buffer: StringBuilder, codepoint: Int) {
		if (codepoint < 0x10000) {
			buffer.append(codepoint.toChar())
		} else {
			val v = codepoint - 0x10000
			buffer.append((0xD800 + (v shr 10)).toChar())
			buffer.append((0xDC00 + (v and 0x3FF)).toChar())
		}
	}
}
