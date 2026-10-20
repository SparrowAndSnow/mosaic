package com.jakewharton.mosaic.ui

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.runMosaicTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Row/Column must size and place weighted children by the slot they are granted, not by each
 * child's content size. A layout that does not itself expand, such as a content-width [Text] under
 * [Modifier.weight] with `fill = true` (the default), would otherwise make the container shrink to
 * the sum of the content widths and pack siblings by content, collapsing equal-column layouts.
 */
class WeightFillSemanticsTest {
	@Test fun rowTextNoWeightWrapsByContent() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.width(100)) {
					Row {
						Text("A")
						Text("BBB")
						Text("CCCCC")
					}
				}
			}
			// No weights: each text reports its own content width and the row wraps.
			val row = awaitSnapshot().children[0].children[0]
			assertThat(row.width).isEqualTo(1 + 3 + 5)
			row.children.forEachIndexed { index, node ->
				assertThat(node.width).isEqualTo(listOf(1, 3, 5)[index])
			}
		}
	}

	@Test fun rowTextWeightFillPlacesByEqualSlots() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.width(100)) {
					Row(Modifier.fillMaxWidth()) {
						Text("A", modifier = Modifier.weight(1f))
						Text("BBB", modifier = Modifier.weight(1f))
						Text("CCCCC", modifier = Modifier.weight(1f))
					}
				}
			}
			val row = awaitSnapshot().children[0].children[0]
			// The row is accounted for by the full width of its equal slots (not 1 + 3 + 5) ...
			assertThat(row.width).isEqualTo(100)
			// ... and siblings are placed back-to-back by slot, so each slot's clickable region is
			// a full 1/3 column. 100/3 rounds to 33 each; the leftover 1 lands on the first slot.
			assertThat(row.children[0].x).isEqualTo(0)
			assertThat(row.children[1].x).isEqualTo(34)
			assertThat(row.children[2].x).isEqualTo(67)
			// The children themselves keep their content size (no visible stretching of the label).
			assertThat(row.children[0].width).isEqualTo(1)
			assertThat(row.children[1].width).isEqualTo(3)
			assertThat(row.children[2].width).isEqualTo(5)
			// Each slot covers the gap between its predecessor and the following sibling.
			row.children.windowed(2).forEach { (a, b) ->
				assertThat(b.x - a.x).isGreaterThan(a.width)
			}
		}
	}

	@Test fun rowTextWeightNonFillKeepsContentWidth() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.width(100)) {
					Row(Modifier.fillMaxWidth()) {
						Text("A", modifier = Modifier.weight(1f, fill = false))
						Text("BBB", modifier = Modifier.weight(1f, fill = false))
					}
				}
			}
			val row = awaitSnapshot().children[0].children[0]
			// fill = false: children keep and are placed by their content width.
			assertThat(row.children[0].width).isEqualTo(1)
			assertThat(row.children[1].width).isEqualTo(3)
			assertThat(row.children[1].x).isEqualTo(1)
		}
	}

	@Test fun mixedWeightedAndFixedRow() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.width(80)) {
					Row(Modifier.fillMaxWidth()) {
						Spacer(Modifier.width(6))
						Text("a", modifier = Modifier.weight(1f))
						Text("b", modifier = Modifier.weight(2f))
					}
				}
			}
			val row = awaitSnapshot().children[0].children[0]
			assertThat(row.width).isEqualTo(80)
			// Fixed child keeps its size; remaining 74 splits 1:2 (rounding up on the odd slot).
			assertThat(row.children[0].width).isEqualTo(6)
			assertThat(row.children[0].x).isEqualTo(0)
			assertThat(row.children[1].x).isEqualTo(6)
			assertThat(row.children[2].x).isEqualTo(31)
		}
	}

	@Test fun columnTextWeightFillPlacesByEqualSlots() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.height(60)) {
					Column(Modifier.fillMaxWidth()) {
						Text("h", modifier = Modifier.weight(1f))
						Text("hh", modifier = Modifier.weight(1f))
					}
				}
			}
			val column = awaitSnapshot().children[0].children[0]
			assertThat(column.height).isEqualTo(60)
			assertThat(column.children[0].y).isEqualTo(0)
			assertThat(column.children[1].y).isEqualTo(30)
		}
	}
}
