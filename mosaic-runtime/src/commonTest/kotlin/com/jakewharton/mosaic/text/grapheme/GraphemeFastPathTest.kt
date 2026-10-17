package com.jakewharton.mosaic.text.grapheme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks in the printable-ASCII fast path of [forEachGraphemeClusterRange]: ASCII code points must
 * be emitted exactly as the full state-machine implementation would (each one its own cluster),
 * including their interaction with combining marks, ZWJ emoji families, regional-indicator pairs,
 * and Indic conjunct sequences on either side.
 */
class GraphemeFastPathTest {

	private fun clustersOf(text: String): List<String> = buildList {
		text.forEachGraphemeClusterRange { start, end ->
			add(text.substring(start, end))
		}
	}

	// The pre-optimization loop shape, kept here as an oracle for the fast path.
	private fun clustersOfRef(text: String): List<String> = buildList {
		text.forEachGraphemeClusterRangeRef { start, end ->
			add(text.substring(start, end))
		}
	}

	@Test fun asciiIsOneClusterPerChar() {
		assertEquals(listOf("a", "b", "c"), clustersOf("abc"))
		assertEquals(listOf("r", "o", "w", " ", "0", "5"), clustersOf("row 05"))
		assertEquals(listOf("\t"), clustersOf("\t"))
		assertEquals(listOf("a", "\t", "b"), clustersOf("a\tb"))
	}

	@Test fun prependStillAttachesToFollowingAscii() {
		// GB9b: a Prepend (here ARABIC NUMBER SIGN) binds to the next code point even when it is
		// an ASCII space. The fast path must not split them.
		assertEquals(listOf("\u0600 ", "x"), clustersOf("\u0600 x"))
	}

	@Test fun asciiInteractsWithCombiningMarks() {
		// Combining marks attach to the preceding base even when it is ASCII; the fast path must
		// break before the base, then let the slow path fold the combining mark in.
		assertEquals(listOf("e\u0301", "x"), clustersOf("e\u0301x"))
		assertEquals(listOf("a", "e\u0301"), clustersOf("ae\u0301"))
	}

	@Test fun asciiAfterEmojiFamiliesBreaksCleanly() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨‍👩‍👧
		assertEquals(listOf(family, "x", "!"), clustersOf("${family}x!"))
		assertEquals(listOf("q", family), clustersOf("q$family"))
	}

	@Test fun asciiAfterRegionalIndicatorPairBreaksCleanly() {
		val flag = "\uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳 (RI pair)
		assertEquals(listOf(flag, "x"), clustersOf("${flag}x"))
		assertEquals(listOf("u", flag, "v"), clustersOf("u${flag}v"))
	}

	@Test fun asciiAfterIndicConjunctBreaksCleanly() {
		val conjunct = "क्षि" // Consonant + vowel sign sequence
		assertEquals(listOf(conjunct, "z"), clustersOf("${conjunct}z"))
		assertEquals(listOf("k", conjunct), clustersOf("k$conjunct"))
	}

	@Test fun stageMatching() {
		// Every cluster boundary the fast path produces must match the full implementation.
		val stage = listOf(
			"Hello, World! 简体中文测试 e\u0301\uD83D\uDE00",
			"👨\u200D👩\u200D👧 with some plain ascii",
			"🇨🇳🇨🇳x and \uD83D\uDC68\u200D\uD83D\uDE00 after",
			"\tk\t lorem\u0301 ipsum \u200D\u0301e",
			"अनुच्छेद\u0301 z \uD83C\uDDE9\uD83C\uDDEA end",
			"1234567890",
		)
		for (text in stage) {
			assertEquals(clustersOfRef(text), clustersOf(text))
		}
	}
}

