package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.Layout
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Intrinsic queries walk a whole subtree, but the result is stable for the same (kind, parameter)
 * while nothing structurally changes. [MosaicNode] caches each query in a single slot gated by the
 * layout version, so repeated queries neither re-walk the subtree nor return values made stale by
 * a state change.
 */
class IntrinsicCacheTest {
	private class Counter {
		var value = 0
	}

	@Test fun repeatedSameParamQueriesRecomputeOnce() = runTest {
		runMosaicTest(NodeSnapshots) {
			val childCalls = Counter()
			val perMeasure = mutableListOf<Int>()
			val parentPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult {
						val child = measurables.first()
						val before = childCalls.value
						child.minIntrinsicWidth(7)
						child.minIntrinsicWidth(7)
						child.minIntrinsicWidth(7)
						perMeasure += childCalls.value - before
						return layout(0, 0) {}
					}
				}
			val childPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult = layout(0, 0) {}

					override fun minIntrinsicWidth(
						measurables: List<IntrinsicMeasurable>,
						height: Int,
					): Int {
						childCalls.value++
						return height
					}
				}
			setContent {
				Layout(
					content = { Layout(content = {}, measurePolicy = childPolicy) },
					measurePolicy = parentPolicy,
				)
			}

			awaitSnapshot()

			// Each parent measure runs three identical queries, but the underlying intrinsic is
			// computed exactly once (memo hits answer the second and third).
			assertThat(perMeasure).isNotEmpty()
			perMeasure.forEach { assertThat(it).isEqualTo(1) }
		}
	}

	@Test fun differentParamRecomputes() = runTest {
		runMosaicTest(NodeSnapshots) {
			val childCalls = Counter()
			val perMeasure = mutableListOf<Int>()
			val parentPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult {
						val child = measurables.first()
						val before = childCalls.value
						child.minIntrinsicWidth(7)
						child.minIntrinsicWidth(8)
						perMeasure += childCalls.value - before
						return layout(0, 0) {}
					}
				}
			val childPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult = layout(0, 0) {}

					override fun minIntrinsicWidth(
						measurables: List<IntrinsicMeasurable>,
						height: Int,
					): Int {
						childCalls.value++
						return height
					}
				}
			setContent {
				Layout(
					content = { Layout(content = {}, measurePolicy = childPolicy) },
					measurePolicy = parentPolicy,
				)
			}

			awaitSnapshot()

			// Distinct parameters are not interchangeable: both queries must recompute.
			assertThat(perMeasure).isNotEmpty()
			perMeasure.forEach { assertThat(it).isEqualTo(2) }
		}
	}

	@Test fun stateChangeInvalidatesMemo() = runTest {
		runMosaicTest(NodeSnapshots) {
			var value by mutableStateOf(10)
			val childPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult = layout(1, 1) {}

					override fun minIntrinsicWidth(
						measurables: List<IntrinsicMeasurable>,
						height: Int,
					) = value
				}
			val parentPolicy =
				object : MeasurePolicy {
					override fun MeasureScope.measure(
						measurables: List<Measurable>,
						constraints: Constraints,
					): MeasureResult {
						// The child's intrinsic (not its measure) is the only thing that reads
						// `value`; the memo must not serve a value made stale by changing it.
						val w = measurables.first().minIntrinsicWidth(5)
						return layout(w, 1) {}
					}
				}
			setContent {
				Layout(
					content = { Layout(content = {}, measurePolicy = childPolicy) },
					measurePolicy = parentPolicy,
				)
			}

			awaitSnapshot().let { root ->
				assertThat(root.children[0].width).isEqualTo(10)
			}

			value = 20

			awaitSnapshot().let { root ->
				assertThat(root.children[0].width).isEqualTo(20)
			}
		}
	}
}
