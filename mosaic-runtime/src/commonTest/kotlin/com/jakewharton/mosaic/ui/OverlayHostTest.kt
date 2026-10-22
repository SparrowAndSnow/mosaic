package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.size
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class OverlayHostTest {

	@Test fun nullOverlayRendersSameFrameAsContentOnly() = runTest {
		@Composable
		fun Base() {
			Box(Modifier.size(8, 3)) {
				Text("base")
			}
		}

		val contentOnly = runMosaicTest {
			setContent { Base() }
			awaitSnapshot()
		}
		val hosted = runMosaicTest {
			setContent {
				OverlayHost(overlay = null) {
					Base()
				}
			}
			awaitSnapshot()
		}

		assertThat(hosted).isEqualTo(contentOnly)
	}

	@Test fun overlaySlotMatchesBaseSizeAndDoesNotResizeHost() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Popup {
							Text("a much wider popup")
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Text("base")
				}
			}
			val root = awaitSnapshot()

			val host = root.children.single()
			assertThat(host.size).isEqualTo(IntSize(10, 5))

			val base = host.children[0]
			assertThat(base.size).isEqualTo(IntSize(4, 1))

			val slot = host.children[1]
			assertThat(slot.size).isEqualTo(IntSize(10, 5))
		}
	}

	@Test fun zeroSizeBaseYieldsZeroSizeOverlay() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Popup {
							Text("popup")
						}
					},
				) {
					Spacer(Modifier.size(0, 0))
				}
			}
			val root = awaitSnapshot()

			val host = root.children.single()
			assertThat(host.size).isEqualTo(IntSize(0, 0))

			val slot = host.children[1]
			assertThat(slot.size).isEqualTo(IntSize(0, 0))

			assertThat(root.draw().render(AnsiLevel.NONE, false)).isEqualTo("")
		}
	}

	@Test fun nestedHostThrows() = runTest {
		runMosaicTest {
			val failure = assertFailsWith<IllegalStateException> {
				setContent {
					OverlayHost {
						OverlayHost {
							Text("nested")
						}
					}
				}
			}
			assertThat(failure.message!!).contains("Only one OverlayHost")
		}
	}

	@Test fun secondHostThrows() = runTest {
		runMosaicTest {
			val failure = assertFailsWith<IllegalStateException> {
				setContent {
					Column {
						OverlayHost {
							Text("first")
						}
						OverlayHost {
							Text("second")
						}
					}
				}
			}
			assertThat(failure.message!!).contains("Only one OverlayHost")
		}
	}

	@Test fun nonModalPopupOffersKeysBeforeBaseAndFallsThrough() = runTest {
		runMosaicTest(NodeSnapshots) {
			val received = mutableListOf<String>()
			setContent {
				OverlayHost(
					overlay = {
						Popup {
							Box(
								Modifier.onKeyEvent {
									received += "popup"
									false
								},
							)
						}
					},
				) {
					Box(
						Modifier.onKeyEvent {
							received += "base"
							true
						},
					) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			root.sendKeyEvent(KeyEvent("x"))

			// Non-modal layers are offered keys first; unhandled events fall through to the base.
			assertThat(received).isEqualTo(listOf("popup", "base"))
		}
	}
}
