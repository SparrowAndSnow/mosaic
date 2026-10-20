package com.jakewharton.mosaic.animation

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.math.abs
import kotlin.test.Test

/**
 * Locks the [TwoWayConverter] table backing the `animate*AsState` family. Round trips must be
 * lossless enough for animation convergence, and color interpolation must run in a perceptually
 * uniform space rather than a naive per-channel lerp.
 */
class VectorConvertersTest {

	@Test fun floatRoundTripsThroughAnimationVector() {
		val converter = Float.VectorConverter
		val value = 42.5f
		assertThat(converter.convertFromVector(converter.convertToVector(value))).isEqualTo(value)
	}

	@Test fun intRoundTripsThroughAnimationVector() {
		val converter = Int.VectorConverter
		val value = 300
		assertThat(converter.convertFromVector(converter.convertToVector(value))).isEqualTo(value)
	}

	@Test fun intOffsetRoundTripsThroughAnimationVector() {
		val converter = IntOffset.VectorConverter
		val value = IntOffset(-4, 12)
		assertThat(converter.convertFromVector(converter.convertToVector(value))).isEqualTo(value)
	}

	@Test fun intSizeRoundTripsAndClampsNegatives() {
		val converter = IntSize.VectorConverter
		assertThat(converter.convertFromVector(converter.convertToVector(IntSize(80, 24))))
			.isEqualTo(IntSize(80, 24))

		// Negative vector components convert back to zero size rather than negative dimensions.
		val size = converter.convertFromVector(AnimationVector2D(-3f, -8f))
		assertThat(size.width).isEqualTo(0)
		assertThat(size.height).isEqualTo(0)
	}

	@Test fun colorRoundTripsWithinTolerance() {
		val converter = Color.VectorConverter
		for (color in listOf(
			Color.Black,
			Color.White,
			Color.Red,
			Color.Green,
			Color.Blue,
			Color(0x12, 0x34, 0x56),
			Color(200, 40, 90),
		)) {
			assertThat(closeTo(color, converter.convertFromVector(converter.convertToVector(color))))
				.isTrue()
		}
	}

	@Test fun colorMidpointLerpsInPerceptualSpaceNotNaiveChannels() {
		val converter = Color.VectorConverter
		// Interpolating black -> white in the animation vector space reaches Oklab L=0.5 midway.
		val midpoint = converter.convertFromVector(AnimationVector3D(0.5f, 0f, 0f))
		val (r, g, b) = midpoint
		// The Oklab midpoint maps to sRGB ~ (99, 99, 99). A naive sRGB lerp would give (128, 128, 128)
		// and a linear-RGB lerp (188, 188, 188); the dark mid-gray is the perceptually even one.
		assertThat(r * 255f).isBetween(90f, 108f)
		assertThat(g * 255f).isBetween(90f, 108f)
		assertThat(b * 255f).isBetween(90f, 108f)
	}

	private fun closeTo(actual: Color, expected: Color, tolerance: Float = 3f): Boolean {
		val (ar, ag, ab) = actual
		val (er, eg, eb) = expected
		return abs(ar - er) * 255f < tolerance &&
			abs(ag - eg) * 255f < tolerance &&
			abs(ab - eb) * 255f < tolerance
	}
}
