package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

/**
 * Opt-in state for wheel-driven fling scrolling.
 *
 * A terminal has no frame clock or pointer velocity of its own, so an application that wants a
 * wheel notch to keep the content gliding after the input event must model the motion itself.
 * This state holds that model: [position] is the scroll offset in rows, [velocity] is the current
 * speed in rows per second, and [advance] integrates the two over a frame's worth of time.
 *
 * The state is backed by snapshot state, so reads during composition or layout observe changes and
 * the UI recomposes as the position moves. The class never starts a coroutine or a frame loop:
 * drive [advance] from the application's own frame loop (for example from a frame-clock effect)
 * and translate [position] into layout however the application scrolls.
 *
 * A wheel notch is expressed with [impulse]. Because the velocity decays exponentially with
 * [friction], a notch of N rows travels roughly N rows in total (the initial velocity divided by
 * the friction rate), minus the small tail below [minSpeed] that is discarded. The defaults make a
 * three-row notch travel roughly three rows.
 *
 * @param friction decay rate in inverse seconds. Larger values stop the fling sooner.
 * @param maxSpeed upper bound in rows per second for the velocity produced by [impulse].
 * @param minSpeed velocity magnitude in rows per second below which the fling is considered
 *   finished and the velocity is cleared.
 */
@Stable
public class MomentumScrollState(
	private val friction: Float = 3f,
	private val maxSpeed: Float = 60f,
	private val minSpeed: Float = 0.5f,
) {
	init {
		require(friction > 0f) { "friction must be positive: $friction" }
		require(maxSpeed > 0f) { "maxSpeed must be positive: $maxSpeed" }
		require(minSpeed >= 0f) { "minSpeed must not be negative: $minSpeed" }
	}

	/** The current scroll position in rows. Snapshot state, so changing it invalidates readers. */
	public var position: Float by mutableFloatStateOf(0f)
		private set

	/**
	 * The current scroll velocity in rows per second. Positive values move toward larger
	 * positions, negative values toward smaller ones. Snapshot state, so changing it invalidates
	 * readers.
	 */
	public var velocity: Float by mutableFloatStateOf(0f)
		private set

	/** Whether [velocity] is non-zero, meaning that [advance] still has motion to integrate. */
	public val isMoving: Boolean
		get() = velocity != 0f

	/**
	 * Sets [position] to [index] rows and clears [velocity], ending any fling in progress. The
	 * position is not clamped here because the state does not know the scrollable range; the next
	 * [advance] clamps it to the range passed there.
	 */
	public fun jumpTo(index: Int) {
		position = index.toFloat()
		velocity = 0f
	}

	/**
	 * Adds a wheel notch of [rows] rows to the fling: [velocity] grows by [rows] times [friction],
	 * so the remaining travel is roughly [rows] rows, and is clamped to +/-[maxSpeed]. A negative
	 * value flings toward the start of the content, a positive value toward the end.
	 */
	public fun impulse(rows: Int) {
		if (rows == 0) return
		velocity = (velocity + rows.toFloat() * friction).coerceIn(-maxSpeed, maxSpeed)
	}

	/**
	 * Advances the fling by [dtSeconds] and clamps [position] into the range from zero to
	 * [maxPosition].
	 *
	 * The position moves by [velocity] times [dtSeconds], the velocity then decays by
	 * e^(-friction * dt), and it is cleared once its magnitude falls below [minSpeed]. Hitting
	 * either edge of the range also clears the velocity so the content does not press against the
	 * boundary. [dtSeconds] is capped at 0.05 seconds so a stalled frame cannot teleport the
	 * content, and non-positive values only clamp the position without integrating or clearing the
	 * velocity.
	 *
	 * Returns whether the fling is still moving after this call. Callers should stop advancing
	 * once this returns false. A zero or negative [dtSeconds] does not advance the motion, so an
	 * active fling still reports true and will continue on the next nonzero frame.
	 */
	public fun advance(dtSeconds: Float, maxPosition: Float): Boolean {
		val dt = dtSeconds.coerceIn(0f, MaxAdvanceSeconds)
		val limit = maxPosition.coerceAtLeast(0f)
		if (dt == 0f) {
			position = position.coerceIn(0f, limit)
			return velocity != 0f
		}
		if (velocity == 0f) {
			position = position.coerceIn(0f, limit)
			return false
		}

		val next = position + velocity * dt
		val clamped = next.coerceIn(0f, limit)
		position = clamped
		if (clamped != next) {
			velocity = 0f
			return false
		}

		velocity *= exp(-friction * dt)
		if (abs(velocity) < minSpeed) {
			velocity = 0f
		}
		return velocity != 0f
	}

	override fun toString(): String = "MomentumScrollState(position=$position, velocity=$velocity)"
}

private const val MaxAdvanceSeconds = 0.05f
