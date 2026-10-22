package com.jakewharton.mosaic.layout

/**
 * Pairs presses on the same index to detect double clicks.
 *
 * The tracker is a small, dependency-free helper for callers that already receive raw mouse
 * events, such as rows that drive selection from an onMouseEvent callback. It keeps only the
 * previous press, so it is cheap to hold next to the state it describes and it starts no timers:
 * the caller supplies the clock.
 *
 * Call [press] for every press with the index under the pointer (a row number, for example) and a
 * millisecond timestamp from a clock that normally moves forward, such as `nanoTime() / 1_000_000`.
 * A `true` result means that the same index was pressed again within [doubleClickMillis] of the
 * previous press, so the caller can treat it as a double click. A first press, a press on a
 * different index, and a press outside the window all return `false` and become the reference
 * point for the next call.
 *
 * Because the window is measured from the previous press rather than from the first press of a
 * pair, a third quick press also returns `true`; callers that care can count consecutive
 * matches themselves.
 *
 * @param doubleClickMillis the inclusive upper bound, in milliseconds, between two presses on the
 *   same index for them to count as a double click. Must not be negative.
 */
public class ClickTracker(
	private val doubleClickMillis: Long = 400L,
) {
	init {
		require(doubleClickMillis >= 0L) {
			"doubleClickMillis must not be negative: $doubleClickMillis"
		}
	}

	private var lastIndex: Int = 0
	private var lastMillis: Long = 0L
	private var hasLastPress: Boolean = false

	/**
	 * Records a press on [index] at [nowMillis] and reports whether it completes a double click.
	 *
	 * Returns `true` when the same [index] was pressed before and the elapsed time is within
	 * `0..doubleClickMillis`. A negative elapsed time, meaning that the clock moved backwards,
	 * never matches: the press is still recorded, so the next press is measured from it.
	 *
	 * @param index the index that was pressed, such as a row number.
	 * @param nowMillis the press time in milliseconds, normally from a monotonic clock.
	 */
	public fun press(index: Int, nowMillis: Long): Boolean {
		val elapsed = nowMillis - lastMillis
		val isDoubleClick = hasLastPress &&
			index == lastIndex &&
			elapsed >= 0L &&
			elapsed <= doubleClickMillis
		lastIndex = index
		lastMillis = nowMillis
		hasLastPress = true
		return isDoubleClick
	}

	/**
	 * Forgets the previous press, so the next call to [press] always returns `false`.
	 *
	 * Use this when the pointer leaves the tracked content or when the content changes under it,
	 * so a stale press cannot pair with a press on unrelated content.
	 */
	public fun reset() {
		hasLastPress = false
	}
}
