package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test

/** Partial Solid backdrops must erase whole wide clusters, never half a CJK/emoji glyph. */
class OverlayBackdropTest {

	private fun row(): TextSurface {
		val surface = TextSurface(6, 1)
		TextCanvasDrawScope(surface, 6, 1).drawText(0, 0, "中A🙂B")
		// 中 = cols 0..1, A = 2, 🙂 = cols 3..4, B = 5.
		assertThat(surface.cells[0].text).isEqualTo("中")
		assertThat(surface.cells[1].isPlaceholder).isTrue()
		assertThat(surface.cells[3].text).isEqualTo("🙂")
		assertThat(surface.cells[4].isPlaceholder).isTrue()
		return surface
	}

	private fun assertNoResidue(surface: TextSurface) {
		val terminal = VirtualTerminal(rows = 1, columns = 6)
		terminal.feed(surface.render(com.jakewharton.mosaic.terminal.AnsiLevel.NONE, false))
		assertThat(terminal.hasResidue()).isFalse()
	}

	@Test fun clearUsesDrawScopeTranslationAsAbsoluteOrigin() {
		val surface = TextSurface(6, 4)
		val painter = TextCanvasDrawScope(surface, 6, 4)
		for (row in 0 until 4) {
			painter.drawText(row, 0, "XXXXXX")
		}

		// A 2x1 node placed at (2,1) must clear only row 1 columns 2..3.
		surface.translationX = 2
		surface.translationY = 1
		TextCanvasDrawScope(surface, 2, 1).clear(Color.Unspecified)

		for (row in 0 until 4) {
			for (column in 0 until 6) {
				val cell = surface.cells[row * 6 + column]
				if (row == 1 && column in 2..3) {
					assertThat(cell.isEmpty()).isTrue()
				} else {
					assertThat(cell.text).isEqualTo("X")
				}
			}
		}
	}

	@Test fun rectOnCjkContinuationClearsWholeClusterAndKeepsNeighbors() {
		val surface = row()
		surface.clearRect(1, 0, 2, 1, Color.Unspecified)

		assertThat(surface.cells[0].isEmpty()).isTrue()
		assertThat(surface.cells[1].isEmpty()).isTrue()
		assertThat(surface.cells[2].text).isEqualTo("A")
		assertThat(surface.cells[3].text).isEqualTo("🙂")
		assertThat(surface.cells[4].isPlaceholder).isTrue()
		assertThat(surface.cells[5].text).isEqualTo("B")
		assertNoResidue(surface)
	}

	@Test fun rectEndingOnCjkHeadClearsItsContinuation() {
		val surface = row()
		surface.clearRect(0, 0, 1, 1, Color.Unspecified)

		assertThat(surface.cells[0].isEmpty()).isTrue()
		assertThat(surface.cells[1].isEmpty()).isTrue()
		assertThat(surface.cells[2].text).isEqualTo("A")
		assertNoResidue(surface)
	}

	@Test fun rectOnEmojiHeadOnlyClearsItsContinuation() {
		val surface = row()
		surface.clearRect(3, 0, 4, 1, Color.Unspecified)

		assertThat(surface.cells[3].isEmpty()).isTrue()
		assertThat(surface.cells[4].isEmpty()).isTrue()
		assertThat(surface.cells[2].text).isEqualTo("A")
		assertThat(surface.cells[5].text).isEqualTo("B")
		assertNoResidue(surface)
	}

	@Test fun rectOnEmojiContinuationOnlyClearsWholeCluster() {
		val surface = row()
		surface.clearRect(4, 0, 5, 1, Color.Unspecified)

		assertThat(surface.cells[3].isEmpty()).isTrue()
		assertThat(surface.cells[4].isEmpty()).isTrue()
		assertThat(surface.cells[2].text).isEqualTo("A")
		assertThat(surface.cells[5].text).isEqualTo("B")
		assertNoResidue(surface)
	}

	@Test fun fullWidthRectStillClearsEverything() {
		val surface = row()
		surface.clearRect(0, 0, 6, 1, Color.Unspecified)

		for (cell in surface.cells) {
			assertThat(cell.isEmpty()).isTrue()
		}
		assertThat(surface.render(com.jakewharton.mosaic.terminal.AnsiLevel.NONE, false)).isEqualTo("")
	}

	@Test fun adjacentCharacterInsideRectIsClearedWithoutTouchingClusterNeighbors() {
		val surface = row()
		surface.clearRect(2, 0, 3, 1, Color.Unspecified)

		assertThat(surface.cells[2].isEmpty()).isTrue()
		assertThat(surface.cells[0].text).isEqualTo("中")
		assertThat(surface.cells[1].isPlaceholder).isTrue()
		assertThat(surface.cells[3].text).isEqualTo("🙂")
		assertThat(surface.cells[4].isPlaceholder).isTrue()
		assertThat(surface.cells[5].text).isEqualTo("B")
		assertNoResidue(surface)
	}
}
