package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

class TtyTerminalPasteTest {
	@Test fun bracketedPasteEnabledWhenOptedIn() = terminalTest {
		// DA1 reply only; the remaining capability queries time out (see TtyTerminalCursorTest).
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal(paste = true) { setup ->
			// Bracketed paste mode (2004) enabled during setup.
			assertThat(setup).contains("$CSI?2004h")
		}

		// Disabled again on teardown.
		assertThat(teardown).contains("$CSI?2004l")
	}

	@Test fun bracketedPasteDisabledByDefault() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("$CSI?2004h")
		}

		assertThat(teardown).doesNotContain("$CSI?2004l")
	}
}
