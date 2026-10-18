package com.jakewharton.mosaic

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isPositive
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Locks in the L7 lifecycle semantics of [Mosaic.awaitComplete]: it is a run-until-idle barrier
 * that only returns once every composition effect has finished and the owed layout/draw pass has
 * been applied, while [Mosaic.cancel] must always release a waiter even while an effect is still
 * running. It also pins the standard repeated-`setContent` behavior: the new content takes over and
 * any effects launched by the previous content are cancelled.
 */
class LifecycleSemanticsTest {

	/** A live composition driven by a ~60fps frame pump, exposing the [MosaicComposition] directly. */
	private class Harness(
		val terminal: TestTerminal,
		val clock: BroadcastFrameClock,
		val mosaic: MosaicComposition,
		val pump: Job,
	) {
		fun cancel() {
			pump.cancel()
			mosaic.cancel()
		}
	}

	private fun CoroutineScope.createHarness(
		terminal: TestTerminal = TestTerminal(),
	): Harness {
		val clock = BroadcastFrameClock()
		val mosaic = MosaicComposition(
			coroutineContext = this.coroutineContext + clock,
			onDraw = {},
			terminal = terminal,
		)
		val pump = launch {
			while (true) {
				clock.sendFrame(nanoTime())
				// Let continuations resumed by the frame (animated effects, the recomposer) run and
				// re-register before the next frame, mirroring the production frame loop's pacing.
				yield()
				delay(16.milliseconds)
			}
		}
		return Harness(terminal, clock, mosaic, pump)
	}

	@Test fun infiniteEffectKeepsAwaitCompleteBlockedUntilCancel() = runBlocking {
		val harness = createHarness()
		try {
			harness.mosaic.setContent { Ticker(); Text("alive") }

			val completed = CompletableDeferred<Unit>()
			val awaiter = launch {
				harness.mosaic.awaitComplete()
				completed.complete(Unit)
			}

			// While the infinite effect (a ticker) is running, awaitComplete must not return; only
			// cancel(), which tears the effect down, may release it.
			delay(100.milliseconds)
			assertThat(withTimeoutOrNull(200.milliseconds) { completed.await() }).isNull()

			harness.mosaic.cancel()
			withTimeout(3.seconds) { completed.await() }
			awaiter.join()
		} finally {
			harness.cancel()
		}
	}

	@Test fun awaitCompleteReturnsOnceAllEffectsFinish() = runBlocking {
		val harness = createHarness()
		try {
			harness.mosaic.setContent { Text("static") }

			// Static content owns no effects, so run-until-idle means awaiting the initial apply and
			// idle settle before returning on its own, without any explicit cancel.
			withTimeout(3.seconds) { harness.mosaic.awaitComplete() }
		} finally {
			harness.cancel()
		}
	}

	@Test fun secondSetContentReplacesContentAndCancelsOldEffects() = runBlocking {
		val harness = createHarness()
		try {
			val frames = mutableMapOf<String, Int>()
			harness.mosaic.setContent { FrameCounter("A", frames) }

			delay(120.milliseconds) // Let A's per-frame effect accumulate some runs.
			assertThat(frames["A"] ?: 0).isPositive()

			harness.mosaic.setContent { FrameCounter("B", frames) }
			delay(120.milliseconds) // B has replaced A, so B's effect should be running.
			assertThat(frames["B"] ?: 0).isPositive()
			val aAtSwitch = frames["A"] ?: 0

			// A was disposed by the content replacement: its counter must be frozen, not growing.
			delay(120.milliseconds)
			assertThat(frames["A"] ?: 0).isEqualTo(aAtSwitch)

			harness.mosaic.cancel()
		} finally {
			harness.cancel()
		}
	}
}

/** A ticker effect that never completes, keeping the composition alive until cancelled. */
@Composable
private fun Ticker() {
	LaunchedEffect(Unit) {
		while (true) {
			withFrameNanos { }
		}
	}
}

/** A per-frame effect that records how many frames ran for the given content key. */
@Composable
private fun FrameCounter(name: String, frames: MutableMap<String, Int>) {
	LaunchedEffect(name) {
		while (true) {
			withFrameNanos {
				frames[name] = (frames[name] ?: 0) + 1
			}
		}
	}
}
