package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Layout
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.unit.IntOffset
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class LayoutCacheTest {

	@Test fun stateChangeInOneSubtreeDoesNotRemeasureSibling() = runTest {
		runMosaicTest(NodeSnapshots) {
			val probeState = mutableIntStateOf(1)

			setContent {
				Row {
					// Read only during measurement (never during composition), so the only
					// invalidation path available is the measured-dependency check.
					Layout(
						content = {},
						measurePolicy = { _, _ ->
							layout(probeState.intValue, 1) {}
						},
					)
					Box(Modifier.size(2, 1))
				}
			}

			val row = awaitSnapshot().children[0]
			val probeNode = row.children[0]
			val siblingNode = row.children[1]

			val probeMeasured = probeNode.measureCount
			val siblingMeasured = siblingNode.measureCount
			val rowMeasured = row.measureCount

			probeState.intValue = 5

			awaitSnapshot()

			// The probe (and its ancestor chain) re-measures because it read the changed state.
			assertThat(probeNode.measureCount).isEqualTo(probeMeasured + 1)
			assertThat(row.measureCount).isEqualTo(rowMeasured + 1)
			// The unaffected sibling is served from the layout cache.
			assertThat(siblingNode.measureCount).isEqualTo(siblingMeasured)
			assertThat(siblingNode.cacheHitCount).isGreaterThan(0L)
			// And the new probe size is reflected.
			assertThat(probeNode.width).isEqualTo(5)
		}
	}

	@Test fun insertingSiblingDoesNotRemeasureExistingChildren() = runTest {
		runMosaicTest(NodeSnapshots) {
			var showSecond by mutableStateOf(false)

			setContent {
				Row {
					Box(Modifier.size(1, 2))
					if (showSecond) {
						Box(Modifier.size(1, 2))
					}
				}
			}

			val row = awaitSnapshot().children[0]
			val firstChild = row.children[0]

			val rowMeasured = row.measureCount
			val firstMeasured = firstChild.measureCount

			showSecond = true

			awaitSnapshot()

			// The row must re-measure to accommodate the new child...
			assertThat(row.measureCount).isEqualTo(rowMeasured + 1)
			// ...but the unchanged existing child's measurement is reused.
			assertThat(firstChild.measureCount).isEqualTo(firstMeasured)
			assertThat(row.children.size).isEqualTo(2)
		}
	}

	@Test fun placementOnlyStateChangeSkipsMeasurement() = runTest {
		runMosaicTest(NodeSnapshots) {
			var offsetX by mutableIntStateOf(0)

			setContent {
				Box(Modifier.width(10)) {
					Box(Modifier.size(1).offset { IntOffset(offsetX, 0) })
					Box(Modifier.size(1))
				}
			}

			val root = awaitSnapshot()
			val box = root.children[0]
			val offsetChild = box.children[0]
			val sibling = box.children[1]

			val offsetMeasured = offsetChild.measureCount
			val siblingMeasured = sibling.measureCount
			val rootCacheHits = root.cacheHitCount

			// Only placement reads this state; sizes are unaffected.
			offsetX = 5

			awaitSnapshot()

			// A layout pass still runs (so placement recomputes from the new value), which is
			// observable as an additional root cache hit, but no subtree is actually re-measured.
			assertThat(offsetChild.measureCount).isEqualTo(offsetMeasured)
			assertThat(sibling.measureCount).isEqualTo(siblingMeasured)
			assertThat(root.cacheHitCount).isGreaterThan(rootCacheHits)
		}
	}

	@Test fun constraintChangeInvalidatesCache() = runTest {
		runMosaicTest(NodeSnapshots) {
			var width by mutableIntStateOf(5)

			setContent {
				Box(Modifier.width(width)) {
					Layout(
						content = {},
						measurePolicy = { _, constraints ->
							layout(constraints.maxWidth.coerceAtMost(3), 1) {}
						},
					)
				}
			}

			val box = awaitSnapshot().children[0]
			val child = box.children[0]

			val childMeasured = child.measureCount

			width = 10

			awaitSnapshot()

			// Wider constraints force the child to re-measure (3 columns, not 5).
			assertThat(child.measureCount).isEqualTo(childMeasured + 1)
			assertThat(child.width).isEqualTo(3)
		}
	}
}
