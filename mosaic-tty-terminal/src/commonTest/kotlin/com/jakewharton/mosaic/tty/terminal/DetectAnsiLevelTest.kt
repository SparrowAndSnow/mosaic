package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.terminal.AnsiLevel
import kotlin.test.Test

/**
 * Locks in the environment-based color detection, in particular the Windows fallback: entering raw
 * mode enables VT output processing, so a plain console window renders 24-bit color even though it
 * does not set `WT_SESSION` the way Windows Terminal does.
 */
class DetectAnsiLevelTest {
	private fun level(env: Map<String, String>, isWindows: Boolean = false) = detectAnsiLevel({ env[it] }, isWindows)

	@Test fun noColorDisablesColorEverywhere() {
		assertThat(level(mapOf("NO_COLOR" to "1"), isWindows = true)).isEqualTo(AnsiLevel.NONE)
	}

	@Test fun emptyNoColorDoesNotDisableColor() {
		assertThat(level(mapOf("NO_COLOR" to ""), isWindows = true)).isEqualTo(AnsiLevel.TRUECOLOR)
	}

	@Test fun colortermTruecolorWins() {
		assertThat(level(mapOf("COLORTERM" to "truecolor"))).isEqualTo(AnsiLevel.TRUECOLOR)
	}

	@Test fun colorterm24BitWins() {
		assertThat(level(mapOf("COLORTERM" to "24bit"))).isEqualTo(AnsiLevel.TRUECOLOR)
	}

	@Test fun term256IsAnsi256() {
		assertThat(level(mapOf("TERM" to "xterm-256color"))).isEqualTo(AnsiLevel.ANSI256)
	}

	@Test fun plainTermIsAnsi16() {
		assertThat(level(mapOf("TERM" to "xterm"))).isEqualTo(AnsiLevel.ANSI16)
	}

	@Test fun windowsWithoutTermIsTruecolor() {
		// The Windows console is 24-bit capable once raw mode enabled VT output processing, whether
		// or not it was launched from Windows Terminal.
		assertThat(level(emptyMap(), isWindows = true)).isEqualTo(AnsiLevel.TRUECOLOR)
	}

	@Test fun nonWindowsWithoutTermIsNoColor() {
		assertThat(level(emptyMap(), isWindows = false)).isEqualTo(AnsiLevel.NONE)
	}

	@Test fun windowsStillHonorsDumbTermWithoutNoColor() {
		// An explicit but non-color TERM string ("dumb") falls through to the platform default,
		// which is 24-bit color on Windows.
		assertThat(level(mapOf("TERM" to "dumb"), isWindows = true)).isEqualTo(AnsiLevel.TRUECOLOR)
	}
}
