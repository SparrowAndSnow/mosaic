package example

import assertk.assertThat
import assertk.assertions.contains
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.terminal.KeyboardEvent
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

	@Test fun lazyPagePageKeysIgnoreAutoRepeat() = runTest {
		runMosaicTest {
			setContentAndSnapshot { LazyScreen() }

			// A single physical press followed by the auto-repeat burst a hold would generate must
			// land on exactly one page, not stack several pages.
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypePress))
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypeRepeat))
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageDown, eventType = KeyboardEvent.EventTypeRepeat))
			val one = awaitSnapshot().toString()
			assertThat(one).contains("offset 9/500")

			// Flipping to the opposite direction is not a repeat and stays immediate.
			sendKeyEvent(KeyboardEvent(KeyboardEvent.PageUp, eventType = KeyboardEvent.EventTypePress))
			val up = awaitSnapshot().toString()
			assertThat(up).contains("offset 0/500")
		}
	}
}
