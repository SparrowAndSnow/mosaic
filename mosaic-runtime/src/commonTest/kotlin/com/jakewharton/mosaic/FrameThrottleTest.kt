package com.jakewharton.mosaic

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Locks in the L7 frame pacing: the frame producer must not spin at ~1000fps. Work presence is
 * decided by [MosaicComposition.hasPendingFrameWork]; a frame awaiting animation keeps the loop at
 * ~60fps while an idle composition must not re-draw on every tick.
 */
class FrameThrottleTest {

	@Test fun frameWorkPlannerIdleAtRest() = runTest {
		val clock = BroadcastFrameClock()
		val mosaic = MosaicComposition(
			coroutineContext = coroutineContext + clock,
			onDraw = {},
			terminal = TestTerminal(),
		)
		try {
			assertThat(mosaic.hasPendingFrameWork()).isFalse()
		} finally {
			mosaic.cancel()
		}
	}

	@Test
	fun animationAwaitingFramesKeepsHighFrameRate() = runBlocking {
		val done = CompletableDeferred<Long>()
		val composition = launch {
			runMosaicComposition(
				terminal = TestTerminal(),
				rendering = AnsiIncrementalRendering(TestTerminal.Capabilities()),
				content = { FrameRateProbe(done) },
			)
		}

		// 30 animation frames at ~60fps take ~0.5s; if the loop wrongly idled at ~20fps it would
		// take ~1.5s. The generous bound still separates the two pacing tiers with slack for GC.
		val elapsedMs = withTimeout(5.seconds) { done.await() }
		composition.cancel()

		assertThat(elapsedMs).isLessThan(1200)
	}

	@Test
	fun staticContentDoesNotRedrawOnIdleFrames() = runBlocking {
		val terminal = TestTerminal()
		var renders = 0
		val counting = object : Rendering {
			override fun render(mosaic: Mosaic): CharSequence {
				renders++
				return ""
			}
		}
		val composition = launch {
			runMosaicComposition(terminal, counting) { Text("static") }
		}

		yield()
		delay(400.milliseconds)
		composition.cancel()

		// Only the initial frame draws; the idle frames that keep ticking must not trigger redraws.
		assertThat(renders).isEqualTo(1)
	}

	@Test
	fun animationStartingAfterIdleKeepsHighFrameRate() = runBlocking {
		val startAnimation = CompletableDeferred<Unit>()
		val done = CompletableDeferred<Long>()
		val composition = launch {
			runMosaicComposition(
				terminal = TestTerminal(),
				rendering = AnsiIncrementalRendering(TestTerminal.Capabilities()),
				content = { IdleThenFrameRateProbe(startAnimation, done) },
			)
		}

		// Let the composition sit idle (the loop drops to ~20fps) before the animation starts.
		yield()
		delay(200.milliseconds)
		startAnimation.complete(Unit)

		// 30 frames at ~60fps take ~0.5s even with one trailing idle tick; if the idle loop failed
		// to switch back to active pacing this would take ~1.5s. The generous bound separates tiers
		// with slack for GC.
		val elapsedMs = withTimeout(5.seconds) { done.await() }
		composition.cancel()

		assertThat(elapsedMs).isLessThan(1400)
	}

}

@Composable
private fun FrameRateProbe(done: CompletableDeferred<Long>) {
	LaunchedEffect(done) {
		val start = TimeSource.Monotonic.markNow()
		repeat(30) {
			withFrameNanos { }
		}
		done.complete(start.elapsedNow().inWholeMilliseconds)
	}
}

@Composable
private fun IdleThenFrameRateProbe(startAnimation: CompletableDeferred<Unit>, done: CompletableDeferred<Long>) {
	LaunchedEffect(startAnimation) {
		// Stay dormant until the caller lets the idle phase elapse, then measure the frame rate.
		startAnimation.await()
		val start = TimeSource.Monotonic.markNow()
		repeat(30) {
			withFrameNanos { }
		}
		done.complete(start.elapsedNow().inWholeMilliseconds)
	}
}

