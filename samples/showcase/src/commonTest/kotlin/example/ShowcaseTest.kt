package example

import assertk.assertThat
import assertk.assertions.contains
import com.jakewharton.mosaic.testing.runMosaicTest
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
}
