package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.FrameWriter
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.NodeSnapshots
import com.jakewharton.mosaic.Rendering
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.runMosaicComposition
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

/**
 * Locks in Modifier.onGloballyPositioned: the callback reports the node's top-left corner in
 * composition-root (screen) coordinates and its size, fires after placement, and re-fires only when
 * the reported placement actually changes - not on every drawn frame of an unmoved layout.
 */
class OnGloballyPositionedTest {

	@Test fun reportsGlobalPositionAndSize() = runTest {
		val reports = mutableListOf<Pair<IntOffset, IntSize>>()
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(30, 16)) {
					Box(Modifier.padding(3, 5)) {
						Box(Modifier.size(4, 6).onGloballyPositioned { position, size ->
							reports += position to size
						})
					}
				}
			}
			awaitSnapshot()
		}
		assertThat(reports).hasSize(1)
		assertThat(reports[0].first).isEqualTo(IntOffset(3, 5))
		assertThat(reports[0].second).isEqualTo(IntSize(4, 6))
	}

	@Test fun positionOfOffsetSiblingAccumulatesIntoGlobal() = runTest {
		val reports = mutableListOf<IntOffset>()
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(40, 20)) {
					Row {
						Box(Modifier.size(7, 1))
						Box(Modifier.size(2, 3).onGloballyPositioned { position, _ -> reports += position })
					}
				}
			}
			awaitSnapshot()
		}
		assertThat(reports).hasSize(1)
		// Inside the Row the measured content starts at x=0; the leading 7-wide sibling shifts
		// this box to column 7 in root coordinates.
		assertThat(reports[0]).isEqualTo(IntOffset(7, 0))
	}

	@Test fun firesAgainWhenPositionChanges() = runTest {
		var offsetX by mutableIntStateOf(0)
		val reports = mutableListOf<IntOffset>()
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(30, 10)) {
					Box(Modifier.offset(offsetX, 0).onGloballyPositioned { position, _ -> reports += position })
				}
			}
			awaitSnapshot()
			offsetX = 6
			awaitSnapshot()
		}
		assertThat(reports).isEqualTo(listOf(IntOffset(0, 0), IntOffset(6, 0)))
	}

	@Test fun firesAgainWhenSizeChanges() = runTest {
		var width by mutableIntStateOf(2)
		val reports = mutableListOf<IntSize>()
		runMosaicTest(NodeSnapshots) {
			setContent {
				Box(Modifier.size(30, 10)) {
					Box(Modifier.width(width).height(1).onGloballyPositioned { _, size -> reports += size })
				}
			}
			awaitSnapshot()
			width = 5
			awaitSnapshot()
		}
		assertThat(reports).isEqualTo(listOf(IntSize(2, 1), IntSize(5, 1)))
	}

	@Test fun unmovedNodeIsReportedOnceAcrossManyFrames() = runBlocking {
		val reports = mutableListOf<Pair<IntOffset, IntSize>>()
		val done = CompletableDeferred<Int>()
		val composition = launch {
			runMosaicComposition(
				terminal = TestTerminal(),
				rendering = object : Rendering {
					override fun render(mosaic: Mosaic): CharSequence = ""
				},
				frameWriter = FrameWriter(writeBytes = { _, _, _ -> }, flushBytes = {}),
			) {
				Box(Modifier.size(30, 16)) {
					Box(Modifier.padding(3, 5)) {
						Box(Modifier.size(4, 6).onGloballyPositioned { position, size ->
							reports += position to size
						})
					}
				}
				AnimatedFrameCounter(expected = 20) {
					done.complete(reports.size)
				}
			}
		}

		val reportedCount = withTimeout(5.seconds) { done.await() }
		composition.cancel()

		// 20 animated frames each redraw and re-place the node, yet the callback must stay at one.
		assertThat(reportedCount).isEqualTo(1)
	}

	@Composable
	private fun AnimatedFrameCounter(
		expected: Int,
		onComplete: () -> Unit,
	) {
		var frame by remember { mutableIntStateOf(0) }
		LaunchedEffect(expected, onComplete) {
			repeat(expected) { index ->
				withFrameNanos { frame = index }
			}
			onComplete()
		}
		// Reading the counter in composition ensures every tick invalidates and redraws.
		Text("$frame")
	}
}
