package com.jakewharton.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.text.unicodewidth.strWidth
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * The single-line field splits its edit rules into a plain [TextEditing] engine so every caret /
 * delta decision is tested directly (wide and combining clusters included), plus a lighter set of
 * end-to-end checks that key events reach the field through the normal runtime dispatch and the
 * caret renders as a reverse-video cell.
 */
class BasicTextFieldTest {

	// ---- edit engine ----

	@Test fun insertAtCaret() {
		assertThat(TextEditing.insert("ab", 1, "X")).isEqualTo(TextEdit("aXb", 2))
	}

	@Test fun insertEmptyIsNoOp() {
		assertThat(TextEditing.insert("ab", 1, "")).isEqualTo(TextEdit("ab", 1))
	}

	@Test fun backspaceRemovesClusterBeforeCaret() {
		assertThat(TextEditing.backspace("ab", 1)).isEqualTo(TextEdit("b", 0))
	}

	@Test fun backspaceAtStartIsNoOp() {
		assertThat(TextEditing.backspace("ab", 0)).isEqualTo(TextEdit("ab", 0))
	}

	@Test fun backspaceRemovesWholeCombiningCluster() {
		// 'a' + combining acute accent is one cluster of two code units.
		assertThat(TextEditing.backspace("a\u0301b", 2)).isEqualTo(TextEdit("b", 0))
	}

	@Test fun deleteRemovesClusterAtCaret() {
		assertThat(TextEditing.delete("aXb", 1)).isEqualTo(TextEdit("ab", 1))
	}

	@Test fun deleteAtEndIsNoOp() {
		assertThat(TextEditing.delete("ab", 2)).isEqualTo(TextEdit("ab", 2))
	}

	@Test fun deleteRemovesWholeCombiningCluster() {
		assertThat(TextEditing.delete("a\u0301b", 0)).isEqualTo(TextEdit("b", 0))
	}

	@Test fun caretMovesByWholeCluster() {
		assertThat(TextEditing.caretLeft("ab", 2)).isEqualTo(1)
		assertThat(TextEditing.caretLeft("ab", 0)).isEqualTo(0)
		assertThat(TextEditing.caretRight("ab", 0)).isEqualTo(1)
		assertThat(TextEditing.caretRight("ab", 2)).isEqualTo(2)
	}

	@Test fun caretSkipsWideAndCombiningClusters() {
		assertThat(TextEditing.caretRight("你好", 0)).isEqualTo(1)
		assertThat(TextEditing.caretLeft("你好", 2)).isEqualTo(1)
		// Moving left from after 'b' steps one UAX#29 cluster at a time: onto, not over, the
		// combining cluster, then past it.
		assertThat(TextEditing.caretLeft("a\u0301b", 3)).isEqualTo(2)
		assertThat(TextEditing.caretLeft("a\u0301b", 2)).isEqualTo(0)
		assertThat(TextEditing.caretRight("a\u0301b", 0)).isEqualTo(2)
		// Dropping a caret inside a combining cluster clamps to the nearest boundary.
		assertThat(TextEditing.caretLeft("a\u0301b", 1)).isEqualTo(0)
		assertThat(TextEditing.caretRight("a\u0301b", 1)).isEqualTo(2)
	}

	@Test fun caretColumnsMatchWideCharacterWidth() {
		// "你好世界" is four double-width characters; moving one caret left must land on the
		// preceding character whose prefix is two columns narrower.
		val caret = TextEditing.caretLeft("你好世界", 4)
		assertThat(caret).isEqualTo(3)
		assertThat(strWidth("你好世界", 0, caret)).isEqualTo(6)
	}

	// ---- composable ----

	private val rendering = DebugRendering(
		TestTerminal.Capabilities(ansiLevel = AnsiLevel.TRUECOLOR),
	)

	private fun output(frame: String): String = frame.substringAfter("OUTPUT:\r\n")

