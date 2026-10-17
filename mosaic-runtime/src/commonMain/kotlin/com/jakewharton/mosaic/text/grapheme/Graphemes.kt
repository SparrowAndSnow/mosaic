package com.jakewharton.mosaic.text.grapheme

/**
 * Returns the [extended grapheme clusters](https://www.unicode.org/reports/tr29/)
 * of this text as subslices, in order.
 *
 * Extended grapheme clusters are the user-perceived characters of a string: a base character
 * together with all its combining marks, ZWJ emoji sequences, variation selectors, regional
 * indicator pairs, Indic conjunct sequences, and Hangul jamo. Text handling (measurement,
 * drawing, wrapping, truncation) should operate on these boundaries rather than on individual
 * code points so combining sequences such as ZWJ emoji families or those containing keycaps are
 * never split or partially overwritten.
 */
public fun CharSequence.graphemeClusters(): Sequence<String> = sequence {
	forEachGraphemeClusterRange { start, end ->
		yield(substring(start, end))
	}
}

/**
 * Invokes [block] with the `[start, end)` index ranges of every extended grapheme cluster in
 * this text, in order.
 *
 * This is the allocation-free core shared by [graphemeClusters] (which materializes [String]
 * slices) and cluster-aware measurement such as `graphemeWidth`. Being `inline`, the loop is a
 * single pass with no per-cluster closure or [String] allocation.
 */
internal inline fun CharSequence.forEachGraphemeClusterRange(block: (start: Int, end: Int) -> Unit) {
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
		// Fast path: a printable-ASCII code point (GCB_OTHER) or tab (GCB_CONTROL) is a single
		// code unit that normally starts a new cluster (GB4/GB5/GB999) and never participates in
		// the RI-pair, extended-pictographic, or Indic-conjunct state machines. The one exception
		// is GB9b, where a Prepend attaches to the following code point even if it is an ASCII
		// space, so the fast path must not fire right after a Prepend. Emit directly without the
		// look-ups below, mirroring the slow path's break-branch state updates.
		val unit = this[index].code
		if ((unit in 0x20..0x7E && previousClass != GCB_PREPEND) || unit == 0x09) {
			if (!first) {
				block(clusterStart, index)
				clusterStart = index
				conjunctState = STATE_NONE
			}
			previousClass = if (unit == 0x09) GCB_CONTROL else GCB_OTHER
			regionalIndicatorsOdd = false
			extendedPictographicRun = false
			runEndsWithZwj = false
			index++
			first = false
			continue
		}

		val codepoint = codepointAt(index)
		val charCount = if (codepoint >= 0x10000) 2 else 1
		val currentClass = gcbClassOf(codepoint)
		val currentIsExtPict = isExtendedPictographic(codepoint)
		val currentInCb = inCbClassOf(codepoint)

		val breakBefore = !first && shouldBreak(
			previousClass = previousClass,
			currentClass = currentClass,
			currentIsExtPict = currentIsExtPict,
			currentInCb = currentInCb,
			regionalIndicatorsOdd = regionalIndicatorsOdd,
			grapheme11Eligible = extendedPictographicRun && runEndsWithZwj,
			grapheme9cEligible = conjunctState == STATE_CONJUNCT,
		)

		if (breakBefore) {
			block(clusterStart, index)
			clusterStart = index
			extendedPictographicRun = currentIsExtPict
			runEndsWithZwj = false
			// A new cluster starts fresh, so the Indic conjunct pattern resets. Only a
			// Consonant (evaluated below) can immediately re-enter the pattern.
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

// Projection states for the GB9c pattern, tracked separately from each code point's own
// Indic_Conjunct_Break class so the rule can be evaluated without lookahead.
internal const val STATE_NONE = 0
internal const val STATE_CONS = 1
internal const val STATE_CONJUNCT = 2

/**
 * Implements the UAX #29 extended-grapheme-cluster boundary rules GB3-GB999. Returns `true` when
 * a boundary is allowed between the previous code point and the current one.
 */
private fun shouldBreak(
	previousClass: Int,
	currentClass: Int,
	currentIsExtPict: Boolean,
	currentInCb: Int,
	regionalIndicatorsOdd: Boolean,
	grapheme11Eligible: Boolean,
	grapheme9cEligible: Boolean,
): Boolean {
	// GB3: CR × LF
	if (previousClass == GCB_CR && currentClass == GCB_LF) return false
	// GB4: (Control | CR | LF) ÷
	if (previousClass == GCB_CONTROL || previousClass == GCB_CR || previousClass == GCB_LF) {
		return true
	}
	// GB5: ÷ (Control | CR | LF)
	if (currentClass == GCB_CONTROL || currentClass == GCB_CR || currentClass == GCB_LF) {
		return true
	}
	// GB12/GB13: RI × RI only within an incomplete pair.
	if (regionalIndicatorsOdd && currentClass == GCB_REGIONAL_INDICATOR) return false
	// GB9: × (Extend | ZWJ)
	if (currentClass == GCB_EXTEND || currentClass == GCB_ZWJ) return false
	// GB9a: × SpacingMark
	if (currentClass == GCB_SPACING_MARK) return false
	// GB9b: Prepend ×
	if (previousClass == GCB_PREPEND) return false
	// GB9c: Consonant (Extend | InCB=Extend)* Linker (Extend | InCB=Extend)* × Consonant
	if (grapheme9cEligible && currentInCb == INC_CONS) return false
	// GB11: \p{Extended_Pictographic} Extend* ZWJ × \p{Extended_Pictographic}
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
