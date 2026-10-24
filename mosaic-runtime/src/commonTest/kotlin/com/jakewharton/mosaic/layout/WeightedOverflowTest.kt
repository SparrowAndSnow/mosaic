package com.jakewharton.mosaic.layout

import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * A weighted child must not crash the layout when its fixed siblings already need more space than
 * the parent offers. Shrinking the terminal below the content's natural size does exactly that:
 * content-sized children keep their own width (or height), so the space left for weights goes
 * negative. It has to clamp to zero rather than become an empty `coerceIn` range.
 */
class WeightedOverflowTest {
	private val rendering =
		DebugRendering(TestTerminal.Capabilities(ansiLevel = AnsiLevel.NONE))

	@Test fun rowNarrowerThanItsFixedContentDoesNotThrow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Row(Modifier.width(4)) {
					Box(Modifier.requiredWidth(10).height(1))
					Text("x", modifier = Modifier.weight(1f))
				}
			}
			awaitSnapshot()
		}
	}

	@Test fun columnShorterThanItsFixedContentDoesNotThrow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column(Modifier.height(2)) {
					Text("a")
					Text("b")
					Text("c")
					Text("d", modifier = Modifier.weight(1f))
				}
			}
			awaitSnapshot()
		}
	}
}