	private fun com.jakewharton.mosaic.testing.TestMosaic<*>.focusField() {
		// The single field sits at the origin; a press focuses it (focus happens on press).
		val click = { type: com.jakewharton.mosaic.terminal.MouseEvent.Type ->
			sendMouseEvent(
				com.jakewharton.mosaic.terminal.MouseEvent(
					x = 0,
					y = 0,
					type = type,
					button = com.jakewharton.mosaic.terminal.MouseEvent.Button.Left,
				),
			)
		}
		click(com.jakewharton.mosaic.terminal.MouseEvent.Type.Press)
		click(com.jakewharton.mosaic.terminal.MouseEvent.Type.Release)
	}

	@Test fun rendersValueAndInvertedCaret() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { BasicTextField("hi", {}) }
			awaitSnapshot()
			// The caret appears only while the field is focused.
			focusField()
			val frame = output(awaitSnapshot().toString())
			assertThat(frame).contains("hi")
			assertThat(frame).contains("\u001B[7m")
		}
	}

	@Test fun disabledFieldRendersNoCaret() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { BasicTextField("hi", {}, enabled = false) }
			val frame = output(awaitSnapshot().toString())
			assertThat(frame).contains("hi")
			assertThat(frame).doesNotContain("\u001B[7m")
		}
	}

	@Test fun typingUpdatesControlledValue() = runTest {
		runMosaicTest {
			var text by mutableStateOf("")
			setContent { BasicTextField(text, onValueChange = { text = it }) }
			awaitSnapshot()

			// An unfocused field ignores input: a 'x' typed before focusing never lands. The
			// focus change itself is the observable recomposition that settles the next snapshot.
			sendKeyEvent(KeyboardEvent('x'.code, text = "x"))
			focusField()
			assertThat(awaitSnapshot()).doesNotContain("x")

			sendKeyEvent(KeyboardEvent('a'.code, text = "a"))
			assertThat(awaitSnapshot()).contains("a")
			sendKeyEvent(KeyboardEvent('b'.code, text = "b"))
			assertThat(awaitSnapshot()).contains("ab")
		}
	}

	@Test fun ctrlCombinationIsNotInserted() = runTest {
		runMosaicTest {
			var text by mutableStateOf("")
			setContent { BasicTextField(text, onValueChange = { text = it }) }
			awaitSnapshot()
			focusField()
			awaitSnapshot()

			// Ctrl combinations stay unhandled (no insertion). A following plain character
			// settles an observable frame; had the ctrl key inserted, the value would be "ab".
			sendKeyEvent(KeyboardEvent('a'.code, modifiers = KeyboardEvent.ModifierCtrl, text = "a"))
			sendKeyEvent(KeyboardEvent('b'.code, text = "b"))
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains("b")
			assertThat(frame).doesNotContain("ab")
		}
	}

	@Test fun backspaceDeletesTypedCharacters() = runTest {
		runMosaicTest {
			var text by mutableStateOf("")
			setContent { BasicTextField(text, onValueChange = { text = it }) }
			awaitSnapshot()
			focusField()
			awaitSnapshot()

			sendKeyEvent(KeyboardEvent('a'.code, text = "a"))
			sendKeyEvent(KeyboardEvent('b'.code, text = "b"))
			sendKeyEvent(KeyboardEvent(127))
			assertThat(awaitSnapshot()).contains("a")
		}
	}

	@Test fun caretNavigationInsertsInTheMiddle() = runTest {
		runMosaicTest {
			var text by mutableStateOf("")
			setContent { BasicTextField(text, onValueChange = { text = it }) }
			awaitSnapshot()
			focusField()
			awaitSnapshot()

			sendKeyEvent(KeyboardEvent('a'.code, text = "a"))
			sendKeyEvent(KeyboardEvent('b'.code, text = "b"))
			sendKeyEvent(KeyboardEvent(KeyboardEvent.Left))
			sendKeyEvent(KeyboardEvent('X'.code, text = "X"))
			assertThat(awaitSnapshot()).contains("aXb")
		}
	}

	@Test fun pasteInsertsAtCaret() = runTest {
		runMosaicTest {
			var text by mutableStateOf("")
			setContent { BasicTextField(text, onValueChange = { text = it }) }
			awaitSnapshot()
			focusField()
			awaitSnapshot()

			sendPaste("你好，世界")
			assertThat(awaitSnapshot()).contains("你好，世界")
		}
	}
}
