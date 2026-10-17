package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class TtyTerminalKittyKeyboardTest {
	@Test fun pushedWhenSupported() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Terminal reports kitty keyboard protocol support with all default flags (23).
		expect("$CSI?u", reply = "$CSI?23u")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.kittyKeyboard).isTrue()

			// The protocol is actively pushed on detection so keys are reported unambiguously.
			assertThat(setup).contains("$CSI>23u")
		}

		// Popped again on teardown to restore the terminal's prior configuration.
		assertThat(teardown).contains("$CSI<23u")
	}

	@Test fun notPushedWhenUnsupported() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.kittyKeyboard).isFalse()

			// No push when the terminal never reported keyboard protocol support.
			assertThat(setup).doesNotContain("$CSI>23u")
		}

		assertThat(teardown).doesNotContain("$CSI<23u")
	}
}
