package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.terminal.Event
import com.jakewharton.mosaic.terminal.KeyboardEvent
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout

class TtyTerminalBackpressureTest {
	@Test fun largeBurstIsNotDropped() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		withTerminal {
			// A burst far larger than the 64-event channel buffer. The producer must backpressure
			// rather than drop events (a paste or mouse storm loses nothing this way).
			testTerminal.write("a".repeat(500))

			val keyEvents = mutableListOf<Event>()
			withTimeout(5.seconds) {
				while (keyEvents.count { it is KeyboardEvent } < 500) {
					keyEvents += this@withTerminal.events.receive()
				}
			}

			assertThat(keyEvents.count { it is KeyboardEvent }).isEqualTo(500)
		}
	}
}
