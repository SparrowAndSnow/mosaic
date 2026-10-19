package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PointerInputTest {

	private fun motion(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Motion)

	private fun press(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Press, MouseEvent.Button.Left)

	private fun release(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Release, MouseEvent.Button.Left)

	@Test fun hoverableReportsEnterAndExit() = runTest {
		runMosaicTest(NodeSnapshots) {
			val hovered = mutableListOf<Boolean>()
			setContent {
				Box(Modifier.size(10, 5).hoverable { hovered += it }) { Text("H") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(motion(1, 1))
			assertThat(hovered).isEqualTo(listOf(true))

			// Leaving into empty space reports exit.
			root.sendMouseEvent(motion(20, 20))
			assertThat(hovered).isEqualTo(listOf(true, false))

			// Re-entering reports a fresh enter.
			root.sendMouseEvent(motion(9, 4))
			assertThat(hovered).isEqualTo(listOf(true, false, true))
		}
	}

	@Test fun hoverableMovesBetweenSiblings() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			val b = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).hoverable { a += it }) { Text("A") }
					Box(Modifier.size(10, 5).hoverable { b += it }) { Text("B") }
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(motion(2, 2))
			assertThat(a).isEqualTo(listOf(true))
			assertThat(b).isEmpty()

			root.sendMouseEvent(motion(12, 2))
			assertThat(a).isEqualTo(listOf(true, false))
			assertThat(b).isEqualTo(listOf(true))
		}
	}

	@Test fun hoverableIgnoresDisabled() = runTest {
		runMosaicTest(NodeSnapshots) {
			val hovered = mutableListOf<Boolean>()
			setContent {
				Box(Modifier.size(10, 5).hoverable(enabled = false) { hovered += it }) { Text("H") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(motion(2, 2))
			assertThat(hovered).isEmpty()
		}
	}

	@Test fun clickablePressAndReleaseInsideTriggersClick() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 5).clickable { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			assertThat(clicks).isEqualTo(0)

			root.sendMouseEvent(release(2, 2))
			assertThat(clicks).isEqualTo(1)
		}
	}

	@Test fun clickableReleaseOutsideCancelsClick() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 5).clickable { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(20, 20))

			assertThat(clicks).isEqualTo(0)
		}
	}

	@Test fun pressOutsideDoesNotClick() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 5).clickable { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(20, 20))
			root.sendMouseEvent(release(2, 2))

			assertThat(clicks).isEqualTo(0)
		}
	}

	@Test fun clickableIgnoresDisabled() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 5).clickable(enabled = false) { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))

			assertThat(clicks).isEqualTo(0)
		}
	}

	@Test fun clickableOverlappingSiblingWins() = runTest {
		runMosaicTest(NodeSnapshots) {
			var back = 0
			var front = 0
			setContent {
				Box(Modifier.size(10, 10)) {
					Box(Modifier.size(5, 5).clickable { back++ })
					Box(Modifier.size(5, 5).offset(2, 2).clickable { front++ })
				}
			}
			val root = awaitSnapshot()

			// Both siblings contain (3, 3); the later (drawn on top) one owns the press.
			root.sendMouseEvent(press(3, 3))
			root.sendMouseEvent(release(3, 3))

			assertThat(back).isEqualTo(0)
			assertThat(front).isEqualTo(1)
		}
	}

	@Test fun clickableNestedInnerWins() = runTest {
		runMosaicTest(NodeSnapshots) {
			var inner = 0
			var outer = 0
			setContent {
				Box(Modifier.size(10, 10).clickable { outer++ }) {
					Box(Modifier.size(4, 4).clickable { inner++ })
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))

			assertThat(inner).isEqualTo(1)
			assertThat(outer).isEqualTo(0)
		}
	}

	@Test fun clickIsWheelIgnored() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 5).clickable { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.WheelUp))
			root.sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Release, MouseEvent.Button.WheelUp))

			assertThat(clicks).isEqualTo(0)
		}
	}

	@Test fun hoverAndClickCoexist() = runTest {
		runMosaicTest(NodeSnapshots) {
			val hovered = mutableListOf<Boolean>()
			var clicks = 0
			setContent {
				Box(
					Modifier
						.size(10, 5)
						.clickable { clicks++ }
						.hoverable { hovered += it },
				) { Text("HC") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(motion(2, 2))
			assertThat(hovered).isEqualTo(listOf(true))

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))
			assertThat(clicks).isEqualTo(1)
			assertThat(hovered).isEqualTo(listOf(true))
		}
	}

	@Test fun motionDoesNotFireClickWhilePressed() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.size(10, 10).clickable { clicks++ }) { Text("C") }
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			// Drag across the surface then release inside the original bounds.
			root.sendMouseEvent(motion(8, 8))
			root.sendMouseEvent(release(3, 3))

			assertThat(clicks).isEqualTo(1)
		}
	}
}
