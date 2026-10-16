package com.jakewharton.mosaic.text.grapheme

/**
 * Grapheme_Cluster_Break property values from UAX #29. The [GraphemeBreakTables] arrays use
 * these values to map a code point to its cluster break class.
 */
internal const val GCB_OTHER = 0
internal const val GCB_CR = 1
internal const val GCB_LF = 2
internal const val GCB_CONTROL = 3
internal const val GCB_EXTEND = 4
internal const val GCB_ZWJ = 5
internal const val GCB_REGIONAL_INDICATOR = 6
internal const val GCB_PREPEND = 7
internal const val GCB_SPACING_MARK = 8
internal const val GCB_L = 9
internal const val GCB_V = 10
internal const val GCB_T = 11
internal const val GCB_LV = 12
internal const val GCB_LVT = 13

/** Returns the [Grapheme_Cluster_Break][GCB_OTHER] class of [codepoint], or [GCB_OTHER]. */
internal fun gcbClassOf(codepoint: Int): Int {
	val ranges = GCB_RANGES
	var low = 0
	var high = ranges.size / 3 - 1
	while (low <= high) {
		val mid = (low + high) ushr 1
		val index = mid * 3
		val c = codepoint
		val start = ranges[index]
		if (c < start) {
			high = mid - 1
		} else if (c > ranges[index + 1]) {
			low = mid + 1
		} else {
			return ranges[index + 2]
		}
	}
	return GCB_OTHER
}

/** Returns whether [codepoint] has the Extended_Pictographic property (used by rule GB11). */
internal fun isExtendedPictographic(codepoint: Int): Boolean {
	val ranges = EXTENDED_PICTOGRAPHIC_RANGES
	var low = 0
	var high = ranges.size / 2 - 1
	while (low <= high) {
		val mid = (low + high) ushr 1
		val index = mid * 2
		if (codepoint < ranges[index]) {
			high = mid - 1
		} else if (codepoint > ranges[index + 1]) {
			low = mid + 1
		} else {
			return true
		}
	}
	return false
}

// Indic_Conjunct_Break (InCB) values used by rule GB9c.
internal const val INC_OTHER = 0
internal const val INC_CONS = 1
internal const val INC_LINKER = 2
internal const val INC_EXTEND = 3

/** Returns the [Indic_Conjunct_Break][INC_OTHER] value of [codepoint], or [INC_OTHER]. */
internal fun inCbClassOf(codepoint: Int): Int {
	val ranges = INDIC_CONJUNCT_RANGES
	var low = 0
	var high = ranges.size / 3 - 1
	while (low <= high) {
		val mid = (low + high) ushr 1
		val index = mid * 3
		if (codepoint < ranges[index]) {
			high = mid - 1
		} else if (codepoint > ranges[index + 1]) {
			low = mid + 1
		} else {
			return ranges[index + 2]
		}
	}
	return INC_OTHER
}

/** Returns the code point at [index], decoding a high/low surrogate pair when present. */
internal fun CharSequence.codepointAt(index: Int): Int {
	val high = this[index]
	if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
		val low = this[index + 1]
		return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
	}
	return high.code
}
