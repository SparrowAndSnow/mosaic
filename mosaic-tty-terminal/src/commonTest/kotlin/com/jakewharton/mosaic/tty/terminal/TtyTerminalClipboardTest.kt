package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import com.jakewharton.mosaic.terminal.ClipboardEvent
import kotlin.test.Test

class TtyTerminalClipboardTest {
	@Test fun readAndWriteEmitOsc52() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal {
			requestClipboardRead()
			writeClipboard("hello")
			writeClipboard("", ClipboardEvent.Selection.Primary)
		}

		// Read request targets the clipboard with a "?" payload.
		assertThat(teardown).contains("${OSC}52;c;?$ST")
		// Writes base64-pack the text.
		assertThat(teardown).contains("${OSC}52;c;aGVsbG8=$ST")
		// Empty text clears, and the primary selection is honored.
		assertThat(teardown).contains("${OSC}52;p;$ST")
	}
}
