package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.ModifierAlt
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.ModifierShift
import kotlin.test.Test

class CompatTest {

	private fun press(codepoint: Int, modifiers: Int = 0, text: String? = null) = KeyboardEvent(codepoint, modifiers = modifiers, text = text)

	@Test fun legacyControlKeysAreNamed() {
		assertThat(press(9).toKeyEventOrNull()).isEqualTo(KeyEvent("Tab"))
		assertThat(press(13).toKeyEventOrNull()).isEqualTo(KeyEvent("Enter"))
		assertThat(press(27).toKeyEventOrNull()).isEqualTo(KeyEvent("Escape"))
		assertThat(press(127).toKeyEventOrNull()).isEqualTo(KeyEvent("Backspace"))
	}

	@Test fun legacyPrintableKeysUseCodepoint() {
		assertThat(press('a'.code).toKeyEventOrNull()).isEqualTo(KeyEvent("a"))
		assertThat(press('中'.code).toKeyEventOrNull()).isEqualTo(KeyEvent("中"))
	}

	@Test fun specialKeysAreMappedFromPrivateSpace() {
		assertThat(press(KeyboardEvent.Left).toKeyEventOrNull()).isEqualTo(KeyEvent("ArrowLeft"))
		assertThat(press(KeyboardEvent.Right).toKeyEventOrNull()).isEqualTo(KeyEvent("ArrowRight"))
		assertThat(press(KeyboardEvent.Up).toKeyEventOrNull()).isEqualTo(KeyEvent("ArrowUp"))
		assertThat(press(KeyboardEvent.Down).toKeyEventOrNull()).isEqualTo(KeyEvent("ArrowDown"))
		assertThat(press(KeyboardEvent.Insert).toKeyEventOrNull()).isEqualTo(KeyEvent("Insert"))
		assertThat(press(KeyboardEvent.Delete).toKeyEventOrNull()).isEqualTo(KeyEvent("Delete"))
		assertThat(press(KeyboardEvent.PageUp).toKeyEventOrNull()).isEqualTo(KeyEvent("PageUp"))
		assertThat(press(KeyboardEvent.PageDown).toKeyEventOrNull()).isEqualTo(KeyEvent("PageDown"))
		assertThat(press(KeyboardEvent.Home).toKeyEventOrNull()).isEqualTo(KeyEvent("Home"))
		assertThat(press(KeyboardEvent.End).toKeyEventOrNull()).isEqualTo(KeyEvent("End"))
		assertThat(press(KeyboardEvent.F1).toKeyEventOrNull()).isEqualTo(KeyEvent("F1"))
		assertThat(press(KeyboardEvent.F12).toKeyEventOrNull()).isEqualTo(KeyEvent("F12"))
		assertThat(press(57380).toKeyEventOrNull()).isEqualTo(KeyEvent("F17"))
	}

	@Test fun repeatIsDeliveredFlaggedAndPressIsNot() {
		assertThat(
			KeyboardEvent(KeyboardEvent.Left, eventType = KeyboardEvent.EventTypeRepeat)
				.toKeyEventOrNull(),
		).isEqualTo(KeyEvent("ArrowLeft", repeat = true))
		assertThat(press(KeyboardEvent.Down).toKeyEventOrNull())
			.isEqualTo(KeyEvent("ArrowDown", repeat = false))
	}

	@Test fun releaseIsDropped() {
		assertThat(
			KeyboardEvent(97, eventType = KeyboardEvent.EventTypeRelease)
				.toKeyEventOrNull(),
		).isNull()
	}

	@Test fun associatedTextIsPreferredOverCodepoint() {
		assertThat(press('a'.code, text = "a").toKeyEventOrNull()).isEqualTo(KeyEvent("a"))
		assertThat(press('中'.code, text = "中").toKeyEventOrNull()).isEqualTo(KeyEvent("中"))
	}

	@Test fun modifiersArePassedThrough() {
		assertThat(
			press('a'.code, ModifierAlt or ModifierCtrl or ModifierShift).toKeyEventOrNull(),
		).isEqualTo(KeyEvent("a", alt = true, ctrl = true, shift = true))
	}

	@Test fun unknownCodepointFallsBackToUnicodeString() {
		assertThat(press(0x2000).toKeyEventOrNull()).isEqualTo(KeyEvent("\u2000"))
	}
}
