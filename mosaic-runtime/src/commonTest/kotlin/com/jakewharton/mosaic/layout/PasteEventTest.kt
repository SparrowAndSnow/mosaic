package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PasteEventTest {

	private class Recorder {
		val pastes = mutableListOf<String>()
		val keys = mutableListOf<KeyEvent>()
		val consume = mutableMapOf<String, Boolean>()

		fun pasteHandler(name: String): (String) -> Boolean = { text ->
			pastes += "$name:$text"
			consume[name] ?: true
		}
	}

	@Test fun pasteGoesToRootHandler() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onPaste(recorder.pasteHandler("root")))
			}
			val root = awaitSnapshot()

			assertThat(root.sendPasteEvent("hello")).isTrue()
			assertThat(recorder.pastes).isEqualTo(listOf("root:hello"))
		}
	}

	@Test fun ancestorConsumesBeforeChild() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onPaste(recorder.pasteHandler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onPaste(recorder.pasteHandler("a")))
					}
				}
			}
			val root = awaitSnapshot()

			val handled = root.sendPasteEvent("hi")

			assertThat(handled).isTrue()
			assertThat(recorder.pastes).isEqualTo(listOf("root:hi"))
		}
	}

	@Test fun unhandledByParentReachesChild() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder().apply { consume["root"] = false }
			setContent {
				Box(Modifier.width(10).height(8).onPaste(recorder.pasteHandler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onPaste(recorder.pasteHandler("a")))
					}
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendPasteEvent("hi")).isTrue()
			assertThat(recorder.pastes).isEqualTo(listOf("root:hi", "a:hi"))
		}
	}

	@Test fun unhandledPasteReturnsFalse() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder().apply {
				consume["root"] = false
				consume["a"] = false
			}
			setContent {
				Box(Modifier.width(10).height(8).onPaste(recorder.pasteHandler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onPaste(recorder.pasteHandler("a")))
					}
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendPasteEvent("hi")).isFalse()
			assertThat(recorder.pastes).isEqualTo(listOf("root:hi", "a:hi"))
		}
	}

	@Test fun pasteContentReachesHandlerAsSingleString() = runTest {
		runMosaicTest {
			var last by mutableStateOf("none")

			setContent {
				Box(
					Modifier.onPaste { text ->
						last = text
						true
					},
				) {
					Text(last)
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("none")

			// Multi-code-point content (CJK + astral plane) must arrive intact when pasting stops.
			sendPaste("中文ab\uD83D\uDE00!")

			assertThat(awaitSnapshot()).isEqualTo("中文ab\uD83D\uDE00!")
		}
	}

	@Test fun pasteContentIsNotDispatchedAsKeyEvents() = runTest {
		runMosaicTest {
			val keys = mutableListOf<KeyEvent>()
			var last by mutableStateOf("none")

			setContent {
				Box(
					Modifier
						.onPaste { text ->
							last = text
							true
						}
						.onKeyEvent {
							keys += it
							true
						},
				) {
					Text(last)
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("none")

			// The individual characters of a paste must reach onPaste as a single string, not
			// as key events.
			sendPaste("paste-me")

			assertThat(awaitSnapshot()).isEqualTo("paste-me")
			assertThat(keys).isEmpty()
		}
	}
}
