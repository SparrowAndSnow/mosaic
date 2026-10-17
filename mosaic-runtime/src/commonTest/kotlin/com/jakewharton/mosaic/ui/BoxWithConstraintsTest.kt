package com.jakewharton.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jakewharton.mosaic.Container
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.position
import com.jakewharton.mosaic.size
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BoxWithConstraintsTest {

	@Test fun contentCanReadMaxWidthToSizeItself() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(100, 40)) {
					BoxWithConstraints(Modifier.fillMaxSize()) {
						Container(Modifier.width(maxWidth / 2), height = 10)
						Container(Modifier.width(maxWidth / 4), height = 20)
					}
				}
			}

			val boxWithConstraints = awaitSnapshot().children[0].children[0]
			val firstChild = boxWithConstraints.children[0].children[0]
			val secondChild = boxWithConstraints.children[0].children[1]

			assertThat(firstChild.size).isEqualTo(IntSize(50, 10))
			assertThat(firstChild.position).isEqualTo(IntOffset.Zero)
			assertThat(secondChild.size).isEqualTo(IntSize(25, 20))
			assertThat(secondChild.position).isEqualTo(IntOffset.Zero)
		}
	}

	@Test fun contentCanReadMinHeightAndBounds() = runTest {
		runMosaicTest(NodeSnapshots) {
			var minHeightRead = 0
			var boundedRead = false
			setContent {
				Box(Modifier.size(90, 30)) {
					BoxWithConstraints {
						minHeightRead = minHeight
						boundedRead = hasBoundedWidth && hasBoundedHeight && !hasFixedWidth
						Container(width = maxWidth, height = maxHeight)
					}
				}
			}

			val container = awaitSnapshot().children[0].children[0].children[0].children[0]
			assertThat(container.size).isEqualTo(IntSize(90, 30))
			assertThat(minHeightRead).isEqualTo(0)
			assertThat(boundedRead).isTrue()
		}
	}

	@Test fun fractionOfAvailableWidth() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(90, 10)) {
					BoxWithConstraints(Modifier.fillMaxSize()) {
						Container(Modifier.width((maxWidth * 2f / 3f).roundToInt()), height = 10)
						Container(Modifier.width(maxWidth / 3), height = 10)
					}
				}
			}

			val boxWithConstraints = awaitSnapshot().children[0].children[0]
			val firstChild = boxWithConstraints.children[0].children[0]
			val secondChild = boxWithConstraints.children[0].children[1]

			assertThat(firstChild.size).isEqualTo(IntSize(60, 10))
			assertThat(secondChild.size).isEqualTo(IntSize(30, 10))
		}
	}

	@Test fun contentRecomposesWhenConstraintsChange() = runTest {
		runMosaicTest(NodeSnapshots) {
			var size by mutableStateOf(100)
			setContent {
				Box(Modifier.size(size, 40)) {
					BoxWithConstraints(Modifier.fillMaxSize()) {
						Container(Modifier.width(maxWidth / 2), height = 10)
					}
				}
			}

			val boxWithConstraints = awaitSnapshot().children[0].children[0]
			val child = boxWithConstraints.children[0].children[0]
			assertThat(child.size).isEqualTo(IntSize(50, 10))

			size = 200

			val boxWithConstraints2 = awaitSnapshot().children[0].children[0]
			val child2 = boxWithConstraints2.children[0].children[0]
			assertThat(child2.size).isEqualTo(IntSize(100, 10))
		}
	}

	@Test fun matchParentSizeAndAlignInsideBoxWithConstraints() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				BoxWithConstraints(
					Modifier.size(100, 50),
					contentAlignment = Alignment.Center,
				) {
					Container(Modifier.matchParentSize())
					Container(Modifier.width(maxWidth / 2).align(Alignment.Center), height = 10)
				}
			}

			val boxWithConstraints = awaitSnapshot().children[0]
			val matchChild = boxWithConstraints.children[0].children[0]
			val alignedChild = boxWithConstraints.children[0].children[1]

			assertThat(matchChild.size).isEqualTo(IntSize(100, 50))
			assertThat(matchChild.position).isEqualTo(IntOffset.Zero)
			assertThat(alignedChild.size).isEqualTo(IntSize(50, 10))
			assertThat(alignedChild.position).isEqualTo(IntOffset(25, 20))
		}
	}

	@Test fun unconstrainedParentCollapsesToContentSize() = runTest {
		runMosaicTest(NodeSnapshots) {
			setContent {
				BoxWithConstraints {
					Container(width = 7, height = 3)
				}
			}

			val boxWithConstraints = awaitSnapshot().children[0]
			val child = boxWithConstraints.children[0].children[0]
			assertThat(child.size).isEqualTo(IntSize(7, 3))
			assertThat(boxWithConstraints.size).isEqualTo(IntSize(7, 3))
		}
	}

	@Test fun mutableIntStateDrivesWidth() = runTest {
		runMosaicTest(NodeSnapshots) {
			var cols by mutableIntStateOf(2)
			setContent {
				Box(Modifier.size(30, 10)) {
					BoxWithConstraints(Modifier.fillMaxSize()) {
						repeat(cols) {
							Container(Modifier.width(maxWidth / cols), height = 10)
						}
					}
				}
			}

			val twoCols = awaitSnapshot().children[0].children[0]
			assertThat(twoCols.children[0].children[0].size).isEqualTo(IntSize(15, 10))
			assertThat(twoCols.children[0].children[1].size).isEqualTo(IntSize(15, 10))

			cols = 3

			val threeCols = awaitSnapshot().children[0].children[0]
			assertThat(threeCols.children[0].children.size).isEqualTo(3)
			assertThat(threeCols.children[0].children[0].size).isEqualTo(IntSize(10, 10))
			assertThat(threeCols.children[0].children[1].size).isEqualTo(IntSize(10, 10))
			assertThat(threeCols.children[0].children[2].size).isEqualTo(IntSize(10, 10))
		}
	}
}
