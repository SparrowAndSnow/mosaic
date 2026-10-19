package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.Delete
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.End
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.F1
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.Home
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.Insert
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.ModifierNumLock
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.ModifierShift
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.PageDown
import com.jakewharton.mosaic.terminal.KeyboardEvent.Companion.PageUp
import kotlin.test.Test

/**
 * Legacy `CSI <key>~` key events can carry the keyboard-protocol modifiers + event-type payload
 * (`CSI <key>;<modifiers>[:<event-type>]~`). Decoding the payload is what keeps a key-up from
 * arriving as a second fresh press for keys such as PgUp/PgDn/F1-F12.
 */
class EventParserCsiTildeKeyboardEventTest : BaseEventParserTest() {
	@Test fun plainPageUp() {
		testTerminal.write("${CSI}5~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(PageUp))
	}

	@Test fun plainPageDown() {
		testTerminal.write("${CSI}6~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(PageDown))
	}

	@Test fun plainInsertDelete() {
		testTerminal.write("${CSI}2~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(Insert))
		testTerminal.write("${CSI}3~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(Delete))
	}

	@Test fun tildeHomeEnd() {
		testTerminal.write("${CSI}7~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(Home))
		testTerminal.write("${CSI}8~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(End))
	}

	@Test fun pageDownPressKeepsModifiers() {
		testTerminal.write("${CSI}6;129:1~")
		assertThat(parser.next()).isEqualTo(
			KeyboardEvent(PageDown, modifiers = ModifierNumLock, eventType = KeyboardEvent.EventTypePress),
		)
	}

	@Test fun pageDownReleaseIsEventType3() {
		// The release of a Fn-layered PgDn; previously decoded as a fresh press.
		testTerminal.write("${CSI}6;129:3~")
		assertThat(parser.next()).isEqualTo(
			KeyboardEvent(PageDown, modifiers = ModifierNumLock, eventType = KeyboardEvent.EventTypeRelease),
		)
	}

	@Test fun pageUpReleaseIsEventType3() {
		testTerminal.write("${CSI}5;129:3~")
		assertThat(parser.next()).isEqualTo(
			KeyboardEvent(PageUp, modifiers = ModifierNumLock, eventType = KeyboardEvent.EventTypeRelease),
		)
	}

	@Test fun pageUpRepeatIsEventType2() {
		testTerminal.write("${CSI}5;129:2~")
		assertThat(parser.next()).isEqualTo(
			KeyboardEvent(PageUp, modifiers = ModifierNumLock, eventType = KeyboardEvent.EventTypeRepeat),
		)
	}

	@Test fun shiftF1() {
		testTerminal.write("${CSI}11;2~")
		assertThat(parser.next()).isEqualTo(KeyboardEvent(F1, modifiers = ModifierShift))
	}
}
