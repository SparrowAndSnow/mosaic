package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent
import de.cketti.codepoints.appendCodePoint

internal fun KeyboardEvent.toKeyEventOrNull(): KeyEvent? {
	// Both press and repeat are delivered: repeat restores holding-a-key auto-repeat parity with
	// terminals that stream repeated bytes in the legacy raw path. Release is dropped so the
	// runtime stays press-only.
	if (eventType != KeyboardEvent.EventTypePress && eventType != KeyboardEvent.EventTypeRepeat) {
		return null
	}

	val key = text ?: when (val codepoint = codepoint) {
		9 -> "Tab"
		13 -> "Enter"
		27 -> "Escape"
		in 32..126 -> codepoint.toChar().toString()
		127 -> "Backspace"
		57350 -> "ArrowLeft"
		57351 -> "ArrowRight"
		57352 -> "ArrowUp"
		57353 -> "ArrowDown"
		57348 -> "Insert"
		57349 -> "Delete"
		57354 -> "PageUp"
		57355 -> "PageDown"
		57356 -> "Home"
		57357 -> "End"
		in 57364..57398 -> "F" + (codepoint - 57363)
		else -> buildString { appendCodePoint(codepoint) }
	}

	return KeyEvent(
		key = key,
		alt = alt,
		ctrl = ctrl,
		shift = shift,
		repeat = eventType == KeyboardEvent.EventTypeRepeat,
	)
}
