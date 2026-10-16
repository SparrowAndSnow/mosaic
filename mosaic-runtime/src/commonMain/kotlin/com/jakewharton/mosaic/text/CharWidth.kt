package com.jakewharton.mosaic.text

import io.github.kotlinmania.unicodewidth.unicodeWidth

/**
 * Emoji blocks that the Unicode East Asian Width data lists as `N`/`A` (width 1) but that virtually
 * all modern terminals render as 2-cell emoji. Mirrors the override list used by `mordant`.
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
 * Delegates to `unicode-width-kotlin` (a Kotlin port of `unicode-rs/unicode-width`, itself a strict
 * UAX#11 implementation) so width data always tracks the Unicode standard. Control characters, for
 * which the backing implementation reports no width, are treated as 1 column.
 */
internal fun charWidth(codepoint: Int): Int {
	val width = codepoint.unicodeWidth() ?: 1
	if (width == 1 && codepoint in emojiOverrideRanges) return 2
	return width
}
