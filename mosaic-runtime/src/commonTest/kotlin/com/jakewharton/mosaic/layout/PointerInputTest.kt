@file:Suppress("ktlint:compose:mutable-state-autoboxing")

package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.BoxWithConstraints
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PointerInputTest {

	private class TestPointerInput(override val state: PointerInputState) : PointerInputModifier

	private fun pointerNode(
		state: PointerInputState,
		width: Int = 10,
		height: Int = 5,
	): MosaicNode {
		val node = MosaicNode(
			measurePolicy = MeasurePolicy { _, _ -> layout(width, height) {} },
			debugPolicy = DebugPolicy { "" },
			isStatic = false,
		)
		node.pointerInputs += state
		node.measureAndPlace()
		return node
	}

	private fun click(pointer: PointerState, node: MosaicNode) {
		pointer.preDispatch(node, press(2, 2))
		pointer.preDispatch(node, release(2, 2))
	}

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

	@Test fun clickSurvivesNodeReplacementDuringPress() = runTest {
		runMosaicTest(NodeSnapshots) {
			var generation by mutableStateOf(0)
			var clicks = 0
			val state = PointerInputState().apply { onClick = { clicks++ } }
			val element = TestPointerInput(state)
			setContent {
				key(generation) {
					Box(
						Modifier
							.size(10, 5)
							.then(element)
							.onMouseEvent { event ->
								if (event.type == MouseEvent.Type.Press) {
									generation++
								}
								false
							},
					) { Text("row $generation") }
				}
			}
			val root = awaitSnapshot()

			// The press is captured by the old node, which this recomposition replaces.
			root.sendMouseEvent(press(2, 2))
			awaitSnapshot()

			root.sendMouseEvent(release(2, 2))
			assertThat(clicks).isEqualTo(1)
		}
	}

	@Test fun clickableRemovedOnPressDoesNotClick() = runTest {
		runMosaicTest(NodeSnapshots) {
			var visible by mutableStateOf(true)
			var clicks = 0
			var doubleClicks = 0
			setContent {
				if (visible) {
					Box(
						Modifier
							.size(10, 5)
							.onMouseEvent { event ->
								if (event.type == MouseEvent.Type.Press) {
									visible = false
								}
								false
							}
							.clickable(onDoubleClick = { doubleClicks++ }) { clicks++ },
					) { Text("C") }
				} else {
					Box(Modifier.size(10, 5)) { Text("Gone") }
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			awaitSnapshot()

			// The clickable was removed while the button was held, so the release must not fire.
			root.sendMouseEvent(release(2, 2))
			assertThat(clicks).isEqualTo(0)
			assertThat(doubleClicks).isEqualTo(0)
		}
	}

	@Test fun clickSurvivesNodeReplacementWithCapturedState() {
		var clicks = 0
		val state = PointerInputState().apply { onClick = { clicks++ } }
		val pointer = PointerState()

		pointer.preDispatch(pointerNode(state), press(2, 2))
		// The element state is still installed, but on a freshly-built node.
		pointer.preDispatch(pointerNode(state), release(2, 2))

		assertThat(clicks).isEqualTo(1)
	}

	@Test fun doubleClickWithinWindowFiresOneOfEach() {
		var now = 1_000L
		val calls = mutableListOf<String>()
		val state = PointerInputState().apply {
			onClick = { calls += "click" }
			onDoubleClick = { calls += "double" }
		}
		val node = pointerNode(state)
		val pointer = PointerState(nowMillis = { now })

		click(pointer, node)
		now += 300
		click(pointer, node)

		assertThat(calls).isEqualTo(listOf("click", "double"))
	}

	@Test fun spacedClicksAreNotADoubleClick() {
		var now = 1_000L
		val calls = mutableListOf<String>()
		val state = PointerInputState().apply {
			onClick = { calls += "click" }
			onDoubleClick = { calls += "double" }
		}
		val node = pointerNode(state)
		val pointer = PointerState(nowMillis = { now })

		click(pointer, node)
		now += 401
		click(pointer, node)

		assertThat(calls).isEqualTo(listOf("click", "click"))
	}

	@Test fun tripleClickStartsANewPair() {
		var now = 1_000L
		val calls = mutableListOf<String>()
		val state = PointerInputState().apply {
			onClick = { calls += "click" }
			onDoubleClick = { calls += "double" }
		}
		val node = pointerNode(state)
		val pointer = PointerState(nowMillis = { now })

		repeat(3) {
			click(pointer, node)
			now += 100
		}

		assertThat(calls).isEqualTo(listOf("click", "double", "click"))
	}

	@Test fun doubleClickIsTrackedPerElement() {
		var now = 1_000L
		val calls = mutableListOf<String>()
		fun state(name: String) = PointerInputState().apply {
			onClick = { calls += "$name:click" }
			onDoubleClick = { calls += "$name:double" }
		}
		val first = state("first")
		val second = state("second")
		val pointer = PointerState(nowMillis = { now })

		click(pointer, pointerNode(first))
		now += 100
		click(pointer, pointerNode(second))

		assertThat(calls).isEqualTo(listOf("first:click", "second:click"))
	}

	@Test fun disabledAfterPressSuppressesBothCallbacks() {
		var calls = 0
		val state = PointerInputState().apply {
			onClick = { calls++ }
			onDoubleClick = { calls++ }
		}
		val node = pointerNode(state)
		val pointer = PointerState()

		pointer.preDispatch(node, press(2, 2))
		state.enabled = false
		pointer.preDispatch(node, release(2, 2))

		assertThat(calls).isEqualTo(0)
	}

	@Test fun disposedAfterPressDoesNotClick() {
		var calls = 0
		val state = PointerInputState().apply {
			onClick = { calls++ }
			onDoubleClick = { calls++ }
		}
		val node = pointerNode(state)
		val pointer = PointerState()

		pointer.preDispatch(node, press(2, 2))
		state.dispose()
		pointer.preDispatch(node, release(2, 2))

		assertThat(calls).isEqualTo(0)
	}

	@Test fun clickableDoubleClickWithinWindow() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			var doubleClicks = 0
			setContent {
				Box(
					Modifier.size(10, 5).clickable(
						onDoubleClick = { doubleClicks++ },
					) { clicks++ },
				) { Text("C") }
			}
			val root = awaitSnapshot()

			repeat(2) {
				root.sendMouseEvent(press(2, 2))
				root.sendMouseEvent(release(2, 2))
			}

			assertThat(clicks).isEqualTo(1)
			assertThat(doubleClicks).isEqualTo(1)
		}
	}

	@Test fun clickableInsideBoxWithConstraintsClicks() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				BoxWithConstraints(Modifier.size(10, 5)) {
					Box(Modifier.size(10, 5).clickable { clicks++ }) { Text("C") }
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))
			assertThat(clicks).isEqualTo(1)
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
