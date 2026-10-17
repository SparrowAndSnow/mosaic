package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class TtyTerminalDec2027Test {
	@Test fun enabledWhenSupportedButReset() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Grapheme clustering is supported but currently reset.
		expect("$CSI?2027\$p", reply = "$CSI?2027;2\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.graphemeClustering).isTrue()
			// Enabled so clusters are kept whole on the terminal side.
			assertThat(setup).contains("$CSI?2027h")
		}

		// Restored on exit.
		assertThat(teardown).contains("$CSI?2027l")
	}

	@Test fun reportedWhenAlreadyEnabled() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?2027\$p", reply = "$CSI?2027;1\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.graphemeClustering).isTrue()
			// Already enabled: no need to send enable.
			assertThat(setup).doesNotContain("$CSI?2027h")
		}
		assertThat(teardown).doesNotContain("$CSI?2027l")
	}

	@Test fun reportedWhenPermanentlyEnabled() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?2027\$p", reply = "$CSI?2027;3\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.graphemeClustering).isTrue()
			assertThat(setup).doesNotContain("$CSI?2027h")
		}
		assertThat(teardown).doesNotContain("$CSI?2027l")
	}

	@Test fun unsupportedWhenNotRecognized() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?2027\$p", reply = "$CSI?2027;0\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.graphemeClustering).isFalse()
			assertThat(setup).doesNotContain("$CSI?2027h")
		}
		assertThat(teardown).doesNotContain("$CSI?2027l")
	}

	@Test fun unsupportedWhenPermanentlyReset() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?2027\$p", reply = "$CSI?2027;4\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.graphemeClustering).isFalse()
			assertThat(setup).doesNotContain("$CSI?2027h")
		}
		assertThat(teardown).doesNotContain("$CSI?2027l")
	}
}
