package com.jakewharton.mosaic

import com.jakewharton.mosaic.ui.Color
import kotlin.math.roundToInt

internal const val ESC = "\u001B"
internal const val CSI = "$ESC["

internal const val synchronizedOutputMode = 2026
internal const val synchronizedOutputEnable = "$CSI?${synchronizedOutputMode}h"
internal const val synchronizedOutputDisable = "$CSI?${synchronizedOutputMode}l"

internal const val cursorMode = 25
internal const val hideCursor = "$CSI?${cursorMode}l"
internal const val showCursor = "$CSI?${cursorMode}h"

internal const val ansiReset = "${CSI}0"
internal const val clearLine = "${CSI}K"
/** Erase the whole display (ED with parameter 2) — `ESC[J` alone only clears below the cursor. */
internal const val clearDisplay = "${CSI}2J"

internal const val ansiSeparator = ";"
internal const val ansiClosingCharacter = "m"

internal const val ansiFgColorSelector = 38
internal const val ansiFgColorReset = 39
internal const val ansiFgColorOffset = 0

internal const val ansiBgColorSelector = 48
internal const val ansiBgColorReset = 49
internal const val ansiBgColorOffset = 10

internal const val ansiSelectorColor256 = "5"
internal const val ansiSelectorColorRgb = "2"

internal const val ansiUnderlineColorSelector = 58
internal const val ansiUnderlineColorReset = 59
internal const val ansiUnderlineColorOffset = 0

/**
 * Maps this color to the base 16-color foreground code (30..37 plus optional 60 for the bright
 * variant), the scheme used when the terminal only supports the basic colors.
 *
 * Brightness is derived from the channel having the highest value: a highest channel of roughly
 * 100% selects the bright set (code + 60), anything below selects the regular set. The low bit of
 * each bit position is set when the corresponding channel rounds to at least half intensity, so a
 * color is "white" once its brighter channels cross half intensity. Black is returned for anything
 * whose channels are all below half intensity.
 */
internal fun Color.toAnsi16Code(): Int {
	val value = (maxOf(redFloat, greenFloat, blueFloat) * 100).roundToInt()
	if (value == 30) {
		return 30
	}
	val v = value / 50
	val ansiCode = 30 + (
		(blueFloat.roundToInt() * 4)
			or (greenFloat.roundToInt() * 2)
			or redFloat.roundToInt()
		)
	return if (v == 2) ansiCode + 60 else ansiCode
}

/**
 * Maps this color to a 256-color palette index (0..255), used when the terminal supports the
 * extended palette. Grays (equal channels) map onto the 24-step gray ramp 232..255 (with the
 * near-black/near-white ends clamped to 16/231); all other colors map onto the 6x6x6 color cube by
 * rounding each `channel / 255 * 5` to a cube step (index 16 + 36*r + 6*g + b).
 */
internal fun Color.toAnsi256Code(): Int {
	val ri = redInt
	val gi = greenInt
	val bi = blueInt
	// grayscale
	return if (ri == gi && gi == bi) {
		when {
			ri < 8 -> 16
			ri > 248 -> 231
			else -> (((ri - 8) / 247.0) * 24.0).roundToInt() + 232
		}
	} else {
		16 + (36 * (redFloat * 5).roundToInt()) +
			(6 * (greenFloat * 5).roundToInt()) +
			(blueFloat * 5).roundToInt()
	}
}
