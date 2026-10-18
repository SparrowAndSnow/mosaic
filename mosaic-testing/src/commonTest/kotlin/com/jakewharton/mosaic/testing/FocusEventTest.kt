package com.jakewharton.mosaic.testing

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * [TestMosaic.sendFocusEvent] must behave like a real focus event: flipping the terminal focus
 * pushes the composition's lifecycle owner through ON_PAUSE/ON_RESUME so effects watching the
 * lifecycle can pause and resume.
 */
class FocusEventTest {

	@Test fun focusLossAndGainDriveCompositionLifecycle() = runTest {
		runMosaicTest {
			var currentState by mutableStateOf(Lifecycle.State.INITIALIZED)
			setContent {
				val lifecycle = LocalLifecycleOwner.current.lifecycle
				DisposableEffect(lifecycle) {
					val observer = LifecycleEventObserver { _, _ ->
						currentState = lifecycle.currentState
					}
					lifecycle.addObserver(observer)
					onDispose { lifecycle.removeObserver(observer) }
				}
				Text("focused")
			}

			// The test terminal starts focused, so the composition begins resumed.
			assertThat(currentState).isEqualTo(Lifecycle.State.RESUMED)

			sendFocusEvent(false)
			delay(10.milliseconds)
			assertThat(currentState).isEqualTo(Lifecycle.State.STARTED)

			sendFocusEvent(true)
			delay(10.milliseconds)
			assertThat(currentState).isEqualTo(Lifecycle.State.RESUMED)
		}
	}
}
