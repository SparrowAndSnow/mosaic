package com.jakewharton.mosaic

import androidx.compose.runtime.mutableStateOf
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Locks the per-frame surface reuse: the root reuses its [TextSurface] (and its cell objects)
 * across draws instead of allocating fresh ones, and [TextSurface.reset] fully restores every cell
 * to the blank default so a later frame can never show residue from an earlier one.
 */
class SurfaceReuseTest {

	@Test fun resetRestoresEveryCellToBlankDefault() {
		val canvas = TextSurface(width = 3, height = 2)
		canvas[0, 0].text = "A"
		canvas[0, 1].markAsContinuation()
		canvas[0, 2].text = "中"
		canvas[0, 2].width = 2
		canvas[1, 0].foreground = Color.Red
		canvas[1, 1].textStyle = TextStyle.Bold

		canvas.reset()

		val blank = TextPixel()
		for (row in 0 until canvas.height) {
			for (column in 0 until canvas.width) {
				val cell = canvas[row, column]
				assertEquals(blank.text, cell.text)
				assertEquals(blank.width, cell.width)
				assertEquals(blank.isPlaceholder, cell.isPlaceholder)
				assertEquals(blank.foreground, cell.foreground)
				assertEquals(blank.background, cell.background)
				assertEquals(blank.textStyle, cell.textStyle)
				assertEquals(blank.underlineStyle, cell.underlineStyle)
				assertEquals(blank.underlineColor, cell.underlineColor)
				assertTrue(cell.isEmpty())
			}
		}
	}

	@Test fun secondDrawShowsNoResidueFromFirst() = runTest {
		val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			val value = mutableStateOf("Alpha")
			setContent {
				Column {
					Text(value.value)
					Text("footer")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HAlpha
				|footer
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			// Shrink the first row to nothing on the reused surface, then diff against the previous
			// frame. The incremental renderer must rewrite the vacated row as blank (clearing any old
			// residue such as the "pha" suffix) instead of leaking it into the new frame.
			value.value = ""
			assertThat(awaitSnapshot()).isEqualTo(
				synchronizedOutputEnable + CSI + "1;1H" + clearLine + CSI + "3;1H" + synchronizedOutputDisable,
			)
		}
	}
}
