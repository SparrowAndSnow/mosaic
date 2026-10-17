package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.terminal.ClipboardEvent
import kotlin.test.Test

class EventParserOscClipboardEventTest : BaseEventParserTest() {
	@Test fun clipboardReplyDecodesBase64() {
		testTerminal.write("${OSC}52;c;aGVsbG8=$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Clipboard, "hello"),
		)
	}

	@Test fun clipboardReplyDecodesUtf8() {
		testTerminal.write("${OSC}52;c;5Lit5paH$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Clipboard, "中文"),
		)
	}

	@Test fun primarySelection() {
		testTerminal.write("${OSC}52;p;YSBiIQ==$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Primary, "a b!"),
		)
	}

	@Test fun emptyReplyIsEmptyString() {
		testTerminal.write("${OSC}52;c;$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Clipboard, ""),
		)
	}

	@Test fun deniedReplyIsNull() {
		testTerminal.write("${OSC}52;c;!$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Clipboard, null),
		)
	}

	@Test fun invalidBase64IsNull() {
		testTerminal.write("${OSC}52;c;\$#$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Clipboard, null),
		)
	}

	@Test fun unknownSelection() {
		testTerminal.write("${OSC}52;x;QUI=$ST")
		assertThat(parser.next()).isEqualTo(
			ClipboardEvent(ClipboardEvent.Selection.Unknown, "AB"),
		)
	}
}
