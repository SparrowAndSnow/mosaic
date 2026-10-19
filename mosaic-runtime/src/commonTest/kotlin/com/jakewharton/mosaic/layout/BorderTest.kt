package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.AnsiIncrementalRendering
import com.jakewharton.mosaic.assertFailure
import com.jakewharton.mosaic.size
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BorderTest {

	private fun linesOf(frame: String): List<String> = frame.lines().map { it.trimEnd() }

	@Test fun borderWrapsContentAndInsetsByOne() = runTest {
		runMosaicTest {
			setContent {
				Box(Modifier.border()) { Text("ab") }
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|┌──┐
				|│ab│
				|└──┘
				""".trimMargin(),
			)
		}
	}

	@Test fun borderWrapsSingleCharContent() = runTest {
		runMosaicTest {
			setContent {
				Box(Modifier.border()) { Text("a") }
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|┌─┐
				|│a│
				|└─┘
				""".trimMargin(),
			)
		}
	}

	@Test fun borderZeroRendersNothingAndKeepsSize() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.border(0)) { Text("ab") }
			}
			val root = awaitSnapshot()
			assertThat(root.size.width).isEqualTo(2)
			assertThat(root.size.height).isEqualTo(1)
		}
		runMosaicTest {
			setContent {
				Box(Modifier.border(0)) { Text("ab") }
			}
			assertThat(awaitSnapshot()).isEqualTo("ab")
		}
	}

	@Test fun borderTwoFillsSolidEdges() = runTest {
		runMosaicTest {
			setContent {
				Box(Modifier.size(10, 5).border(2)) { Text("ab") }
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|┌────────┐
				|││──────││
				|││ab    ││
				|││──────││
				|└────────┘
				""".trimMargin(),
			)
		}
	}

	@Test fun borderSpansFullWidthWithFillMaxWidth() = runTest {
		runMosaicTest {
			setContent {
				Column(Modifier.width(8)) {
					Box(Modifier.fillMaxWidth().border()) { Text("a") }
				}
			}
			assertThat(linesOf(awaitSnapshot())).isEqualTo(
				listOf(
					"┌──────┐",
					"│a     │",
					"└──────┘",
				),
			)
		}
	}

	@Test fun borderComposesWithPaddingOutside() = runTest {
		runMosaicTest {
			setContent {
				Box(Modifier.padding(1).border()) { Text("ab") }
			}
			assertThat(linesOf(awaitSnapshot())).isEqualTo(
				listOf(
					"",
					" ┌──┐",
					" │ab│",
					" └──┘",
					"",
				),
			)
		}
	}

	@Test fun borderColorEmitsAnsiForeground() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(TestTerminal.Capabilities()))) {
			setContent {
				Box(Modifier.border(color = Color.Red)) { Text("ab") }
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).contains("38;2;255;0;0")
		}
	}

	@Test fun borderUnspecifiedColorHasNoForegroundSgr() = runTest {
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(TestTerminal.Capabilities()))) {
			setContent {
				Box(Modifier.border()) { Text("ab") }
			}
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("38;2")
			assertThat(frame).doesNotContain("31m")
		}
	}

	@Test fun borderKeepsInnerClickableWorking() = runTest {
		runMosaicTest(NodeSnapshots) {
			var clicks = 0
			setContent {
				Box(Modifier.padding(1).border()) {
					Box(Modifier.size(6, 3).clickable { clicks++ }) { Text("C") }
				}
			}
			val root = awaitSnapshot()

			// The clickable box starts at root (2,2) and spans 6x3; a press+release well inside
			// it must still register even though its parent draws a border.
			root.sendMouseEvent(MouseEvent(4, 3, MouseEvent.Type.Press, MouseEvent.Button.Left))
			assertThat(clicks).isEqualTo(0)
			root.sendMouseEvent(MouseEvent(4, 3, MouseEvent.Type.Release, MouseEvent.Button.Left))
			assertThat(clicks).isEqualTo(1)

			// Outside the border the click is not delivered.
			root.sendMouseEvent(MouseEvent(20, 20, MouseEvent.Type.Press, MouseEvent.Button.Left))
			root.sendMouseEvent(MouseEvent(20, 20, MouseEvent.Type.Release, MouseEvent.Button.Left))
			assertThat(clicks).isEqualTo(1)
		}
	}

	@Test fun horizontalDividerSpansWidthAndIsThick() = runTest {
		runMosaicTest {
			setContent {
				Column(Modifier.width(8)) {
					HorizontalDivider()
					Text("ab")
				}
			}
			assertThat(linesOf(awaitSnapshot())).isEqualTo(
				listOf(
					"────────",
					"ab",
				),
			)
		}
	}

	@Test fun horizontalDividerThickness() = runTest {
		runMosaicTest {
			setContent {
				Column(Modifier.width(8)) {
					HorizontalDivider(thickness = 2)
					Text("ab")
				}
			}
			assertThat(linesOf(awaitSnapshot())).isEqualTo(
				listOf(
					"────────",
					"────────",
					"ab",
				),
			)
		}
	}

	@Test fun horizontalDividerHonorsIndents() = runTest {
		runMosaicTest {
			setContent {
				Column(Modifier.width(8)) {
					HorizontalDivider(startIndent = 1, endIndent = 2)
				}
			}
			assertThat(linesOf(awaitSnapshot())).isEqualTo(
				listOf(" ─────"),
			)
		}
	}

	@Test fun verticalDividerSpansHeight() = runTest {
		runMosaicTest {
			setContent {
				Row(Modifier.height(4)) {
					Text("ab")
					VerticalDivider()
					Text("cd")
				}
			}
			val lines = linesOf(awaitSnapshot())
			assertThat(lines[0]).isEqualTo("ab│cd")
			assertThat(lines.count { it.getOrNull(2) == '│' }).isEqualTo(4)
		}
	}

	@Test fun verticalDividerHonorsIndents() = runTest {
		runMosaicTest {
			setContent {
				Row(Modifier.height(4)) {
					VerticalDivider(startIndent = 1, endIndent = 1)
				}
			}
			val lines = linesOf(awaitSnapshot())
			assertThat(lines[0]).isEqualTo("")
			assertThat(lines[1]).isEqualTo("│")
			assertThat(lines[2]).isEqualTo("│")
			assertThat(lines[3]).isEqualTo("")
		}
	}

	@Test fun borderRequiresNonNegativeWidth() {
		assertFailure<IllegalArgumentException> {
			Modifier.border(-1)
		}
	}

	@Test fun dividerRequiresNonNegativeArguments() = runTest {
		runMosaicTest {
			assertFailure<IllegalArgumentException> {
				setContent {
					HorizontalDivider(thickness = -1)
				}
			}
		}
	}
}
