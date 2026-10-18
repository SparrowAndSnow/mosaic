package com.jakewharton.mosaic.text.grapheme

import com.jakewharton.mosaic.text.charWidth
import com.jakewharton.mosaic.text.isControl
import com.jakewharton.mosaic.text.unicodewidth.strWidth

/**
 * Returns the number of terminal columns this text occupies, counted per extended grapheme
 * cluster.
 *
 * [UAX #11] is applied independently to each cluster rather than to the whole string, so a
 * multi-code-point cluster is always measured as one rendering unit: a ZWJ emoji family, keycap,
 * regional-indicator pair, or Hangul jamo run never contributes more than its single base cell
 * plus continuation cells. Terminals render certain emoji blocks two cells wide even though
 * UAX #11 marks them narrow; that override is applied here at cluster granularity so that
 * measurement always agrees with drawing.
 */
internal fun CharSequence.graphemeWidth(): Int {
	if (isPrintableAscii()) return length
	var total = 0
	forEachGraphemeClusterRange { start, end ->
		total += graphemeClusterWidth(start, end)
	}
	return total
}

/** Returns whether every code unit is printable ASCII, which is always one column per unit. */
private fun CharSequence.isPrintableAscii(): Boolean {
	for (i in indices) {
		val code = this[i].code
		if (code !in 0x20..0x7E) return false
	}
	return true
}

/**
 * Returns the width in columns of the cluster spanning `[from, to)` of this text.
 *
 * This is the single rule both measurement and drawing use, so a synthesized cluster (a ZWJ
 * emoji family, keycap, flag, or Hangul jamo run) is always measured and drawn the same way.
 */
internal fun CharSequence.graphemeClusterWidth(from: Int, to: Int): Int {
	// Control characters are GCB_CONTROL: they always form their own single-codepoint cluster and
	// are non-printing, so such a cluster occupies zero columns regardless of what the width tables
	// report. Measurement and drawing both consult this rule, so a control is never laid out as a
	// column nor written to a cell.
	if (isControl(codepointAt(from))) return 0
	val width = strWidth(this, from, to)
	if (width == 1 && charWidth(codepointAt(from)) == 2) return 2
	return width
}
