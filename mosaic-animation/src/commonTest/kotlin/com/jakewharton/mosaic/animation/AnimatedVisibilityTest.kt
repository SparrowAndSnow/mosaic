package com.jakewharton.mosaic.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isTrue
import com.jakewharton.mosaic.testing.TestMosaic
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest

/** Verifies enter/exit animation of [com.jakewharton.mosaic.animation.AnimatedVisibility]. */
class AnimatedVisibilityTest {

	@Test fun hiddenInitialStateRendersNothing() = runTest {
		runMosaicTest {
			var visible by mutableStateOf(false)
			val snapshot = setContentAndSnapshot {
				AnimatedVisibility(visible) { Text("HELLO") }
			}
			assertThat(snapshot).doesNotContain("HELLO")
		}
	}

	@Test fun visibleShowsContentImmediately() = runTest {
		runMosaicTest {
			var visible by mutableStateOf(true)
			val snapshot = setContentAndSnapshot {
				AnimatedVisibility(visible, enter = EnterTransition.None, exit = ExitTransition.None) {
					Text("HELLO")
				}
			}
			assertThat(snapshot).contains("HELLO")
		}
	}

	@Test fun entranceAnimatesOverFramesThenSettles() = runTest {
		runMosaicTest {
			var visible by mutableStateOf(false)
			setContentAndSnapshot {
				AnimatedVisibility(visible, enter = expandVertically(tween(400))) {
					Column {
						Text("R1")
						Text("R2")
						Text("R3")
					}
				}
			}

			visible = true
			// The vertical expansion passes through several distinct partial-size frames.
			val frames = pump(20)
			assertThat(frames.distinct().size > 2).isTrue()
			assertThat(frames.any { it.contains("R1") }).isTrue()

			val settled = settle()
			assertThat(settled).contains("R1")
			assertThat(settled).contains("R2")
			assertThat(settled).contains("R3")
		}
	}

	@Test fun exitKeepsContentDuringAnimationThenRemovesIt() = runTest {
		runMosaicTest {
			var visible by mutableStateOf(true)
			setContentAndSnapshot {
				AnimatedVisibility(visible, enter = EnterTransition.None, exit = shrinkVertically(tween(400))) {
					Column {
						Text("R1")
						Text("R2")
						Text("R3")
					}
				}
			}
			settle()

			visible = false
			// While the exit animation runs the content is still composed (shrinking).
			val mid = pump(12)
			assertThat(mid.any { it.contains("R1") }).isTrue()

			val settled = settle()
			assertThat(settled).doesNotContain("R1")
			assertThat(settled).doesNotContain("R2")
		}
	}

	@Test fun expansionHasMultipleFrames() = runTest {
		runMosaicTest {
			var visible by mutableStateOf(false)
			setContentAndSnapshot {
				AnimatedVisibility(visible, enter = expandVertically(tween(400))) {
					Column {
						Text("R1")
						Text("R2")
						Text("R3")
					}
				}
			}
			visible = true
			assertThat(pump(20).distinct().size > 2).isTrue()
		}
	}

	@Test fun mutableTransitionStateStartsAnimated() = runTest {
		runMosaicTest {
			val visibleState = MutableTransitionState(false)
			// Start hidden but target visible, so the enter animation runs from the first frames.
			visibleState.targetState = true
			setContentAndSnapshot {
				AnimatedVisibility(visibleState, enter = expandVertically(tween(400))) {
					Column {
						Text("R1")
						Text("R2")
					}
				}
			}
			val settled = settle()
			assertThat(settled).contains("R1")
			assertThat(settled).contains("R2")
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
