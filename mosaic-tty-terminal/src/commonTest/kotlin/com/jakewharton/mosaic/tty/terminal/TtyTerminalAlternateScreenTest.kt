package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.io.bytestring.encodeToByteString

class TtyTerminalAlternateScreenTest {
	@Test fun entersBeforeCapabilityProbesAndLeavesOnClose() = terminalTest {
		val teardown = withTerminal(alternateScreen = true) { setup ->
			// The first capability probe (DA1) must arrive only after the enter sequence.
			assertThat(setup).isEqualTo((alternateScreenEnable + "${CSI}0c").encodeToByteString())
		}

		assertThat(teardown).isEqualTo(alternateScreenDisable.encodeToByteString())
	}

	@Test fun leaveFollowsRestoredModesAndIsTheLastOutput() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?25\$p", reply = "$CSI?25;1\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal(alternateScreen = true) { setup ->
			assertThat(capabilities.cursorVisibility).isTrue()
			assertThat(setup).contains("$CSI?25l")
		}

		// Cursor visibility (and every other toggled mode) is restored before the leave sequence.
		assertThat(teardown).isEqualTo(("$CSI?25h" + alternateScreenDisable).encodeToByteString())
	}

	@Test fun disabledNeverTouchesAlternateScreen() = terminalTest {
		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("1049")
		}

		assertThat(teardown).doesNotContain("1049")
	}
}
