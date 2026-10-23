package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.MouseEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.unit.Constraints
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * End-to-end scrolling: [verticalScroll]/[horizontalScroll] turn a bounded viewport box into a
 * window over arbitrarily long content, offset it by the scroll state, and keep scrolled-away
 * cells hidden by the implicit clip. The outer box is larger than the viewport so overhanging,
 * clipped-away content would be visible if the clip ever failed.
 */
class ScrollRenderingTest {
	private val rendering =
		DebugRendering(TestTerminal.Capabilities(ansiLevel = AnsiLevel.NONE))

	private fun rows(frame: String): List<String> = frame.substringAfter("OUTPUT:\r\n").split("\r\n")

	@Test fun verticalScrollShowsWindowAndClipsRest() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(20, 6)) {
					Box(Modifier.size(20, 3)) {
						Column(Modifier.verticalScroll(state)) {
							Text("row0")
							Text("row1")
							Text("row2")
							Text("row3")
							Text("row4")
							Text("row5")
						}
					}
				}
			}
			val initial = rows(awaitSnapshot().toString())

			// Viewport is 3 rows; the state learns content overflows it.
			assertThat(state.maxValue).isEqualTo(3)
			assertThat(initial[0]).isEqualTo("row0")
			assertThat(initial[1]).isEqualTo("row1")
			assertThat(initial[2]).isEqualTo("row2")
			// Rows 3..5 exist inside the 6-row surface but outside the clip: hidden.
			assertThat(initial[3]).isEqualTo("")
			assertThat(initial[4]).isEqualTo("")
			assertThat(initial[5]).isEqualTo("")

			// Mutating the scroll state invalidates layout; the next frame shows the new window.
			state.scrollTo(3)
			val scrolled = rows(awaitSnapshot().toString())
			assertThat(scrolled[0]).isEqualTo("row3")
			assertThat(scrolled[1]).isEqualTo("row4")
			assertThat(scrolled[2]).isEqualTo("row5")
			assertThat(scrolled[3]).isEqualTo("")
		}
	}

	@Test fun verticalScrollWithoutClipWouldLeak() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(20, 6)) {
					Box(Modifier.size(20, 3)) {
						Column(ScrollStatePassThrough(state)) {
							Text("row0")
							Text("row1")
							Text("row2")
							Text("row3")
							Text("row4")
							Text("row5")
						}
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			// Control: without the scroll modifier's clip, the overflow is drawn on the surface.
			assertThat(frame[3]).isEqualTo("row3")
			assertThat(frame[4]).isEqualTo("row4")
			assertThat(frame[5]).isEqualTo("row5")
		}
	}

	@Test fun horizontalScrollShowsWindowAndClipsRest() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 2)) {
					Box(Modifier.size(4, 1)) {
						Row(Modifier.horizontalScroll(state)) {
							Text("ab")
							Text("cd")
							Text("ef")
						}
					}
				}
			}
			val initial = rows(awaitSnapshot().toString())
			assertThat(state.maxValue).isEqualTo(2)
			// "ef" lands at columns 4..5 (inside the 8-wide surface) but outside the 4-wide clip.
			assertThat(initial[0]).isEqualTo("abcd")

			state.scrollTo(2)
			val scrolled = rows(awaitSnapshot().toString())
			assertThat(scrolled[0]).isEqualTo("cdef")
		}
	}

	@Test fun contentThatFitsDoesNotScroll() = runTest {
		val state = ScrollState().apply { scrollTo(99) }
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(20, 5)) {
					Box(Modifier.size(20, 5)) {
						Column(Modifier.verticalScroll(state)) {
							Text("a")
							Text("b")
							Text("c")
						}
					}
				}
			}
			val frame = rows(awaitSnapshot().toString())

			assertThat(state.maxValue).isEqualTo(0)
			assertThat(state.value).isEqualTo(0)
			assertThat(frame[0]).isEqualTo("a")
			assertThat(frame[1]).isEqualTo("b")
			assertThat(frame[2]).isEqualTo("c")
		}
	}

	@Test fun verticalScrollRespondsToMouseWheel() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(20, 6)) {
					Box(Modifier.size(20, 3)) {
						Column(Modifier.verticalScroll(state)) {
							Text("row0")
							Text("row1")
							Text("row2")
							Text("row3")
							Text("row4")
							Text("row5")
						}
					}
				}
			}
			val initial = rows(awaitSnapshot().toString())
			assertThat(state.maxValue).isEqualTo(3)
			assertThat(initial[0]).isEqualTo("row0")

			// A wheel notch over the viewport scrolls three rows down.
			sendMouseEvent(MouseEvent(2, 1, MouseEvent.Type.Press, MouseEvent.Button.WheelDown))
			val scrolled = rows(awaitSnapshot().toString())
			assertThat(scrolled[0]).isEqualTo("row3")
			assertThat(state.value).isEqualTo(3)

			// Wheeling back up returns to the top.
			sendMouseEvent(MouseEvent(2, 1, MouseEvent.Type.Press, MouseEvent.Button.WheelUp))
			val back = rows(awaitSnapshot().toString())
			assertThat(back[0]).isEqualTo("row0")
			assertThat(state.value).isEqualTo(0)
		}
	}

	@Test fun horizontalScrollRespondsToMouseWheel() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(8, 2)) {
					Box(Modifier.size(4, 1)) {
						Row(Modifier.horizontalScroll(state)) {
							Text("ab")
							Text("cd")
							Text("ef")
						}
					}
				}
			}
			val initial = rows(awaitSnapshot().toString())
			assertThat(state.maxValue).isEqualTo(2)
			assertThat(initial[0]).isEqualTo("abcd")

			// A wheel notch over the row scrolls right by three cells (clamped to the end).
			sendMouseEvent(MouseEvent(1, 0, MouseEvent.Type.Press, MouseEvent.Button.WheelDown))
			val scrolled = rows(awaitSnapshot().toString())
			assertThat(scrolled[0]).isEqualTo("cdef")
			assertThat(state.value).isEqualTo(2)
		}
	}
}

/** A [verticalScroll] whose scroll offset is always zero - an unscrolled-but-clipping list. */
private class ScrollStatePassThrough(
	private val scrollState: ScrollState,
) : LayoutModifier {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val content = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
		val viewport = if (constraints.maxHeight == Constraints.Infinity) content.height else constraints.maxHeight
		scrollState.maxValue = maxOf(0, content.height - viewport)
		return layout(content.width, viewport) {
			content.place(0, 0)
		}
	}

	override fun toString(): String = "ScrollPassThrough"
}
