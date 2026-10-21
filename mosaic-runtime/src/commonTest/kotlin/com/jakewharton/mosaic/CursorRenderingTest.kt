package com.jakewharton.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.layout.offset
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Cursor
import com.jakewharton.mosaic.ui.CursorShape
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Locks the hardware-cursor contract of [AnsiIncrementalRendering]: where the cursor is placed,
 * how its shape/blink are encoded, and how visibility is managed once an app opts into the cursor
 * API. [VirtualTerminal] replays the emitted frames to assert the position a terminal would
 * actually end up at.
 */
class CursorRenderingTest {
	// The hide/show framing is only used when the terminal cannot synchronize output, so disabling
	// synchronization makes the hide -> draw -> position -> show order directly assertable.
	private val noSync = TestTerminal.Capabilities(synchronizedOutput = false)

	@Test fun cursorIsPositionedAtItsLayoutPosition() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(10, 3)) {
					Text("abc")
					Cursor(Modifier.offset(x = 2, y = 1))
				}
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains("${CSI}2;3H")
			assertThat(frame).contains(cursorShapeSequence(1))
			assertThat(frame.endsWith(showCursor)).isTrue()

			val terminal = VirtualTerminal(rows = 3, columns = 10)
			terminal.feed(frame)
			assertThat(terminal.displayRow(0).trimEnd()).isEqualTo("abc")
			assertThat(terminal.cursorRow).isEqualTo(1)
			assertThat(terminal.cursorColumn).isEqualTo(2)
		}
	}

	@Test fun cursorPositionIncludesAncestorPadding() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(10, 4)) {
					Box(Modifier.padding(2)) {
						Cursor()
					}
				}
			}
			assertThat(awaitSnapshot().toString()).contains("${CSI}3;3H")
		}
	}

	@Test fun cursorShapeAndBlinkSelectDecscusrCode() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(10, 3)) {
					Cursor(Modifier.offset(x = 1, y = 1), shape = CursorShape.Bar, blink = false)
				}
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains(cursorShapeSequence(6))
			assertThat(frame).contains("${CSI}2;2H")
		}
	}

	@Test fun cursorOnWideClusterContinuationSnapsToItsHead() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(4, 1)) {
					Text("中")
					Cursor(Modifier.offset(x = 1))
				}
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains("${CSI}1;1H")

			val terminal = VirtualTerminal(rows = 1, columns = 4)
			terminal.feed(frame)
			assertThat(terminal.cursorColumn).isEqualTo(0)
		}
	}

	@Test fun removingCursorHidesItAndRestoresShape() = runTest {
		var cursorVisible by mutableStateOf(true)
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(10, 3)) {
					Text("abc")
					if (cursorVisible) {
						Cursor(Modifier.offset(x = 1, y = 1))
					}
				}
			}
			assertThat(awaitSnapshot().toString()).contains(showCursor)

			cursorVisible = false
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains(hideCursor)
			assertThat(frame).contains(resetCursorShape)
			assertThat(frame).doesNotContain(showCursor)
		}
	}

	@Test fun cursorOutsideCompositionIsNotShown() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(5, 2)) {
					Cursor(Modifier.offset(x = 9))
				}
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("${CSI}1;10H")
			assertThat(frame).doesNotContain(showCursor)
		}
	}

	@Test fun cursorMovesBetweenFrames() = runTest {
		var column by mutableIntStateOf(1)
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Box(Modifier.size(10, 1)) {
					Cursor(Modifier.offset(x = column))
				}
			}
			assertThat(awaitSnapshot().toString()).contains("${CSI}1;2H")

			column = 5
			assertThat(awaitSnapshot().toString()).contains("${CSI}1;6H")
		}
	}

	@Test fun cursorWorksWithSynchronizedOutput() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(TestTerminal.Capabilities()))) {
			setContent {
				Box(Modifier.size(10, 3)) {
					Cursor(Modifier.offset(x = 3, y = 1))
				}
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains("${CSI}2;4H")
			assertThat(frame).contains(showCursor)
		}
	}

	@Test fun framesWithoutCursorKeepTheExistingHideShowFraming() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(noSync))) {
			setContent {
				Text("a")
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains(hideCursor)
			assertThat(frame.endsWith(showCursor)).isTrue()
		}
	}

	@Test fun exitRestoresTheTerminalDefaultCursorShape() = runBlocking {
		val terminal = TestTerminal()
		val output = ByteCollector()
		val writer = FrameWriter(writeBytes = output::appendBytes, flushBytes = {})

		runMosaicComposition(
			terminal = terminal,
			rendering = AnsiIncrementalRendering(terminal.capabilities),
			frameWriter = writer,
			content = {
				Box(Modifier.size(4, 1)) {
					Cursor()
				}
			},
		)

		assertThat(output.text).contains(showCursor)
		assertThat(output.text.endsWith(resetCursorShape)).isTrue()
	}
}
