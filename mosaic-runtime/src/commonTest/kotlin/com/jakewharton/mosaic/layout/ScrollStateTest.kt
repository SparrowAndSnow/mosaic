package com.jakewharton.mosaic.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

/** Unit behavior of [ScrollState] independent of any layout: clamping and consumed deltas. */
class ScrollStateTest {
	@Test fun startsAtInitialValueWithNoMax() {
		val state = ScrollState(initial = 2)
		assertThat(state.value).isEqualTo(2)
		assertThat(state.maxValue).isEqualTo(0)
	}

	@Test fun scrollToClampsWithinMaxValue() {
		val state = ScrollState().apply { maxValue = 10 }
		state.scrollTo(4)
		assertThat(state.value).isEqualTo(4)

		state.scrollTo(99)
		assertThat(state.value).isEqualTo(10)

		state.scrollTo(-3)
		assertThat(state.value).isEqualTo(0)
	}

	@Test fun scrollByClampsAndReportsConsumed() {
		val state = ScrollState().apply { maxValue = 10 }
		state.scrollTo(3)

		assertThat(state.scrollBy(4)).isEqualTo(4)
		assertThat(state.value).isEqualTo(7)

		// At the end: consumes only what remains.
		assertThat(state.scrollBy(100)).isEqualTo(3)
		assertThat(state.value).isEqualTo(10)

		// At/near the start again.
		state.scrollTo(1)
		assertThat(state.scrollBy(-100)).isEqualTo(-1)
		assertThat(state.value).isEqualTo(0)

		// No movement past bounds.
		assertThat(state.scrollBy(-5)).isEqualTo(0)
		assertThat(state.value).isEqualTo(0)
	}

	@Test fun maxValueZeroPinsValue() {
		val state = ScrollState(initial = 5).apply { maxValue = 0 }
		state.scrollTo(2)
		assertThat(state.value).isEqualTo(0)
		assertThat(state.scrollBy(1)).isEqualTo(0)
	}
}
