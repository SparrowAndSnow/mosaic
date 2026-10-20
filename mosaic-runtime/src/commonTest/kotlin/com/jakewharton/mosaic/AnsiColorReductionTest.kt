package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test

/**
 * Locks the RGB -> basic (16) / extended (256) color reduction semantics.
 *
 * Basic colors: the brightest channel selects the regular or bright set (a highest channel at
 * roughly 100% adds 60 to the code), and each channel across half intensity flips the corresponding
 * bit of the 4-bit base index. Extended colors: grays (equal channels) land on the 24-step gray
 * ramp (with near-black/near-white clamped to 16/231), everything else on the 6x6x6 cube by
 * rounding `channel / 255 * 5`.
 */
class AnsiColorReductionTest {
	private class Rgb(val red: Int, val green: Int, val blue: Int)

	private fun assertAnsi16(rgb: Rgb, expected: Int) {
		val code = Color(rgb.red, rgb.green, rgb.blue).toAnsi16Code()
		assertThat(code, "toAnsi16(${rgb.red}, ${rgb.green}, ${rgb.blue})").isEqualTo(expected)
	}

	private fun assertAnsi256(rgb: Rgb, expected: Int) {
		val code = Color(rgb.red, rgb.green, rgb.blue).toAnsi256Code()
		assertThat(code, "toAnsi256(${rgb.red}, ${rgb.green}, ${rgb.blue})").isEqualTo(expected)
	}

	@Test fun ansi16Code() {
		// Black, white and the regular/bright boundary.
		assertAnsi16(Rgb(0, 0, 0), 30)
		assertAnsi16(Rgb(255, 255, 255), 97)
		assertAnsi16(Rgb(253, 0, 0), 31)
		assertAnsi16(Rgb(254, 0, 0), 91)

		// A channel crossing half intensity flips its bit: gray 127 -> black, 128 -> white.
		assertAnsi16(Rgb(127, 127, 127), 30)
		assertAnsi16(Rgb(128, 128, 128), 37)
		assertAnsi16(Rgb(127, 0, 0), 30)
		assertAnsi16(Rgb(128, 0, 0), 31)

		// The bright variant (code + 60) for each pure primary and secondary.
		assertAnsi16(Rgb(0, 255, 0), 92)
		assertAnsi16(Rgb(0, 0, 255), 94)
		assertAnsi16(Rgb(255, 0, 255), 95)
		assertAnsi16(Rgb(0, 255, 255), 96)
		assertAnsi16(Rgb(255, 255, 0), 93)

		// Two-bit combinations of the base index.
		assertAnsi16(Rgb(200, 200, 0), 33)
		assertAnsi16(Rgb(0, 200, 200), 36)
		assertAnsi16(Rgb(200, 0, 200), 35)
		assertAnsi16(Rgb(205, 133, 63), 33)
	}

	@Test fun ansi16CodeMaxChannelOfThirty() {
		// The highest channel at 30% (a color like dark gray) maps to black; this also pins the
		// dedicated early-return for value == 30.
		assertAnsi16(Rgb(77, 77, 77), 30)
		assertAnsi16(Rgb(77, 0, 0), 30)
		assertAnsi16(Rgb(76, 0, 0), 30)
	}

	@Test fun ansi256GrayscaleSegmentation() {
		// Below 8 -> palette index 16 (near black), above 248 -> 231 (near white).
		assertAnsi256(Rgb(0, 0, 0), 16)
		assertAnsi256(Rgb(7, 7, 7), 16)
		assertAnsi256(Rgb(249, 249, 249), 231)
		assertAnsi256(Rgb(255, 255, 255), 231)

		// The 24-step gray ramp, including its endpoints.
		assertAnsi256(Rgb(8, 8, 8), 232)
		assertAnsi256(Rgb(247, 247, 247), 255)
		assertAnsi256(Rgb(248, 248, 248), 255)

		// Interior ramp samples.
		assertAnsi256(Rgb(23, 23, 23), 233)
		assertAnsi256(Rgb(24, 24, 24), 234)
		assertAnsi256(Rgb(77, 77, 77), 239)
		assertAnsi256(Rgb(90, 90, 90), 240)
		assertAnsi256(Rgb(128, 128, 128), 244)
		assertAnsi256(Rgb(150, 150, 150), 246)
		assertAnsi256(Rgb(180, 180, 180), 249)
		assertAnsi256(Rgb(230, 230, 230), 254)
		assertAnsi256(Rgb(240, 240, 240), 255)
	}

	@Test fun ansi256ColorCube() {
		// Pure primaries and secondaries.
		assertAnsi256(Rgb(255, 0, 0), 196)
		assertAnsi256(Rgb(0, 255, 0), 46)
		assertAnsi256(Rgb(0, 0, 255), 21)
		assertAnsi256(Rgb(255, 0, 255), 201)
		assertAnsi256(Rgb(0, 255, 255), 51)
		assertAnsi256(Rgb(255, 255, 0), 226)

		// Cube-step rounding boundary on the first channel: 76 -> step 1, 77 -> step 2.
		assertAnsi256(Rgb(76, 0, 0), 52)
		assertAnsi256(Rgb(77, 0, 0), 88)
		assertAnsi256(Rgb(95, 0, 0), 88)
		assertAnsi256(Rgb(127, 0, 0), 88)
		assertAnsi256(Rgb(128, 0, 0), 124)
		assertAnsi256(Rgb(160, 0, 0), 124)
		assertAnsi256(Rgb(180, 0, 0), 160)
		assertAnsi256(Rgb(247, 0, 0), 196)

		// The same rounding threshold on the other channels.
		assertAnsi256(Rgb(0, 0, 127), 18)
		assertAnsi256(Rgb(0, 0, 128), 19)
		assertAnsi256(Rgb(0, 128, 128), 37)
		assertAnsi256(Rgb(128, 0, 128), 127)

		// Two components at half the cube width keep their own per-channel steps.
		assertAnsi256(Rgb(95, 95, 0), 100)
		assertAnsi256(Rgb(95, 0, 95), 90)
		assertAnsi256(Rgb(0, 95, 95), 30)
		assertAnsi256(Rgb(95, 255, 95), 120)
		assertAnsi256(Rgb(95, 95, 255), 105)
		assertAnsi256(Rgb(255, 95, 95), 210)

		// Misc two-bit and mixed colors.
		assertAnsi256(Rgb(200, 200, 0), 184)
		assertAnsi256(Rgb(0, 200, 200), 44)
		assertAnsi256(Rgb(200, 0, 200), 164)
		assertAnsi256(Rgb(205, 133, 63), 179)
		assertAnsi256(Rgb(128, 0, 0), 124)
	}
}
