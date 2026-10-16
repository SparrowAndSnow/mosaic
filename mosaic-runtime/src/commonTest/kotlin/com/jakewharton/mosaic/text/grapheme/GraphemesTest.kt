package com.jakewharton.mosaic.text.grapheme

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemesTest {

	private fun clusters(text: String): List<String> = text.graphemeClusters().toList()

	@Test fun emptyText() {
		assertEquals(emptyList(), clusters(""))
	}

	@Test fun asciiLettersBreak() {
		assertEquals(listOf("a", "b"), clusters("ab"))
	}

	@Test fun combiningMarkJoinsBase() {
		assertEquals(listOf("a\u0301", "b"), clusters("a\u0301b"))
	}

	@Test fun crLfPairIsSingleCluster() {
		assertEquals(listOf("\r\n"), clusters("\r\n"))
	}

	@Test fun crBreaksFromOther() {
		assertEquals(listOf("\r", "X"), clusters("\rX"))
	}

	@Test fun otherBreaksFromCr() {
		assertEquals(listOf("X", "\r"), clusters("X\r"))
	}

	@Test fun controlBreaksBothSides() {
		assertEquals(listOf("x", "\u0001", "y"), clusters("x\u0001y"))
		assertEquals(listOf("\u0001", "x"), clusters("\u0001x"))
	}

	@Test fun hangulLVtJoins() {
		assertEquals(listOf("\u1100\u1161\u11A8"), clusters("\u1100\u1161\u11A8"))
		assertEquals(listOf("\u1100\u1100"), clusters("\u1100\u1100"))
		assertEquals(listOf("\uAC00\u11A8"), clusters("\uAC00\u11A8"))
	}

	@Test fun prependJoinsFollowing() {
		assertEquals(listOf("\u0600\u0020"), clusters("\u0600\u0020"))
		assertEquals(listOf("\u0600", "\u0001"), clusters("\u0600\u0001"))
		assertEquals(listOf("a", "\u0600"), clusters("a\u0600"))
	}

	@Test fun spacingMarkJoinsBase() {
		assertEquals(listOf("a\u0903"), clusters("a\u0903"))
		assertEquals(listOf(" \u0308\u0903"), clusters(" \u0308\u0903"))
	}

	@Test fun regionalIndicatorPairs() {
		assertEquals(
			listOf("\uD83C\uDDE6\uD83C\uDDE7"),
			clusters("\uD83C\uDDE6\uD83C\uDDE7"),
		)
		assertEquals(
			listOf("\uD83C\uDDE6\uD83C\uDDE7", "\uD83C\uDDE8"),
			clusters("\uD83C\uDDE6\uD83C\uDDE7\uD83C\uDDE8"),
		)
		assertEquals(
			listOf("a", "\uD83C\uDDE6\uD83C\uDDE7", "b"),
			clusters("a\uD83C\uDDE6\uD83C\uDDE7b"),
		)
	}

	@Test fun zwjEmojiFamilyIsSingleCluster() {
		assertEquals(
			listOf("\uD83D\uDC76\u200D\uD83D\uDD6D"),
			clusters("\uD83D\uDC76\u200D\uD83D\uDD6D"),
		)
	}

	@Test fun emojiModifierJoinsBase() {
		assertEquals(
			listOf("\uD83D\uDC76\uD83C\uDFFF"),
			clusters("\uD83D\uDC76\uD83C\uDFFF"),
		)
	}

	@Test fun keycapAndVariationSelectors() {
		assertEquals(listOf("#\uFE0F\u20E3"), clusters("#\uFE0F\u20E3"))
		assertEquals(listOf("\u2600\uFE0F"), clusters("\u2600\uFE0F"))
	}
}
