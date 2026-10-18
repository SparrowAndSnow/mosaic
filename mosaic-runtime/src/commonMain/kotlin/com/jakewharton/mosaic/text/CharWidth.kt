package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.text.unicodewidth.unicodeWidth

/**
 * C0 (0x00-0x1F), DEL (0x7F) and C1 (0x80-0x9F) control characters are non-printing. They occupy
 * zero columns and must never be emitted into the ANSI stream: a terminal interprets them as
 * cursor motions (CR/BS/TAB) or protocol escapes, which would corrupt frame layout.
 */
internal fun isControl(codepoint: Int): Boolean = codepoint in 0x00..0x1F || codepoint in 0x7F..0x9F

/**
 * Emoji blocks that the Unicode East Asian Width data lists as `N`/`A` (width 1) but that virtually
 * all modern terminals render as 2-cell emoji.
 */
private val emojiOverrideRanges = listOf(
	0x1F000..0x1F02F, // Mahjong Tiles
	0x1F0A0..0x1F0FF, // Playing Cards
	0x1F300..0x1F5FF, // Miscellaneous Symbols and Pictographs
	0x1F600..0x1F64F, // Emoticons
	0x1F680..0x1F6FF, // Transport and Map Symbols
	0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs
	0x1FA00..0x1FA6F, // Chess Symbols + Xiangqi
	0x1FA70..0x1FAFF, // Symbols and Pictographs Extended-A
)

private operator fun List<IntRange>.contains(codepoint: Int): Boolean {
	var low = 0
	var high = size - 1
	while (low <= high) {
		val mid = (low + high) ushr 1
		val range = get(mid)
		if (codepoint < range.first) {
			high = mid - 1
		} else if (codepoint > range.last) {
			low = mid + 1
		} else {
			return true
		}
	}
	return false
}

/**
 * Returns the number of terminal columns a Unicode code point occupies.
 *
 * Delegates to the bundled UAX #11 width tables (see `text/unicodewidth/`) so width data always
 * tracks the Unicode standard. Control characters ([isControl]) are non-printing and occupy zero
 * columns; they are filtered out before anything is drawn or emitted.
 */
internal fun charWidth(codepoint: Int): Int {
	if (isControl(codepoint)) return 0
	val width = codepoint.unicodeWidth() ?: 1
	if (width == 1 && codepoint in emojiOverrideRanges) return 2
	return width
}
