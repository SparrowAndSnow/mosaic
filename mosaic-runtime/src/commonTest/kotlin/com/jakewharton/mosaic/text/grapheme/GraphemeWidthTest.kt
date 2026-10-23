package com.jakewharton.mosaic.text.grapheme

import com.jakewharton.mosaic.text.charWidth
import com.jakewharton.mosaic.text.unicodewidth.unicodeWidth
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemeWidthTest {

	@Test fun emptyText() {
		assertEquals(0, "".graphemeWidth())
	}

	@Test fun printableAsciiIsOneColumnPerUnit() {
		assertEquals(3, "ABC".graphemeWidth())
		assertEquals(1, " ".graphemeWidth())
	}

	@Test fun controlCharactersOccupyZeroColumns() {
		assertEquals(0, "\t".graphemeWidth())
		assertEquals(0, "\r".graphemeWidth())
		assertEquals(0, "\b".graphemeWidth())
		assertEquals(0, "\u001B".graphemeWidth())
		assertEquals(0, "\u007F".graphemeWidth())
		assertEquals(0, "\u0085".graphemeWidth()) // NEL (C1)
		assertEquals(2, "\tAB".graphemeWidth()) // control filtered out, does not advance a column
		assertEquals(2, "A\tB".graphemeWidth())
	}

	@Test fun cjkText() {
		assertEquals(2, "中".graphemeWidth())
		assertEquals(4, "A中B".graphemeWidth())
	}

	@Test fun combiningMarksAreZeroWidth() {
		assertEquals(1, "e\u0301".graphemeWidth())
		assertEquals(0, "\u0301".graphemeWidth()) // lone combining mark
		assertEquals(0, "\u200B".graphemeWidth()) // ZWSP
	}

	@Test fun zwjEmojiFamilyIsTwoColumns() {
		assertEquals(2, "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67".graphemeWidth()) // 👨👩👧
		assertEquals(2, "\uD83D\uDC68\u200D\uD83D\uDC67".graphemeWidth()) // 👨👧
	}

	@Test fun flagsAreTwoColumns() {
		assertEquals(2, "\uD83C\uDDE8\uD83C\uDDF3".graphemeWidth()) // 🇨🇳
		assertEquals(1, "\uD83C\uDDE8".graphemeWidth()) // lone regional indicator
	}

	@Test fun keycapSequences() {
		assertEquals(2, "#\uFE0F\u20E3".graphemeWidth())
		assertEquals(1, "#\u20E3".graphemeWidth())
	}

	@Test fun variationSelectors() {
		assertEquals(1, "©".graphemeWidth())
		assertEquals(2, "©\uFE0F".graphemeWidth()) // VS16 switches to emoji presentation
		assertEquals(1, "\u2600\uFE0E".graphemeWidth()) // VS15 keeps text presentation
		assertEquals(2, "\u2600\uFE0F".graphemeWidth()) // VS16
	}

	@Test fun hangulJamoFoldsIntoBaseWidth() {
		assertEquals(2, "각".graphemeWidth())
		assertEquals(2, "\u1100\u1161".graphemeWidth()) // L+V jamo
		assertEquals(2, "\u1100\u1161\u11A8".graphemeWidth()) // L+V+T jamo
	}

	@Test fun plainEmojiIsTwoColumns() {
		assertEquals(2, "\uD83D\uDE00".graphemeWidth()) // 😀
	}

	@Test fun terminalEmojiOverrideMatchesDrawing() {
		// These emoji blocks are marked narrow by UAX #11 yet render two cells wide in terminals;
		// cluster measurement applies the same override drawing uses, so the two never disagree.
		val narrowInUax11 = listOf(0x1F0A1, 0x1F900, 0x1FA00)
		for (codepoint in narrowInUax11) {
			val text = codepoint.toChars()
			assertEquals(1, text.unicodeWidth(), "expected narrow under UAX #11: 0x" + codepoint.toString(16))
			assertEquals(2, charWidth(codepoint))
			assertEquals(2, text.graphemeWidth(), "0x" + codepoint.toString(16))
		}
		// Mahjong tiles are already wide under UAX #11, so the override must not change them.
		assertEquals(2, "\uD83C\uDC04".unicodeWidth())
		assertEquals(2, "\uD83C\uDC04".graphemeWidth())
		assertEquals(4, ("\uD83C\uDC04" + "\uD83C\uDC04").graphemeWidth())
	}

	@Test fun totalWidthEqualsSumOfClusterWidths() {
		val samples = listOf(
			"",
			"ABC",
			"A中B",
			"e\u0301",
			"\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
			"\uD83C\uDDE8\uD83C\uDDF3 \uD83C\uDDFA\uD83C\uDDF8",
			"#\uFE0F\u20E3 1\uFE0F\u20E3",
			"\u1100\u1161\u11A8 \uAC00 \uD83D\uDE00",
			"\u0915\u094D\u0915", // Devanagari conjunct (ka-virama-ka)
			"\u0915\u094D\u200D\u0937", // Devanagari + ZWJ sequence
		)
		for (sample in samples) {
			val expected = sample.graphemeClusters().map { cluster ->
				val width = cluster.unicodeWidth()
				if (width == 1 && charWidth(cluster.codepointAt(0)) == 2) 2 else width
			}.sum()
			assertEquals(expected, sample.graphemeWidth(), "total != sum of clusters for: $sample")
		}
	}

	@Test fun clusterWidthAgreesWithStringWidthWhenNoOverride() {
		// Where no terminal emoji override applies, per-cluster and whole-string UAX #11 agree.
		val samples = listOf(
			"ABC",
			"A中B",
			"e\u0301",
			"\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
			"\uD83C\uDDE8\uD83C\uDDF3 \uD83C\uDDFA\uD83C\uDDF8",
			"#\uFE0F\u20E3 1\uFE0F\u20E3",
			"\u1100\u1161\u11A8 \uAC00 \uD83D\uDE00",
			"\u0915\u094D\u0915",
		)
		for (sample in samples) {
			assertEquals(
				sample.unicodeWidth(),
				sample.graphemeWidth(),
				"cluster measurement diverged from whole-string width for: $sample",
			)
		}
	}

	private fun Int.toChars(): String {
		if (this < 0x10000) return this.toChar().toString()
		val v = this - 0x10000
		val builder = StringBuilder(2)
		builder.append((0xD800 + (v shr 10)).toChar())
		builder.append((0xDC00 + (v and 0x3FF)).toChar())
		return builder.toString()
	}
}
