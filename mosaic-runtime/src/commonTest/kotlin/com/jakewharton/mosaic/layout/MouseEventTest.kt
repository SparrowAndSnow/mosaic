package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.offset
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.terminal.MouseEvent as TerminalMouseEvent
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.Row
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class MouseEventTest {

	private data class Dispatch(
		val name: String,
		val event: MouseEvent,
	)

	private class Recorder {
		val dispatched = mutableListOf<Dispatch>()
		val consume = mutableMapOf<String, Boolean>()

		fun handler(name: String): (MouseEvent) -> Boolean = { event ->
			dispatched += Dispatch(name, event)
			consume[name] ?: true
		}
	}

	@Test fun clickIsHandledByTopMostChild() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("a"))
			assertThat(recorder.dispatched[0].event.x).isEqualTo(2)
			assertThat(recorder.dispatched[0].event.y).isEqualTo(2)
		}
	}

	@Test fun clickIsHandledBySecondChild() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(MouseEvent(7, 2, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("b"))
		}
	}

	@Test fun clickOutsideChildrenFallsBackToAncestor() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			// Below the row, still within the root.
			root.sendMouseEvent(MouseEvent(7, 6, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("root"))
		}
	}

	@Test fun clickOutsideAllBoundsIsIgnored() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(MouseEvent(12, 12, MouseEvent.Type.Press, MouseEvent.Button.Left))
			root.sendMouseEvent(MouseEvent(-1, 0, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched).isEmpty()
		}
	}

	@Test fun unhandledClickBubblesToAncestor() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder().apply { consume["a"] = false }
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("a", "root"))
		}
	}

	@Test fun consumedClickStopsPropagation() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8).onMouseEvent(recorder.handler("root"))) {
					Row(Modifier.width(10)) {
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("a")))
						Box(Modifier.size(5, 4).onMouseEvent(recorder.handler("b")))
					}
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendMouseEvent(MouseEvent(2, 2, MouseEvent.Type.Press, MouseEvent.Button.Left))).isTrue()

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("a"))
		}
	}

	@Test fun overloadedSiblingOnTopHandlesClick() = runTest {
		runMosaicTest(NodeSnapshots) {
			val recorder = Recorder()
			setContent {
				Box(Modifier.width(10).height(8)) {
					Box(Modifier.size(5, 5).onMouseEvent(recorder.handler("back")))
					Box(Modifier.size(5, 5).offset(2, 2).onMouseEvent(recorder.handler("front")))
				}
			}
			val root = awaitSnapshot()

			// Both siblings contain (3, 3); the later (drawn on top) one wins.
			root.sendMouseEvent(MouseEvent(3, 3, MouseEvent.Type.Press, MouseEvent.Button.Left))

			assertThat(recorder.dispatched.map { it.name }).isEqualTo(listOf("front"))
		}
	}

	@Test fun terminalMouseEventReachesHandler() = runTest {
		runMosaicTest {
			var last by mutableStateOf("none")

			setContent {
				Box(Modifier.onMouseEvent {
					last = "${it.x},${it.y}:${it.type}"
					true
				}) {
					com.jakewharton.mosaic.ui.Text(last)
				}
			}

			assertThat(awaitSnapshot()).isEqualTo("none")

			sendMouseEvent(
				com.jakewharton.mosaic.terminal.MouseEvent(
					x = 2,
					y = 0,
					type = com.jakewharton.mosaic.terminal.MouseEvent.Type.Press,
					button = com.jakewharton.mosaic.terminal.MouseEvent.Button.Left,
				),
			)

			assertThat(awaitSnapshot()).isEqualTo("2,0:Press")
		}
	}

	private fun motion(x: Int, y: Int): TerminalMouseEvent = TerminalMouseEvent(
		x = x,
		y = y,
		type = TerminalMouseEvent.Type.Motion,
	)

	@Test fun motionEventsAreCoalescedPerFrame() = runTest {
		runMosaicTest(NodeSnapshots) {
			var received by mutableStateOf(emptyList<MouseEvent>())
			setContent {
				// Reading [received] in composition ties state mutations to recomposition so that
				// awaitSnapshot can observe them.
				Box(Modifier.size(20, 10).onMouseEvent {
					received += it
					true
				}) {
					Text(received.size.toString())
				}
			}
			awaitSnapshot()
			received = emptyList()

			// One drain batch: three motions then a release. Only the final motion position is
			// delivered, and it is delivered before the release.
			sendMouseEvent(motion(1, 1))
			sendMouseEvent(motion(2, 2))
			sendMouseEvent(motion(3, 3))
			sendMouseEvent(
				TerminalMouseEvent(
					x = 3,
					y = 3,
					type = TerminalMouseEvent.Type.Release,
					button = TerminalMouseEvent.Button.Left,
				),
			)

			awaitSnapshot()

			assertThat(received.map { "${it.type}@${it.x},${it.y}" }).isEqualTo(
				listOf("Motion@3,3", "Release@3,3"),
			)
		}
	}

	@Test fun motionOnlyBatchDeliversSingleLatestMotion() = runTest {
		runMosaicTest(NodeSnapshots) {
			var received by mutableStateOf(emptyList<MouseEvent>())
			setContent {
				// Reading [received] in composition ties state mutations to recomposition so that
				// awaitSnapshot can observe them.
				Box(Modifier.size(20, 10).onMouseEvent {
					received += it
					true
				}) {
					Text(received.size.toString())
				}
			}
			awaitSnapshot()
			received = emptyList()

			sendMouseEvent(motion(1, 2))
			sendMouseEvent(motion(7, 8))

			awaitSnapshot()

			assertThat(received.map { "${it.type}@${it.x},${it.y}" }).isEqualTo(listOf("Motion@7,8"))
		}
	}

	@Test fun discreteEventsAreNeverCoalesced() = runTest {
		runMosaicTest(NodeSnapshots) {
			var received by mutableStateOf(emptyList<MouseEvent>())
			setContent {
				// Reading [received] in composition ties state mutations to recomposition so that
				// awaitSnapshot can observe them.
				Box(Modifier.size(20, 10).onMouseEvent {
					received += it
					true
				}) {
					Text(received.size.toString())
				}
			}
			awaitSnapshot()
			received = emptyList()

			sendMouseEvent(
				TerminalMouseEvent(0, 0, TerminalMouseEvent.Type.Press, TerminalMouseEvent.Button.Left),
			)
			sendMouseEvent(
				TerminalMouseEvent(0, 0, TerminalMouseEvent.Type.Press, TerminalMouseEvent.Button.Right),
			)

			awaitSnapshot()

			// Presses are discrete and must all arrive.
			assertThat(received.map { it.button }).isEqualTo(
				listOf(MouseEvent.Button.Left, MouseEvent.Button.Right),
			)
		}
	}
}
