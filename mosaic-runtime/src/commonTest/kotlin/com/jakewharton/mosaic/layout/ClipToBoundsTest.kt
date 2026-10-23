package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Locks in [clipToBounds]: a layout clips the drawing of its descendants to its own rectangle.
 * Content shifted outside that rectangle by [offset] - while still inside the enclosing canvas -
 * must never leak into neighboring cells. Each pair of tests renders the same offset content with
 * and without the clip so the clipping is provably what keeps the overflow hidden.
 */
class ClipToBoundsTest {
	private val rendering =
		DebugRendering(TestTerminal.Capabilities(ansiLevel = AnsiLevel.NONE))

	private fun rows(frame: String): List<String> = frame.substringAfter("OUTPUT:\r\n").split("\r\n")

	@Test fun contentPushedPastRightEdgeIsClipped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 3)) {
					Box(Modifier.size(5, 1).clipToBounds()) {
						Text("hello", modifier = Modifier.offset(x = 2))
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			// "hello" at column 2 covers columns 2..6; the 5-wide clip lets only 2..4 ("hel") out.
			assertThat(frame[0]).isEqualTo("  hel")
		}
	}

	@Test fun contentPushedPastRightEdgeOverflowsWithoutClip() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 3)) {
					Box(Modifier.size(5, 1)) {
						Text("hello", modifier = Modifier.offset(x = 2))
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			// Without the clip the overhang reaches the canvas columns 2..6.
			assertThat(frame[0]).isEqualTo("  hello")
		}
	}

	@Test fun contentPushedBelowEdgeIsClipped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 4)) {
					Box(Modifier.size(5, 2).clipToBounds()) {
						Box(Modifier.offset(y = 2)) {
							Text("A")
						}
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			// The "A" lands at row 2, past the 2-row clip; nothing may show anywhere below it.
			assertThat(frame[0]).isEqualTo("")
			assertThat(frame[1]).isEqualTo("")
			assertThat(frame[2]).isEqualTo("")
			assertThat(frame[3]).isEqualTo("")
		}
	}

	@Test fun contentPushedBelowEdgeOverflowsWithoutClip() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 4)) {
					Box(Modifier.size(5, 2)) {
						Box(Modifier.offset(y = 2)) {
							Text("A")
						}
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			assertThat(frame[2]).isEqualTo("A")
		}
	}
}