private fun CharSequence.forEachGraphemeClusterRangeRef(block: (start: Int, end: Int) -> Unit) {
	if (isEmpty()) return

	var clusterStart = 0
	var index = 0
	var first = true
	var previousClass = GCB_OTHER
	var regionalIndicatorsOdd = false
	var extendedPictographicRun = false
	var runEndsWithZwj = false
	var conjunctState = STATE_NONE

	while (index < length) {
		val codepoint = codepointAt(index)
		val charCount = if (codepoint >= 0x10000) 2 else 1
		val currentClass = gcbClassOf(codepoint)
		val currentIsExtPict = isExtendedPictographic(codepoint)
		val currentInCb = inCbClassOf(codepoint)

		val breakBefore = !first && shouldBreakRef(
			previousClass, currentClass, currentIsExtPict, currentInCb,
			regionalIndicatorsOdd,
			extendedPictographicRun && runEndsWithZwj,
			conjunctState == STATE_CONJUNCT,
		)

		if (breakBefore) {
			block(clusterStart, index)
			clusterStart = index
			extendedPictographicRun = currentIsExtPict
			runEndsWithZwj = false
			conjunctState = STATE_NONE
		} else {
			when {
				currentClass == GCB_EXTEND -> runEndsWithZwj = false
				currentClass == GCB_ZWJ -> runEndsWithZwj = extendedPictographicRun
				currentClass == GCB_SPACING_MARK -> {
					extendedPictographicRun = false
					runEndsWithZwj = false
				}
				else -> {
					extendedPictographicRun = currentIsExtPict
					runEndsWithZwj = false
				}
			}
		}

		conjunctState = when {
			currentInCb == INC_CONS -> STATE_CONS
			currentInCb == INC_LINKER ->
				if (conjunctState == STATE_CONS || conjunctState == STATE_CONJUNCT) STATE_CONJUNCT else STATE_NONE
			currentInCb == INC_EXTEND ->
				if (conjunctState == STATE_CONS || conjunctState == STATE_CONJUNCT) conjunctState else STATE_NONE
			else -> STATE_NONE
		}

		previousClass = currentClass
		regionalIndicatorsOdd =
			if (currentClass == GCB_REGIONAL_INDICATOR) !regionalIndicatorsOdd else false
		index += charCount
		first = false
	}
	block(clusterStart, length)
}

private fun shouldBreakRef(
	previousClass: Int,
	currentClass: Int,
	currentIsExtPict: Boolean,
	currentInCb: Int,
	regionalIndicatorsOdd: Boolean,
	grapheme11Eligible: Boolean,
	grapheme9cEligible: Boolean,
): Boolean {
	if (previousClass == GCB_CR && currentClass == GCB_LF) return false
	if (previousClass == GCB_CONTROL || previousClass == GCB_CR || previousClass == GCB_LF) return true
	if (currentClass == GCB_CONTROL || currentClass == GCB_CR || currentClass == GCB_LF) return true
	if (regionalIndicatorsOdd && currentClass == GCB_REGIONAL_INDICATOR) return false
	if (currentClass == GCB_EXTEND || currentClass == GCB_ZWJ) return false
	if (currentClass == GCB_SPACING_MARK) return false
	if (previousClass == GCB_PREPEND) return false
	if (grapheme9cEligible && currentInCb == INC_CONS) return false
	if (grapheme11Eligible && currentIsExtPict) return false
	// GB6: L × (L | V | LV | LVT)
	if (previousClass == GCB_L &&
		(currentClass == GCB_L || currentClass == GCB_V || currentClass == GCB_LV ||
			currentClass == GCB_LVT)
	) {
		return false
	}
	// GB7: (LV | V) × (V | T)
	if ((previousClass == GCB_LV || previousClass == GCB_V) &&
		(currentClass == GCB_V || currentClass == GCB_T)
	) {
		return false
	}
	// GB8: (LVT | T) × T
	if ((previousClass == GCB_LVT || previousClass == GCB_T) && currentClass == GCB_T) {
		return false
	}
	// GB999: Any ÷ Any
	return true
}
