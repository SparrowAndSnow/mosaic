package com.jakewharton.mosaic

import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.isEmptyTextStyle
import com.jakewharton.mosaic.ui.isSpecifiedColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Locks in the row-level lazy reset: a frame that rewrites most rows must not blank the whole
 * cell array — only rows untouched by the draw pass revert to blank, so residue from a previous
 * frame is still impossible while the reset cost scales with the changed area.
 */
class SurfaceLazyResetTest {

	private fun TextSurface.drawFrame(block: TextSurface.() -> Unit) {
		beginDraw()
		block()
		resetCellsNotWritten()
	}

	@Test fun unwrittenCellsResetAndWrittenCellsKeepNewContent() {
		val canvas = TextSurface(width = 4, height = 3)
		canvas.drawFrame {
			this[0, 0].text = "A"
			this[1, 1].text = "B"
		}
		// Written rows keep their content.
		assertEquals("A", canvas[0, 0].text)
		assertEquals(" ", canvas[0, 1].text)
		assertEquals("B", canvas[1, 1].text)
		// Unwritten cells must be blank, including style residue from before the frame.
		assertFalse(canvas[2, 3].foreground.isSpecifiedColor)
		assertTrue(canvas[2, 3].isEmpty())
		assertSame(" ", canvas[2, 3].textSource) // blank slice restored
	}

	@Test fun residueFromPreviousFrameIsCleared() {
		val canvas = TextSurface(width = 4, height = 2)
		canvas.drawFrame {
			this[0, 0].text = "old"
			this[1, 0].text = "keep"
		}
		assertEquals("old", canvas[0, 0].text)
		// Next frame does not touch row 0 -> its cells revert to blank, row 1 preserved.
		canvas.drawFrame {
			this[1, 0].text = "keep2"
		}
		assertTrue(canvas[0, 0].isEmpty())
		assertEquals(" ", canvas[0, 0].text)
		assertEquals("keep2", canvas[1, 0].text)
	}

	@Test fun styleAndPlaceholderResidueIsClearedOnUnwrittenRow() {
		val canvas = TextSurface(width = 3, height = 2)
		canvas.drawFrame {
			this[0, 0].text = "中"
			this[0, 0].width = 2
			this[0, 1].markAsContinuation()
			this[0, 2].foreground = Color.Red
			this[0, 2].textStyle = TextStyle.Bold
		}
		assertTrue(canvas[0, 1].isPlaceholder)

		canvas.drawFrame { /* next frame paints nothing on row 0 */ }
		val cell = canvas[0, 1]
		assertTrue(cell.isEmpty())
		assertFalse(cell.isPlaceholder)
		assertFalse(cell.foreground.isSpecifiedColor)
		assertTrue(cell.textStyle.isEmptyTextStyle)
	}

	@Test fun drawnWideClusterKeepsContinuationCells() {
		val canvas = TextSurface(width = 4, height = 1)
		canvas.drawFrame {
			this[0, 0].text = "中"
			this[0, 0].width = 2
			this[0, 1].markAsContinuation()
		}
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals(TextStyle.Empty, canvas[0, 1].textStyle)
		canvas.drawFrame {
			this[0, 0].text = "X"
		}
		assertTrue(canvas[0, 1].isEmpty(), "orphaned continuation must be swept when unwritten next frame")
	}

	@Test fun partiallyUpdatedRowClearsShiftedOutCells() {
		// The classic residue trap for row-granular lazy clearing: X moves within one row.
		val canvas = TextSurface(width = 5, height = 1)
		canvas.drawFrame {
			this[0, 0].text = "X"
		}
		assertEquals("X", canvas[0, 0].text)
		canvas.drawFrame {
			this[0, 4].text = "X"
		}
		// Column 0 was not written this frame -> must revert to blank.
		assertEquals(" ", canvas[0, 0].text)
		assertEquals("X", canvas[0, 4].text)
	}
}
