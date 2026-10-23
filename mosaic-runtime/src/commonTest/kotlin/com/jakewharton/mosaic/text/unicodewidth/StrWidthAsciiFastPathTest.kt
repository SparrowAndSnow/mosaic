package com.jakewharton.mosaic.text.unicodewidth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the printable-ASCII fast path in [strWidth]: a whole printable-ASCII range returns
 * `to - from` without running the per-codepoint width loop, while any non-printable code unit
 * (control, DEL, and everything above U+007E) must fall through to the full state machine so
 * the fast path can never change behavior for real-world text.
 */
class StrWidthAsciiFastPathTest {

	@Test fun wholePrintableAsciiRangesEqualLength() {
		assertEquals(0, strWidth("", 0, 0))
		assertEquals(1, strWidth("A", 0, 1))
		assertEquals(3, strWidth("ABC", 0, 3))
		assertEquals(4, strWidth("\tAB ", 0, 4)) // tab and space are one column each
		assertEquals(96, strWidth(printableAscii, 0, printableAscii.length)) // tab + U+20..U+7E
	}

	@Test fun asciiSubRangesOfMixedText() {
		val text = "A中B"
		assertEquals(1, strWidth(text, 0, 1)) // "A"
		assertEquals(1, strWidth(text, 2, 3)) // "B"
		assertEquals(4, strWidth(text, 0, 3)) // "A中B"
	}

	@Test fun nonPrintableUnitDisablesFastPath() {
		// U+007F (DEL) is not printable ASCII: the fast path must decline and the state machine
		// must resolve it (it counts one column) instead of the fast path claiming a width it
		// cannot measure.
		assertEquals(2, strWidth("a\u007F", 0, 2))
		assertEquals(2, strWidth("\u007Fa", 0, 2))
		assertEquals(3, strWidth("a\u007Fb", 0, 3))
	}

	@Test fun controlCharactersDisableFastPath() {
		assertEquals(3, strWidth("a\nb", 0, 3)) // LF starts the LINE_FEED state, not ASCII width
		assertEquals(2, strWidth("a\u0000", 0, 2))
	}

	@Test fun asciiAdjacentToWideTextKeepsResult() {
		assertEquals(3, strWidth("A中", 0, 2))
		assertEquals(3, strWidth("中A", 0, 2)) // wide + ASCII suffix
	}

	private val printableAscii: String
		get() = buildString {
			append('\t')
			for (code in 0x20..0x7E) {
				append(code.toChar())
			}
		}
}
