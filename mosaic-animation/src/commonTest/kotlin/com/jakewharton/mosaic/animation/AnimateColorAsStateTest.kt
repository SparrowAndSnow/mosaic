package com.jakewharton.mosaic.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.testing.TestMosaic
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest

/** Verifies the fire-and-forget `animate*AsState` family converges onto its target. */
class AnimateColorAsStateTest {

	@Test fun colorAnimatesThroughOklabMidGrayAndSettlesExactly() = runTest {
		runMosaicTest {
			var target by mutableStateOf(Color.Black)
			var finishedCount = 0
			setContentAndSnapshot {
				val color = animateColorAsState(target, tween(2000), finishedListener = { finishedCount++ })
				Text(color.value.toString())
			}

			target = Color.White
			val frames = pump(100)
			// The in-flight animation passes through the Oklab mid-gray (~99, 99, 99). A naive
			// sRGB lerp would hit (128, 128, 128) and a linear-RGB one (188, 188, 188).
			assertThat(frames.map(::rgb).any { (r, g, b) -> r in 90..110 && g == r && b == r }).isTrue()

			val settled = settle(fallback = frames.last())
			assertThat(settled).contains("Color(255, 255, 255)")
			// The finished listener fires exactly once after the animation completes.
			assertThat(finishedCount).isEqualTo(1)
		}
	}

	@Test fun colorRetargetsMidAnimation() = runTest {
		runMosaicTest {
			var target by mutableStateOf(Color.Black)
			setContentAndSnapshot {
				val color = animateColorAsState(target, tween(1000))
				Text(color.value.toString())
			}

			// Retarget before the first animation has finished: the in-flight animation should
			// change course instead of finishing at the old target.
			target = Color.Red
			pump(12)
			target = Color.Blue
			val settled = settle()
			assertThat(settled).contains("Color(0, 0, 255)")
		}
	}

	@Test fun floatAsStateAnimatesAndSettles() = runTest {
		runMosaicTest {
			var target by mutableFloatStateOf(0f)
			var finishedCount = 0
			setContentAndSnapshot {
				val value = animateFloatAsState(target, tween(1000), finishedListener = { finishedCount++ })
				Text(value.value.toString())
			}

			target = 1f
			val frames = pump(40)
			// The animation passes through intermediate values before converging on the target.
			assertThat(frames.any { it != "0.0" && it != "1.0" }).isTrue()

			val settled = settle(fallback = frames.last())
			assertThat(settled).contains("1.0")
			assertThat(finishedCount).isEqualTo(1)
		}
	}

	@Test fun intAsStateAnimatesAndSettles() = runTest {
		runMosaicTest {
			var target by mutableIntStateOf(0)
			var finishedCount = 0
			setContentAndSnapshot {
				val value = animateIntAsState(target, tween(1000), finishedListener = { finishedCount++ })
				Text(value.value.toString())
			}

			target = 10
			val frames = pump(40)
			assertThat(frames.any { it != "0" && it != "10" }).isTrue()

			val settled = settle(fallback = frames.last())
			assertThat(settled).contains("10")
			assertThat(finishedCount).isEqualTo(1)
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

private fun rgb(frame: String): Triple<Int, Int, Int> {
	val start = frame.indexOf("Color(") + "Color(".length
	val end = frame.indexOf(')', start)
	val parts = frame.substring(start, end).split(", ")
	return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}
