package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

class TtyTerminalMouseTest {
	@Test fun mouseEnabledWhenOptedIn() = terminalTest {
		// DA1 reply only; the remaining capability queries time out (see TtyTerminalCursorTest).
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal(mouse = true) { setup ->
			// SGR (1006) + press/release (1000) + all-motion (1003) reporting during setup.
			assertThat(setup).contains("$CSI?1006h$CSI?1000h$CSI?1003h")
		}

		// Both modes are disabled again on teardown.
		assertThat(teardown).contains("$CSI?1006l$CSI?1000l$CSI?1003l")
	}

	@Test fun mouseDisabledByDefault() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("$CSI?1006h")
		}

		assertThat(teardown).doesNotContain("$CSI?1006l")
	}
}
