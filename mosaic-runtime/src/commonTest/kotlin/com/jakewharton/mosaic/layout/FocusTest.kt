package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class FocusTest {

	private fun press(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Press, MouseEvent.Button.Left)

	private fun release(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Release, MouseEvent.Button.Left)

	@Test fun tabMovesFocusForwardAndWraps() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			val b = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
					Box(Modifier.size(10, 5).focusable().onFocusChanged { b += it.isFocused })
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(a).isEqualTo(listOf(true))

			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(a).isEqualTo(listOf(true, false))
			assertThat(b).isEqualTo(listOf(true))

			// Wraps back to the first.
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(b).isEqualTo(listOf(true, false))
			assertThat(a).isEqualTo(listOf(true, false, true))
		}
	}

	@Test fun shiftTabMovesFocusBackward() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			val b = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
					Box(Modifier.size(10, 5).focusable().onFocusChanged { b += it.isFocused })
				}
			}
			val root = awaitSnapshot()

			// Shift+Tab from nothing focuses the last focusable.
			assertThat(root.sendKeyEvent(KeyEvent("Tab", shift = true))).isTrue()
			assertThat(b).isEqualTo(listOf(true))

			// Shift+Tab moves back to the first.
			assertThat(root.sendKeyEvent(KeyEvent("Tab", shift = true))).isTrue()
			assertThat(b).isEqualTo(listOf(true, false))
			assertThat(a).isEqualTo(listOf(true))
		}
	}

	@Test fun tabIsConsumedEvenForSingleFocusable() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			setContent {
				Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(a).isEqualTo(listOf(true))
			// Repeated capture does not fire while remaining focused.
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(a).isEqualTo(listOf(true))
		}
	}

	@Test fun tabWithoutFocusablesFallsThrough() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(10, 5)) { Text("no focus here") }
			}
			val root = awaitSnapshot()

			// No focusable exists, so Tab is not consumed and the key keeps its legacy behavior.
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isFalse()
		}
	}

	@Test fun clickFocusesFocusable() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			val b = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
					Box(Modifier.size(10, 5).focusable().onFocusChanged { b += it.isFocused })
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))
			assertThat(a).isEqualTo(listOf(true))

			root.sendMouseEvent(press(12, 2))
			root.sendMouseEvent(release(12, 2))
			assertThat(a).isEqualTo(listOf(true, false))
			assertThat(b).isEqualTo(listOf(true))
		}
	}

	@Test fun clickDoesNotFocusNonFocusable() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
					Box(Modifier.size(10, 5)) { Text("plain") }
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			assertThat(a).isEqualTo(listOf(true))

			// Clicking the non-focusable sibling leaves focus on A.
			root.sendMouseEvent(press(12, 2))
			root.sendMouseEvent(release(12, 2))
			assertThat(a).isEqualTo(listOf(true))
		}
	}

	@Test fun disabledFocusableIsSkipped() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			val b = mutableListOf<Boolean>()
			setContent {
				Row(Modifier.width(20).height(5)) {
					Box(Modifier.size(10, 5).focusable(enabled = false).onFocusChanged { a += it.isFocused })
					Box(Modifier.size(10, 5).focusable().onFocusChanged { b += it.isFocused })
				}
			}
			val root = awaitSnapshot()

			// The disabled focusable is not traversable: Tab lands on the enabled one.
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(a).isEmpty()
			assertThat(b).isEqualTo(listOf(true))

			// And not click-focusable.
			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))
			assertThat(a).isEmpty()
			assertThat(b).isEqualTo(listOf(true))
		}
	}

	@Test fun focusRequesterFocusesTarget() = runTest {
		runMosaicTest(NodeSnapshots) {
			val requester = FocusRequester()
			val focused = mutableListOf<Boolean>()
			setContent {
				Box(
					Modifier
						.size(10, 5)
						.focusRequester(requester)
						.focusable()
						.onFocusChanged { focused += it.isFocused },
				)
			}
			awaitSnapshot()

			requester.requestFocus()

			assertThat(focused).isEqualTo(listOf(true))
		}
	}

	@Test fun requesterOnNonFocusableIsIgnored() = runTest {
		runMosaicTest(NodeSnapshots) {
			val requester = FocusRequester()
			val focused = mutableListOf<Boolean>()
			setContent {
				Box(
					Modifier
						.size(10, 5)
						.focusRequester(requester)
						.onFocusChanged { focused += it.isFocused },
				)
			}
			awaitSnapshot()

			requester.requestFocus()

			assertThat(focused).isEmpty()
		}
	}

	@Test fun ancestorObservesDescendantFocus() = runTest {
		runMosaicTest(NodeSnapshots) {
			val container = mutableListOf<Pair<Boolean, Boolean>>()
			val child = mutableListOf<Pair<Boolean, Boolean>>()
			setContent {
				Box(Modifier.size(20, 10).onFocusChanged { container += it.hasFocus to it.isFocused }) {
					Box(Modifier.size(10, 5).focusable().onFocusChanged { child += it.hasFocus to it.isFocused })
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 2))
			root.sendMouseEvent(release(2, 2))

			// Child is focused: it reports hasFocus and isFocused; the container only hasFocus.
			assertThat(child).isEqualTo(listOf(true to true))
			assertThat(container).isEqualTo(listOf(true to false))
		}
	}

	@Test fun ctrlDoesNotTraverse() = runTest {
		runMosaicTest(NodeSnapshots) {
			val a = mutableListOf<Boolean>()
			setContent {
				Box(Modifier.size(10, 5).focusable().onFocusChanged { a += it.isFocused })
			}
			val root = awaitSnapshot()

			// Ctrl+Tab is a raw shortcut, not focus navigation.
			assertThat(root.sendKeyEvent(KeyEvent("Tab", ctrl = true))).isFalse()
			assertThat(a).isEmpty()
		}
	}
}
