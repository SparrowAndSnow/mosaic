package com.jakewharton.mosaic.layout

import androidx.compose.runtime.snapshots.Snapshot
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Unit behavior of [MomentumScrollState] without a frame loop or any layout. */
class MomentumScrollStateTest {
	@Test fun defaultWheelNotchTravelsAboutThreeRowsThenStops() {
		val state = MomentumScrollState()
		state.impulse(MouseWheelLines)

		var moving = true
		var frames = 0
		while (moving && frames < 10_000) {
			moving = state.advance(1f / 60f, maxPosition = 1_000f)
			frames++
		}

		assertThat(moving).isFalse()
		assertThat(state.isMoving).isFalse()
		assertThat(state.velocity).isEqualTo(0f)
		// The discarded tail below the minimum speed leaves a small remainder.
		assertThat(state.position).isCloseTo(MouseWheelLines.toFloat(), 0.2f)
	}

	@Test fun upwardImpulseAtStartClampsAndStops() {
		val state = MomentumScrollState()
		state.impulse(-3)

		assertThat(state.isMoving).isTrue()
		assertThat(state.advance(1f / 60f, maxPosition = 100f)).isFalse()
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.velocity).isEqualTo(0f)
		assertThat(state.isMoving).isFalse()
	}

	@Test fun impulseCapsAtMaxSpeed() {
		val state = MomentumScrollState()

		state.impulse(100)
		assertThat(state.velocity).isEqualTo(60f)

		state.impulse(-100)
		assertThat(state.velocity).isEqualTo(-60f)
	}

	@Test fun impulsesAccumulateAndStayWithinMaxSpeed() {
		val state = MomentumScrollState()

		state.impulse(3)
		assertThat(state.velocity).isEqualTo(9f)
		state.impulse(3)
		assertThat(state.velocity).isEqualTo(18f)
		state.impulse(-3)
		assertThat(state.velocity).isEqualTo(9f)
		state.impulse(100)
		assertThat(state.velocity).isEqualTo(60f)
	}

	@Test fun zeroImpulseDoesNothing() {
		val state = MomentumScrollState()

		state.impulse(0)

		assertThat(state.position).isEqualTo(0f)
		assertThat(state.velocity).isEqualTo(0f)
		assertThat(state.isMoving).isFalse()
	}

	@Test fun stateDoesNotMoveUntilAdvanced() {
		val state = MomentumScrollState()

		state.impulse(MouseWheelLines)

		// The state owns no coroutine or frame loop: without advance() the position cannot move.
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.isMoving).isTrue()
	}

	@Test fun jumpToClearsVelocity() {
		val state = MomentumScrollState()
		state.impulse(3)
		assertThat(state.isMoving).isTrue()

		state.jumpTo(42)

		assertThat(state.position).isEqualTo(42f)
		assertThat(state.velocity).isEqualTo(0f)
		assertThat(state.isMoving).isFalse()
	}

	@Test fun jumpToIsNotClampedUntilAdvance() {
		val state = MomentumScrollState()

		state.jumpTo(500)
		assertThat(state.position).isEqualTo(500f)

		assertThat(state.advance(0f, maxPosition = 100f)).isFalse()
		assertThat(state.position).isEqualTo(100f)
		assertThat(state.velocity).isEqualTo(0f)
	}

	@Test fun advanceWithoutMotionClampsPositionAndStops() {
		val state = MomentumScrollState()

		state.jumpTo(50)
		assertThat(state.advance(1f / 60f, maxPosition = 20f)).isFalse()
		assertThat(state.position).isEqualTo(20f)

		state.jumpTo(-5)
		assertThat(state.advance(1f / 60f, maxPosition = 20f)).isFalse()
		assertThat(state.position).isEqualTo(0f)
	}

	@Test fun zeroDeltaTimeKeepsTheFlingAlive() {
		val state = MomentumScrollState()
		state.impulse(3)

		assertThat(state.advance(0f, maxPosition = 100f)).isTrue()
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.isMoving).isTrue()

		// Negative time is treated like zero: clamp only, never integrate backwards.
		assertThat(state.advance(-1f, maxPosition = 100f)).isTrue()
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.isMoving).isTrue()
	}

	@Test fun advanceStopsAtTheEndBoundary() {
		val state = MomentumScrollState()
		state.jumpTo(99)
		state.impulse(100)
		assertThat(state.velocity).isEqualTo(60f)

		assertThat(state.advance(0.05f, maxPosition = 100f)).isFalse()
		assertThat(state.position).isEqualTo(100f)
		assertThat(state.velocity).isEqualTo(0f)
		assertThat(state.isMoving).isFalse()
	}

	@Test fun zeroMaxPositionClampsAndStops() {
		val state = MomentumScrollState()
		state.impulse(3)

		assertThat(state.advance(1f / 60f, maxPosition = 0f)).isFalse()
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.velocity).isEqualTo(0f)
	}

	@Test fun negativeMaxPositionBehavesAsZero() {
		val state = MomentumScrollState()
		state.impulse(-3)

		assertThat(state.advance(1f / 60f, maxPosition = -10f)).isFalse()
		assertThat(state.position).isEqualTo(0f)
		assertThat(state.velocity).isEqualTo(0f)
	}

	@Test fun oversizedDeltaTimeIsCappedAtFiftyMilliseconds() {
		val state = MomentumScrollState()
		state.impulse(1)

		val moving = state.advance(dtSeconds = 1f, maxPosition = 1_000f)

		assertThat(moving).isTrue()
		assertThat(state.position).isCloseTo(0.15f, 0.0001f)
		assertThat(state.velocity).isCloseTo(3f * exp(-0.15f), 0.0001f)
	}

	@Test fun invalidParametersAreRejected() {
		assertFailsWith<IllegalArgumentException> { MomentumScrollState(friction = 0f) }
		assertFailsWith<IllegalArgumentException> { MomentumScrollState(friction = -1f) }
		assertFailsWith<IllegalArgumentException> { MomentumScrollState(maxSpeed = 0f) }
		assertFailsWith<IllegalArgumentException> { MomentumScrollState(maxSpeed = -1f) }
		assertFailsWith<IllegalArgumentException> { MomentumScrollState(minSpeed = -0.1f) }
	}

	@Test fun positionAndVelocityAreSnapshotState() {
		val state = MomentumScrollState()
		val reads = mutableListOf<Any>()

		Snapshot.observe(readObserver = reads::add) {
			state.position
			state.velocity
		}

		assertThat(reads.isNotEmpty()).isTrue()
	}

	@Test fun applicationFrameLoopMapsPositionToLayout() {
		val state = MomentumScrollState()
		state.impulse(MouseWheelLines)

		// The application owns the loop and maps the physical position onto its layout offset.
		val offsets = mutableListOf<Int>()
		var frames = 0
		while (state.isMoving && frames < 600) {
			state.advance(1f / 60f, maxPosition = 1_000f)
			offsets += state.position.roundToInt()
			frames++
		}

		assertThat(frames > 1).isTrue()
		assertThat(offsets.any { it > 0 }).isTrue()
		assertThat(offsets.last()).isEqualTo(MouseWheelLines)
		for (index in 1 until offsets.size) {
			assertThat(offsets[index] >= offsets[index - 1]).isTrue()
		}
	}
}
