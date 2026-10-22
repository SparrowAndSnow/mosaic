package com.jakewharton.mosaic.ui

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.AnsiIncrementalRendering
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.layout.MouseEvent
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.MouseEvent as TerminalMouseEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TabRowTest {

	private fun press(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Press, MouseEvent.Button.Left)

	private fun release(x: Int, y: Int) = MouseEvent(x, y, MouseEvent.Type.Release, MouseEvent.Button.Left)

	private fun motion(x: Int, y: Int) = TerminalMouseEvent(x, y, TerminalMouseEvent.Type.Motion)

	private fun truecolor() = RenderingSnapshots(
		AnsiIncrementalRendering(TestTerminal.Capabilities()),
	)

	@Test fun rendersLabelsDividerAndTrailingContent() = runTest {
		runMosaicTest {
			setContent {
				TabRow(
					tabs = listOf("One", "Two"),
					selectedIndex = 0,
					onSelect = {},
					modifier = Modifier.width(40),
					trailing = {
						Spacer(Modifier.weight(1f))
						Text("hint")
					},
				)
			}
			// Fixed tabs occupy 11 cells; the weighted spacer takes the remaining 25 before the
			// four-cell hint, proving the trailing slot participates in Row weighting.
			assertThat(awaitSnapshot()).isEqualTo(" One │ Two " + " ".repeat(25) + "hint")
		}
	}

	@Test fun selectedAndUnselectedTabsUseTheirColors() = runTest {
		val colors = TabRowColors(
			container = Color(60, 70, 80),
			selectedBackground = Color(200, 10, 20),
			selectedContent = Color(30, 40, 50),
			content = Color(90, 100, 110),
			hoverBackground = Color(120, 130, 140),
			divider = Color(150, 160, 170),
		)
		runMosaicTest(truecolor()) {
			setContent {
				TabRow(
					tabs = listOf("One", "Two"),
					selectedIndex = 1,
					onSelect = {},
					colors = colors,
				)
			}
			val frame = awaitSnapshot().toString()

			// The selected second tab paints its background and content colors.
			assertThat(frame).contains("48;2;200;10;20")
			assertThat(frame).contains("38;2;30;40;50")
			// The unselected first tab paints the container background and normal content color.
			assertThat(frame).contains("48;2;60;70;80")
			assertThat(frame).contains("38;2;90;100;110")
			// The divider is styled independently of both tabs.
			assertThat(frame).contains("38;2;150;160;170")
		}
	}

	@Test fun clickingSecondTabCallsOnSelectWithItsIndex() = runTest {
		runMosaicTest(NodeSnapshots) {
			val selected = mutableListOf<Int>()
			setContent {
				TabRow(
					tabs = listOf("One", "Two"),
					selectedIndex = 0,
					onSelect = { selected += it },
				)
			}
			val root = awaitSnapshot()

			// " Two " starts after " One " and the divider, at columns 6..10.
			root.sendMouseEvent(press(7, 0))
			root.sendMouseEvent(release(7, 0))

			assertThat(selected).isEqualTo(listOf(1))
		}
	}

	@Test fun clickingSelectedTabStillCallsOnSelect() = runTest {
		runMosaicTest(NodeSnapshots) {
			val selected = mutableListOf<Int>()
			setContent {
				TabRow(
					tabs = listOf("One", "Two"),
					selectedIndex = 0,
					onSelect = { selected += it },
				)
			}
			val root = awaitSnapshot()

			root.sendMouseEvent(press(2, 0))
			root.sendMouseEvent(release(2, 0))

			assertThat(selected).isEqualTo(listOf(0))
		}
	}

	@Test fun hoveringNonSelectedTabUsesHoverBackgroundAndLeaveRestoresContainer() = runTest {
		val colors = TabRowColors(
			container = Color(60, 70, 80),
			selectedBackground = Color(200, 10, 20),
			selectedContent = Color(30, 40, 50),
			content = Color(90, 100, 110),
			hoverBackground = Color(120, 130, 140),
		)
		runMosaicTest(truecolor()) {
			setContent {
				TabRow(
					tabs = listOf("A", "B"),
					selectedIndex = 0,
					onSelect = {},
					colors = colors,
				)
			}
			val initial = awaitSnapshot().toString()
			assertThat(initial).contains("48;2;60;70;80")

			// " B " spans columns 4..6, after " A " and the divider.
			sendMouseEvent(motion(5, 0))
			val hovered = awaitSnapshot().toString()
			assertThat(hovered).contains("48;2;120;130;140")

			// Moving off every tab reports leave on the next pointer event.
			sendMouseEvent(motion(20, 0))
			val left = awaitSnapshot().toString()
			assertThat(left).contains("48;2;60;70;80")
		}
	}

	@Test fun nullDividerRendersTabsAdjacent() = runTest {
		runMosaicTest {
			setContent {
				TabRow(
					tabs = listOf("A", "B"),
					selectedIndex = 0,
					onSelect = {},
					modifier = Modifier.width(10),
					divider = null,
				)
			}
			assertThat(awaitSnapshot()).isEqualTo(" A  B")
		}
	}

	@Test fun emptyTabsRenderOnlyTrailingContent() = runTest {
		runMosaicTest {
			setContent {
				TabRow(
					tabs = emptyList(),
					selectedIndex = 3,
					onSelect = {},
					modifier = Modifier.width(10),
					trailing = { Text("empty") },
				)
			}
			// An out-of-range selection selects nothing and no divider is drawn without tabs.
			assertThat(awaitSnapshot()).isEqualTo("empty")
		}
	}
}
