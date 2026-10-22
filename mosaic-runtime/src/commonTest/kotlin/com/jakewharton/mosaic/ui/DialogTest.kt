package com.jakewharton.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.TextSurface
import com.jakewharton.mosaic.layout.FocusRequester
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.MouseEvent
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.focusRequester
import com.jakewharton.mosaic.layout.focusable
import com.jakewharton.mosaic.layout.hoverable
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onPaste
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.layout.testTag
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.nodeByTestTag
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.runMosaicTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class DialogTest {

	private fun press(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Press, MouseEvent.Button.Left)
	private fun release(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Release, MouseEvent.Button.Left)
	private fun motion(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Motion)

	private fun click(root: com.jakewharton.mosaic.layout.MosaicNode, x: Int, y: Int) {
		root.sendMouseEvent(press(x, y))
		root.sendMouseEvent(release(x, y))
	}

	@Test fun modalDialogBlocksKeyAndPasteFromBase() = runTest {
		runMosaicTest(NodeSnapshots) {
			val received = mutableListOf<String>()
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Box(
								Modifier
									.onKeyEvent {
										received += "dialog"
										true
									}
									.onPaste {
										received += "paste-dialog"
										true
									},
							)
						}
					},
				) {
					Box(
						Modifier
							.onKeyEvent {
								received += "base"
								true
							}
							.onPaste {
								received += "paste-base"
								true
							},
					) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("x"))).isTrue()
			assertThat(root.sendPasteEvent("pasted")).isTrue()
			assertThat(received).isEqualTo(listOf("dialog", "paste-dialog"))
		}
	}

	@Test fun dialogEscapeDismissesWhenContentDoesNotConsume() = runTest {
		runMosaicTest(NodeSnapshots) {
			var dismisses = 0
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = { dismisses++ }) {
							Text("dialog")
						}
					},
				) {
					Text("base")
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("Escape"))).isTrue()
			assertThat(dismisses).isEqualTo(1)
		}
	}

	@Test fun dialogEscapeCanBeConsumedByContent() = runTest {
		runMosaicTest(NodeSnapshots) {
			var dismisses = 0
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = { dismisses++ }) {
							Box(Modifier.onKeyEvent { it.key == "Escape" })
						}
					},
				) {
					Text("base")
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("Escape"))).isTrue()
			assertThat(dismisses).isEqualTo(0)
		}
	}

	@Test fun backdropClickIgnoresContentAndPressCrossings() = runTest {
		runMosaicTest(NodeSnapshots) {
			var dismisses = 0
			var baseClicks = 0
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = { dismisses++ }) {
							Box(Modifier.size(4, 3))
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

			// The 4x3 dialog surface is centered at (3, 1) within the 10x5 base.
			click(root, 0, 0)
			assertThat(dismisses).isEqualTo(1)
			assertThat(baseClicks).isEqualTo(0)

			// Clicks on the dialog surface never reach the backdrop, even without a handler.
			click(root, 4, 2)
			assertThat(dismisses).isEqualTo(1)

			// Press inside, release outside: no backdrop click.
			root.sendMouseEvent(press(4, 2))
			root.sendMouseEvent(release(0, 0))
			assertThat(dismisses).isEqualTo(1)

			// Press outside, release inside: no backdrop click either.
			root.sendMouseEvent(press(0, 0))
			root.sendMouseEvent(release(4, 2))
			assertThat(dismisses).isEqualTo(1)

			assertThat(baseClicks).isEqualTo(0)
		}
	}

	@Test fun noneBackdropLeavesBaseVisible() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(
							onDismissRequest = {},
							backdrop = OverlayBackdrop.None,
						) {
							Text("D")
						}
					},
					modifier = Modifier.size(10, 3),
				) {
					Box(Modifier.size(10, 3)) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			val rows = root.draw().render(AnsiLevel.NONE, false).split("\n")
			assertThat(rows[0]).isEqualTo("base")
		}
	}

	@Test fun solidBackdropClearsBaseCharactersAndStyles() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Text("D")
						}
					},
					modifier = Modifier.size(10, 3),
				) {
					Box(Modifier.size(10, 3)) {
						Text(
							"base",
							color = Color.Green,
							background = Color.Red,
							underlineStyle = UnderlineStyle.Straight,
							underlineColor = Color.Blue,
						)
					}
				}
			}
			val root = awaitSnapshot()
			val surface = root.draw() as TextSurface

			// (0,0) held the base 'b' with a red background and a blue underline; the default
			// backdrop erases all of it back to the terminal default.
			val cleared = surface.cells[0]
			assertThat(cleared.isEmpty()).isTrue()
			assertThat(cleared.background == Color.Unspecified).isTrue()
			assertThat(cleared.underlineStyle == UnderlineStyle.Unspecified).isTrue()
		}
	}

	@Test fun solidBackdropWithColorPaintsBackground() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(
							onDismissRequest = {},
							backdrop = OverlayBackdrop.Solid(Color.Red),
						) {
							Text("D")
						}
					},
					modifier = Modifier.size(10, 3),
				) {
					Box(Modifier.size(10, 3)) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()
			val cell = (root.draw() as TextSurface).cells[0]

			assertThat(cell.isEmpty()).isFalse()
			assertThat(cell.background == Color.Red).isTrue()
			assertThat(cell.foreground == Color.Unspecified).isTrue()
			assertThat(cell.underlineStyle == UnderlineStyle.Unspecified).isTrue()
		}
	}

	@Test fun dialogSavesAndRestoresBaseFocus() = runTest {
		runMosaicTest(NodeSnapshots) {
			var show by mutableStateOf(false)
			val requester = FocusRequester()
			setContent {
				OverlayHost(
					overlay = {
						if (show) {
							Dialog(onDismissRequest = {}) {
								Text("dialog")
							}
						}
					},
				) {
					Box(
						Modifier
							.focusable()
							.focusRequester(requester)
							.testTag("base"),
					) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()
			val base = root.nodeByTestTag("base")
			requester.requestFocus()
			assertThat(root.ensureFocusManager().focusedNode).isEqualTo(base)

			show = true
			val openRoot = awaitSnapshot()
			assertThat(openRoot.ensureFocusManager().focusedNode).isNull()

			show = false
			val closedRoot = awaitSnapshot()
			assertThat(closedRoot.ensureFocusManager().focusedNode).isEqualTo(base)
		}
	}

	@Test fun dialogDoesNotRestoreRemovedFocusTarget() = runTest {
		runMosaicTest(NodeSnapshots) {
			var showBase by mutableStateOf(true)
			var showDialog by mutableStateOf(false)
			val requester = FocusRequester()
			setContent {
				OverlayHost(
					overlay = {
						if (showDialog) {
							Dialog(onDismissRequest = {}) {
								Text("dialog")
							}
						}
					},
				) {
					if (showBase) {
						Box(
							Modifier
								.focusable()
								.focusRequester(requester)
								.testTag("base"),
						) {
							Text("base")
						}
					}
				}
			}
			val root = awaitSnapshot()
			requester.requestFocus()
			assertThat(root.ensureFocusManager().focusedNode).isEqualTo(root.nodeByTestTag("base"))

			showDialog = true
			awaitSnapshot()

			showBase = false
			awaitSnapshot()

			showDialog = false
			val closedRoot = awaitSnapshot()
			assertThat(closedRoot.ensureFocusManager().focusedNode).isNull()
		}
	}

	@Test fun tabTraversalStaysInsideModalScope() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Box(Modifier.focusable().testTag("b")) { Text("B") }
							Box(Modifier.focusable().testTag("c")) { Text("C") }
						}
					},
				) {
					Box(Modifier.focusable().testTag("base")) { Text("base") }
				}
			}
			val root = awaitSnapshot()
			val b = root.nodeByTestTag("b")
			val c = root.nodeByTestTag("c")

			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(root.ensureFocusManager().focusedNode).isEqualTo(b)
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(root.ensureFocusManager().focusedNode).isEqualTo(c)
			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(root.ensureFocusManager().focusedNode).isEqualTo(b)
		}
	}

	@Test fun tabIsConsumedWhenModalHasNoFocusable() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Text("dialog")
						}
					},
				) {
					Box(Modifier.focusable().testTag("base")) { Text("base") }
				}
			}
			val root = awaitSnapshot()

			assertThat(root.sendKeyEvent(KeyEvent("Tab"))).isTrue()
			assertThat(root.ensureFocusManager().focusedNode).isNull()
		}
	}

	@Test fun openingModalCancelsPressAndClearsHover() = runTest {
		runMosaicTest(NodeSnapshots) {
			var show by mutableStateOf(false)
			var clicks = 0
			val hovered = mutableListOf<Boolean>()
			setContent {
				OverlayHost(
					overlay = {
						if (show) {
							Dialog(onDismissRequest = {}) {
								Text("dialog")
							}
						}
					},
					modifier = Modifier.size(10, 5),
				) {
					Box(
						Modifier
							.size(10, 5)
							.clickable { clicks++ }
							.hoverable { hovered += it },
					) {
						Text("base")
					}
				}
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(motion(1, 1))
			root.sendMouseEvent(press(1, 1))
			assertThat(hovered).isEqualTo(listOf(true))

			show = true
			awaitSnapshot()
			assertThat(hovered).isEqualTo(listOf(true, false))

			root.sendMouseEvent(release(1, 1))
			assertThat(clicks).isEqualTo(0)
		}
	}

	@Test fun laterModalLayerReceivesKeysFirstAndBarrierMovesDown() = runTest {
		runMosaicTest(NodeSnapshots) {
			var first by mutableStateOf(true)
			var second by mutableStateOf(true)
			val received = mutableListOf<String>()
			setContent {
				OverlayHost(
					overlay = {
						if (first) {
							Dialog(onDismissRequest = {}) {
								Box(
									Modifier.onKeyEvent {
										received += "first"
										true
									},
								)
							}
						}
						if (second) {
							Dialog(onDismissRequest = {}) {
								Box(
									Modifier.onKeyEvent {
										received += "second"
										true
									},
								)
							}
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
			assertThat(received).isEqualTo(listOf("second"))

			second = false
			awaitSnapshot()
			root.sendKeyEvent(KeyEvent("x"))
			assertThat(received).isEqualTo(listOf("second", "first"))

			first = false
			awaitSnapshot()
			root.sendKeyEvent(KeyEvent("x"))
			assertThat(received).isEqualTo(listOf("second", "first", "base"))
		}
	}
}
