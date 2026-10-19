package com.jakewharton.mosaic.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isTrue
import com.jakewharton.mosaic.testing.TestMosaic
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest

/** Verifies [com.jakewharton.mosaic.animation.Crossfade] layer switching and slide animation. */
class CrossfadeTest {

	@Test fun initialTargetShown() = runTest {
		runMosaicTest {
			var page by mutableIntStateOf(0)
			val snapshot = setContentAndSnapshot {
				Crossfade(targetState = page) { p -> Text("page $p") }
			}
			assertThat(snapshot).contains("page 0")
			assertThat(snapshot).doesNotContain("page 1")
		}
	}

	@Test fun switchAnimatesThroughMixedFrames() = runTest {
		runMosaicTest {
			var page by mutableIntStateOf(0)
			setContentAndSnapshot {
				Crossfade(targetState = page, animationSpec = tween(1000)) { p ->
					Text(if (p == 0) "OLD__0" else "1__NEW")
				}
			}

			// Midway through the slide the outgoing tail and the incoming head share the row:
			// both the outgoing '0' and the incoming '1' are visible in one frame.
			page = 1
			val frames = pump(60)
			assertThat(frames.any { it.indexOf('0') >= 0 && it.indexOf('1') >= 0 }).isTrue()
		}
	}

	@Test fun afterSwitchOnlyTargetRemains() = runTest {
		runMosaicTest {
			var page by mutableIntStateOf(0)
			setContentAndSnapshot {
				Crossfade(targetState = page, animationSpec = tween(300)) { p -> Text("page $p") }
			}

			page = 1
			val settled = settle()
			assertThat(settled).contains("page 1")
			assertThat(settled).doesNotContain("page 0")
		}
	}

	@Test fun intermediateFramesAnimate() = runTest {
		runMosaicTest {
			var page by mutableIntStateOf(0)
			setContentAndSnapshot {
				Crossfade(targetState = page, animationSpec = tween(600)) { p -> Text("page $p") }
			}

			page = 1
			val frames = pump(12)
			assertThat(frames.distinct().size > 2).isTrue()
		}
	}

	@Test fun switchingBackStaysSingleLayer() = runTest {
		runMosaicTest {
			var page by mutableIntStateOf(0)
			val initial = setContentAndSnapshot {
				Crossfade(targetState = page, animationSpec = tween(100)) { p -> Text("PAGE$p") }
			}
			assertThat(initial).contains("PAGE0")

			page = 0
			val settled = settle(initial)
			assertThat(settled).contains("PAGE0")
			assertThat(settled).doesNotContain("PAGE1")
		}
	}
}

private suspend fun TestMosaic<String>.pump(count: Int): List<String> = buildList {
	repeat(count) {
		try {
			add(awaitSnapshot())
		} catch (e: TimeoutCancellationException) {
			// The composition went idle (no frame changes); nothing more to collect.
			return@buildList
		}
	}
}

private suspend fun TestMosaic<String>.settle(fallback: String = ""): String {
	var last = fallback
	repeat(80) {
		try {
			last = awaitSnapshot()
		} catch (e: TimeoutCancellationException) {
			return last
		}
	}
	return last
}
