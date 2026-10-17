package com.jakewharton.mosaic.ui.platform

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ClipboardTest {
	@Test fun getTextReceivesInjectedReply() = runTest {
		runMosaicTest {
			var last by mutableStateOf("none")

			setContent {
				Box {
					val clipboard = LocalClipboard.current
					LaunchedEffect(Unit) {
						last = clipboard.getText() ?: "null"
					}
					Text(last)
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("none")

			// The terminal replies asynchronously; it satisfies the pending read.
			sendClipboardReply("hello")

			assertThat(awaitSnapshot()).isEqualTo("hello")
		}
	}

	@Test fun deniedReplyYieldsNull() = runTest {
		runMosaicTest {
			var last by mutableStateOf("none")

			setContent {
				Box {
					val clipboard = LocalClipboard.current
					LaunchedEffect(Unit) {
						val text = clipboard.getText()
						last = if (text == null) "denied" else "got:$text"
					}
					Text(last)
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("none")
			sendClipboardReply(null)
			assertThat(awaitSnapshot()).isEqualTo("denied")
		}
	}

	@Test fun setTextWritesThroughTerminal() = runTest {
		val terminal = TestTerminal()
		val clipboard = TermClipboard(terminal)

		clipboard.setText("hello")

		assertThat(terminal.clipboardWrites).isEqualTo(
			listOf("hello" to ClipboardEvent.Selection.Clipboard),
		)
		assertThat(terminal.clipboardReadRequested).isNull()
	}
}
