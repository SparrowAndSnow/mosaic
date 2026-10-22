package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Unit behavior of [ClickTracker], with timestamps supplied by the test. */
class ClickTrackerTest {
	@Test fun sameIndexWithinWindowIsDoubleClick() {
		val tracker = ClickTracker()

		assertThat(tracker.press(index = 2, nowMillis = 1_000L)).isFalse()
		// The upper bound is inclusive.
		assertThat(tracker.press(index = 2, nowMillis = 1_400L)).isTrue()
		// The window is measured from the previous press, not from the first one.
		assertThat(tracker.press(index = 2, nowMillis = 1_800L)).isTrue()
	}

	@Test fun pressOnDifferentIndexIsNotDoubleClick() {
		val tracker = ClickTracker()

		assertThat(tracker.press(index = 0, nowMillis = 1_000L)).isFalse()
		assertThat(tracker.press(index = 1, nowMillis = 1_100L)).isFalse()
		// The second press became the reference point for the different index.
		assertThat(tracker.press(index = 1, nowMillis = 1_200L)).isTrue()
	}

	@Test fun pressOutsideWindowIsNotDoubleClick() {
		val tracker = ClickTracker(doubleClickMillis = 400L)

		assertThat(tracker.press(index = 3, nowMillis = 1_000L)).isFalse()
		assertThat(tracker.press(index = 3, nowMillis = 1_401L)).isFalse()
		// The late press is the new reference point.
		assertThat(tracker.press(index = 3, nowMillis = 1_500L)).isTrue()
	}

	@Test fun pressWithBackwardsClockIsNotDoubleClick() {
		val tracker = ClickTracker()

		assertThat(tracker.press(index = 4, nowMillis = 1_000L)).isFalse()
		assertThat(tracker.press(index = 4, nowMillis = 900L)).isFalse()
		// The backwards press is recorded, so the next press is measured from it.
		assertThat(tracker.press(index = 4, nowMillis = 950L)).isTrue()
	}

	@Test fun resetForgetsPreviousPress() {
		val tracker = ClickTracker()

		assertThat(tracker.press(index = 5, nowMillis = 1_000L)).isFalse()
		tracker.reset()
		assertThat(tracker.press(index = 5, nowMillis = 1_050L)).isFalse()
	}

	@Test fun negativeWindowIsRejected() {
		assertFailsWith<IllegalArgumentException> {
			ClickTracker(doubleClickMillis = -1L)
		}
	}
}
