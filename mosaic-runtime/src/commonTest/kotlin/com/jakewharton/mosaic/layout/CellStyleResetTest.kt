package com.jakewharton.mosaic.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.jakewharton.mosaic.DebugRendering
import com.jakewharton.mosaic.RenderingSnapshots
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Cells are reused across frames. A draw that leaves a style field unspecified must not inherit
 * the previous frame's styling for that cell, otherwise flat text painted where a colored block
 * used to be keeps the old background. The same accreting behavior must still hold within a
 * frame, where a child's uncolored text sits on its parent's fill.
 */
class CellStyleResetTest {
	private val rendering =
		DebugRendering(TestTerminal.Capabilities(ansiLevel = AnsiLevel.TRUECOLOR))

	private fun rows(frame: String): List<String> = frame.substringAfter("OUTPUT:\r\n").split("\r\n")

	@Test fun flatTextDoesNotRetainPreviousFrameBackground() = runTest {
		var colored by mutableIntStateOf(1)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Box(Modifier.size(20, 4)) {
					if (colored == 1) {
						Text("block", background = Color(138, 226, 52))
					} else {
						Text("plain")
					}
				}
			}
			val first = rows(awaitSnapshot().toString()).joinToString("\n")
			assertThat(first).contains("138;226;52")

			colored = 0
			val second = rows(awaitSnapshot().toString()).joinToString("\n")
			val secondRow = second.lines().first()
			// The cell now carries plain text with no styling: no green residue, no partial color.
			assertThat(second).doesNotContain("138;226;52")
			assertThat(secondRow).contains("plain")
		}
	}
}
