package com.jakewharton.mosaic.ui

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.layout.ScrollState
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.MouseEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Lazy lists compose only the window around the visible viewport: a 1000-item list renders a
 * handful of items whose count is independent of the total, scrolls through the whole list, and
 * keeps the offset clamped even when the wheel is spun at the edges. The node debug output is
 * asserted directly because the window is what makes the container lazy.
 *
 * A state mutation emits exactly one changed frame; the harness `awaitSnapshot` returns on a
 * change, so each mutation is followed by a single wait here (the one extra wait right after
 * `setContentAndSnapshot` settles the window the first measure learned).
 */
class LazyColumnTest {
	private val rendering = DebugRendering(TestTerminal.Capabilities(ansiLevel = AnsiLevel.NONE))

	private fun rows(frame: String): List<String> = frame.substringAfter("OUTPUT:\r\n").split("\r\n")

	private fun nodeDebug(frame: String): String = frame.substringAfter("NODES:\r\n").substringBefore("\r\n\r\n")

	@Test fun composesOnlyTheVisibleWindow() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1, beforeLookahead = 0, afterLookahead = 0) {
						items(1000) { index -> Text("row$index") }
					}
				}
			}
			val settled = awaitSnapshot()

			assertThat(state.maxValue).isEqualTo(995)
			assertThat(nodeDebug(settled)).contains("LazyColumn(total=1000, composed=5)")
			assertThat(rows(settled)[0]).isEqualTo("row0")
			assertThat(rows(settled)[4]).isEqualTo("row4")
			assertThat(rows(settled)[5]).isEqualTo("")
		}
	}

	@Test fun scrollingMovesTheWindowAcrossTheList() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1, beforeLookahead = 0, afterLookahead = 0) {
						items(1000) { index -> Text("row$index") }
					}
				}
			}
			awaitSnapshot()

			state.scrollTo(100)
			val frame = awaitSnapshot()

			assertThat(state.maxValue).isEqualTo(995)
			assertThat(state.value).isEqualTo(100)
			assertThat(nodeDebug(frame)).contains("LazyColumn(total=1000, composed=5)")
			assertThat(rows(frame)[0]).isEqualTo("row100")
			assertThat(rows(frame)[4]).isEqualTo("row104")
		}
	}

	@Test fun mouseWheelScrollsTheLazyList() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1) {
						items(1000) { index -> Text("row$index") }
					}
				}
			}
			awaitSnapshot()

			sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.WheelDown))
			val frame = awaitSnapshot()
			assertThat(state.value).isEqualTo(3)
			assertThat(rows(frame)[0]).isEqualTo("row3")

			sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.WheelUp))
			val back = awaitSnapshot()
			assertThat(state.value).isEqualTo(0)
			assertThat(rows(back)[0]).isEqualTo("row0")
		}
	}

	@Test fun listShorterThanViewportDoesNotScroll() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1, beforeLookahead = 0, afterLookahead = 0) {
						items(3) { index -> Text("row$index") }
					}
				}
			}
			val frame = awaitSnapshot()

			assertThat(state.maxValue).isEqualTo(0)
			assertThat(nodeDebug(frame)).contains("LazyColumn(total=3, composed=3)")
			assertThat(rows(frame)[0]).isEqualTo("row0")
			assertThat(rows(frame)[2]).isEqualTo("row2")
			assertThat(rows(frame)[3]).isEqualTo("")
		}
	}

	@Test fun emptyListRendersNothing() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1) {
						items(0) { index -> Text("row$index") }
					}
				}
			}
			val frame = awaitSnapshot()

			assertThat(state.maxValue).isEqualTo(0)
			assertThat(nodeDebug(frame)).contains("LazyColumn(total=0, composed=0)")
			assertThat(rows(frame)[0]).isEqualTo("")
		}
	}

	@Test fun multipleItemsSectionsShareGlobalIndices() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 2)) {
					LazyColumn(state = state, itemHeight = 1, beforeLookahead = 0, afterLookahead = 0) {
						items(3) { index -> Text("A$index") }
						items(listOf("x", "y")) { text -> Text(text) }
					}
				}
			}
			val top = awaitSnapshot()
			assertThat(rows(top)[0]).isEqualTo("A0")
			assertThat(rows(top)[1]).isEqualTo("A1")

			state.scrollTo(3)
			val scrolled = awaitSnapshot()
			// Global index 3 = first element of the second items() block.
			assertThat(rows(scrolled)[0]).isEqualTo("x")
			assertThat(state.value).isEqualTo(3)
		}
	}

	@Test fun wheelAtTheEndStaysClamped() = runTest {
		val state = ScrollState()
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContentAndSnapshot {
				Box(Modifier.size(20, 5)) {
					LazyColumn(state = state, itemHeight = 1) {
						items(6) { index -> Text("row$index") }
					}
				}
			}
			awaitSnapshot()

			sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.WheelDown))
			sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.WheelDown))
			val frame = awaitSnapshot()
			assertThat(state.value).isEqualTo(1)
			assertThat(rows(frame)[0]).isEqualTo("row1")
			assertThat(rows(frame)[4]).isEqualTo("row5")
		}
	}
}
