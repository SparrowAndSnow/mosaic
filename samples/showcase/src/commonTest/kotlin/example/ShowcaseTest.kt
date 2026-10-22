package example

import assertk.assertThat
import assertk.assertions.contains
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.testing.runMosaicTest
import example.screens.AnimationScreen
import example.screens.BorderScreen
import example.screens.FieldScreen
import example.screens.LazyScreen
import example.screens.ScrollScreen
import example.screens.TextScreen
import example.screens.UnicodeScreen
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Smoke tests for the showcase pages: each page is a plain composable, so it renders through the
 * snapshot harness like any app content. These lock in that the demo shows the refactor's headline
 * behavior — wide/special Unicode and cluster-safe text layout — rather than an empty shell.
 */
class ShowcaseTest {

	@Test fun unicodePageShowsWideAndEmojiContent() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { UnicodeScreen() }
			assertThat(snapshot).contains("你好，世界！")
			assertThat(snapshot).contains("👨\u200D👩\u200D👧\u200D👦")
			assertThat(snapshot).contains("🇨🇳")
			assertThat(snapshot).contains("┌─┐")
		}
	}

	@Test fun textPageShowsAlignmentAndScrollControls() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { TextScreen() }
			assertThat(snapshot).contains("textAlign")
			assertThat(snapshot).contains("第01行")
		}
	}

	@Test fun textPageShowsClipAndEllipsisTruncation() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { TextScreen() }
			assertThat(snapshot).contains("截断")
			assertThat(snapshot).contains("…")
		}
	}

	@Test fun scrollPageShowsScrollingContentWithinViewport() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { ScrollScreen() }
			assertThat(snapshot).contains("纵向滚动")
			assertThat(snapshot).contains("L01")
			assertThat(snapshot).contains("横向滚动")
			assertThat(snapshot).contains("块1")
		}
	}

	@Test fun lazyPageShowsOnlyTheVisibleWindow() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { LazyScreen() }
			assertThat(snapshot).contains("LazyColumn")
			assertThat(snapshot).contains("第  0行")
			assertThat(snapshot).contains("offset 0/500")
		}
	}

	@Test fun fieldPageShowsEditableInputAndInertStates() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { FieldScreen() }
			assertThat(snapshot).contains("BasicTextField")
			assertThat(snapshot).contains("你好，mosaic")
			assertThat(snapshot).contains("只读内容")
			assertThat(snapshot).contains("禁用内容")
		}
	}

	@Test fun lazyPagePageKeysIgnoreAutoRepeat() = runTest {
		runMosaicTest {
			setContentAndSnapshot { LazyScreen() }

			// A single physical press is one page; the keyboard-protocol auto-repeat is swallowed
			// and the key-up (which the runtime drops before composition) must not add anything.
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypePress))
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypeRepeat))
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypeRelease))
			val one = awaitSnapshot().toString()
			assertThat(one).contains("offset 9/500")

			// Flipping to the opposite direction is not a repeat and stays immediate.
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageUp, eventType = KeyboardEvent.EventTypePress))
			val up = awaitSnapshot().toString()
			assertThat(up).contains("offset 0/500")
		}
	}

	@Test fun borderPageShowsBordersAndDividers() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { BorderScreen() }
			assertThat(snapshot).contains("border()")
			assertThat(snapshot).contains("┌")
			assertThat(snapshot).contains("┐")
			assertThat(snapshot).contains("│")
			assertThat(snapshot).contains("水平分隔线")
			assertThat(snapshot).contains("垂直分隔线")
		}
	}

	@Test fun animationPageShowsContainers() = runTest {
		runMosaicTest {
			val snapshot = setContentAndSnapshot { AnimationScreen() }
			assertThat(snapshot).contains("页面推进")
			assertThat(snapshot).contains("展开 / 收起")
			assertThat(snapshot).contains("tick")
		}
	}

	@Test fun overlayPageShowsPopupAndDialogControls() = runTest {
		runMosaicTest {
			setContentAndSnapshot { ShowcaseApp() }

			// Overlay is the second page; the shell's number shortcuts select it.
			sendKeyEvent(KeyboardEvent('2'.code, text = "2"))
			val snapshot = awaitSnapshot().toString()
			assertThat(snapshot).contains("当前页: Overlay")
			assertThat(snapshot).contains("[打开 Popup]")
			assertThat(snapshot).contains("[打开 Dialog]")
			assertThat(snapshot).contains("[点击 base: 0]")
		}
	}

	@Test fun tabBarClickSwitchesPage() = runTest {
		runMosaicTest {
			setContentAndSnapshot { ShowcaseApp() }

			val rows = com.jakewharton.mosaic.terminal.Terminal.Size.Default.rows
			// The tab bar sits on the second-to-last row, just above the hint line. Each tab label
			// is `" $label "` and weighted tabs keep their content width, so the x offset equals the
			// cumulative rendered label length of the preceding tabs.
			val tabBarY = rows - 2
			val target = Page.Text
			val tabStartX = Page.entries.takeWhile { it != target }.sumOf { " ${it.label} ".length }
			val tabCenterX = tabStartX + " ${target.label} ".length / 2

			sendMouseEvent(
				com.jakewharton.mosaic.terminal.MouseEvent(
					x = tabCenterX,
					y = tabBarY,
					type = com.jakewharton.mosaic.terminal.MouseEvent.Type.Press,
					button = com.jakewharton.mosaic.terminal.MouseEvent.Button.Left,
				),
			)
			sendMouseEvent(
				com.jakewharton.mosaic.terminal.MouseEvent(
					x = tabCenterX,
					y = tabBarY,
					type = com.jakewharton.mosaic.terminal.MouseEvent.Type.Release,
					button = com.jakewharton.mosaic.terminal.MouseEvent.Button.Left,
				),
			)

			val after = awaitSnapshot().toString()
			assertThat(after).contains("当前页: Text")
			assertThat(after).contains("第01行")
		}
	}
}
