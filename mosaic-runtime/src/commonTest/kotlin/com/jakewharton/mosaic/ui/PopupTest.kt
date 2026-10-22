package com.jakewharton.mosaic.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.MouseEvent
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.position
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.unit.IntOffset
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PopupTest {

	private fun press(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Press, MouseEvent.Button.Left)

	private fun release(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Release, MouseEvent.Button.Left)

	@Test fun drawsAboveBaseAtAlignmentAndOffset() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Popup(
							alignment = Alignment.TopStart,
							offset = IntOffset(2, 1),
						) {
							Text("X")
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(Modifier.size(10, 5)) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			val host = root.children.single()
			val popup = host.children[1].children.single()
			assertThat(popup.position).isEqualTo(IntOffset(2, 1))

			val rows = root.draw().render(AnsiLevel.NONE, false).split("\n")
			assertThat(rows).isEqualTo(listOf("base", "  X", "", "", ""))
		}
	}

	@Test fun bottomEndAlignmentAndNegativeOffset() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Popup(
							alignment = Alignment.BottomEnd,
							offset = IntOffset(-1, 0),
						) {
							Text("XY")
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(Modifier.size(10, 5))
				}
			}
			val root = awaitSnapshot()

			val host = root.children.single()
			val popup = host.children[1].children.single()
			assertThat(popup.position).isEqualTo(IntOffset(7, 4))

			val rows = root.draw().render(AnsiLevel.NONE, false).split("\n")
			assertThat(rows).isEqualTo(listOf("", "", "", "", "       XY"))
		}
	}

	@Test fun laterLayerDrawsAboveEarlierAndWinsHitTest() = runTest {
		runMosaicTest(NodeSnapshots) {
			var earlier = 0
			var later = 0
			setContent {
				OverlayHost(
					overlay = {
						Popup {
							Box(
								Modifier.size(4, 2).onMouseEvent {
									earlier++
									true
								},
							) {
								Text("AAAA")
							}
						}
						Popup {
							Box(
								Modifier.size(2, 1).onMouseEvent {
									later++
									true
								},
							) {
								Text("BB")
							}
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(Modifier.size(10, 5)) {
						Text("xxxx")
					}
				}
			}
			val root = awaitSnapshot()

			// Later popups are measured, placed, and drawn after earlier ones.
			val rows = root.draw().render(AnsiLevel.NONE, false).split("\n")
			assertThat(rows[0]).isEqualTo("BBAA")

			// The later layer covers (0,0) and is the top-most hit target.
			root.sendMouseEvent(press(0, 0))
			assertThat(later).isEqualTo(1)
			assertThat(earlier).isEqualTo(0)

			// Where only the earlier layer covers, it still receives events.
			root.sendMouseEvent(press(2, 0))
			assertThat(later).isEqualTo(1)
			assertThat(earlier).isEqualTo(1)
		}
	}

	@Test fun clicksInsidePopupAreShieldedFromBase() = runTest {
		runMosaicTest(NodeSnapshots) {
			var baseClicks = 0
			var baseMouse = 0
			setContent {
				OverlayHost(
					overlay = {
						Popup(offset = IntOffset(2, 1)) {
							Text("P")
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(
						Modifier
							.size(10, 5)
							.clickable { baseClicks++ }
							.onMouseEvent {
								baseMouse++
								true
							},
					) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			// Inside the popup: the event is consumed by the layer and the base click does not fire.
			assertThat(root.sendMouseEvent(press(2, 1))).isTrue()
			root.sendMouseEvent(release(2, 1))
			assertThat(baseMouse).isEqualTo(0)
			assertThat(baseClicks).isEqualTo(0)

			// Outside (non-modal): the event falls through and the base receives the click.
			assertThat(root.sendMouseEvent(press(0, 0))).isTrue()
			root.sendMouseEvent(release(0, 0))
			assertThat(baseMouse).isEqualTo(2)
			assertThat(baseClicks).isEqualTo(1)
		}
	}

	@Test fun popupContentReceivesClickBeforeShield() = runTest {
		runMosaicTest(NodeSnapshots) {
			var baseClicks = 0
			var popupClicks = 0
			setContent {
				OverlayHost(
					overlay = {
						Popup(offset = IntOffset(2, 1)) {
							Box(Modifier.size(3, 1).clickable { popupClicks++ }) {
								Text("P")
							}
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(Modifier.size(10, 5).clickable { baseClicks++ }) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 1))
			root.sendMouseEvent(release(2, 1))

			assertThat(popupClicks).isEqualTo(1)
			assertThat(baseClicks).isEqualTo(0)
		}
	}
}
