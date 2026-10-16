package com.jakewharton.mosaic

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.ui.TextStyle
import kotlin.test.Test

/**
 * Renders crafted cell grids through [AnsiIncrementalRendering] into a [VirtualTerminal] and
 * asserts the final *on-screen* state, locking the wide-cluster diff guarantees: no half-cell
 * residue when a wide cluster's head is overwritten, narrow->wide ("forced width") rewrites the
 * whole cluster, and a cluster whose extent would run past the right edge is not emitted (it is
 * replaced by a space instead, so the row can never wrap).
 */
class CellDiffRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test fun wideHeadOverwrittenByNarrowClearsFormerContinuation() {
		val vt = transition(
			row("<", wide("中"), ">"),
			row("<", narrow("X"), narrow(" "), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<X >")
	}

	@Test fun narrowBecomesWideWritesWholeCluster() {
		val vt = transition(
			row("<", narrow("X"), narrow("Y"), ">"),
			row("<", wide("中"), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<中>")
	}

	@Test fun wideBecomesRowOfNarrowRemovesAllTrace() {
		val vt = transition(
			row("<", wide("中"), ">"),
			row("<", narrow("X"), narrow("Y"), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<XY>")
	}

	@Test fun adjacentWideClusterBoundaryChange() {
		val vt = transition(
			row(wide("中"), wide("一"), ">"),
			row(wide("中"), wide("二"), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("中二>")
	}

	@Test fun wideClusterShiftedRightByOneColumn() {
		val vt = transition(
			row("<", wide("中"), ">"),
			row("<", narrow("A"), wide("中")),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<A中")
	}

	@Test fun zwjFamilyTextChangeKeepsClusterWhole() {
		val family = "👩\u200D👨\u200D👧" // 👩‍👨‍👧
		val second = "👩\u200D👩\u200D👧" // 👩‍👩‍👧
		val vt = transition(
			row("<", wide(family), ">"),
			row("<", wide(second), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<$second>")
	}

	@Test fun keycapClusterTextChange() {
		val keycap = "#\uFE0F\u20E3" // #️⃣
		val starCap = "*\uFE0F\u20E3" // *️⃣
		val vt = transition(
			row("<", wide(keycap), ">"),
			row("<", wide(starCap), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<$starCap>")
	}

	@Test fun unchangedWideClusterHeadWithStyleChangeIsClean() {
		val vt = transition(
			row(styled("中", 2, TextStyle.Bold), ">"),
			row(styled("中", 2, TextStyle.Italic), ">"),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("中>")
	}


	@Test fun wideClusterExactlyFittingRightEdgeIsKept() {
		val vt = transition(
			row(narrow("A"), wide("中")),
			row(narrow("A"), wide("文")),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("A文")
	}

	@Test fun nonZeroRowRightEdgeWideClusterIsBlocked() {
		val vt = transitionRows(
			listOf(row(narrow("Q")), row(narrow("X"), narrow("A"))),
			listOf(row(narrow("Q")), row(narrow("X"), wide("中"))),
			width = 2,
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("Q ")
		assertThat(vt.displayRow(1)).isEqualTo("X ")
	}

	@Test fun secondRowWideHeadChangeIsClean() {
		val vt = transitionRows(
			listOf(row(narrow("<"), wide("中"), narrow(">")), row(wide("中"), narrow("A"), narrow("B"))),
			listOf(row(narrow("<"), wide("中"), narrow(">")), row(wide("一"), narrow("A"), narrow("B"))),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("<中>")
		assertThat(vt.displayRow(1)).isEqualTo("一AB")
	}

	@Test fun trailingContentRemovalKeepsRowClean() {
		val vt = transition2(
			listOf(row(narrow("A"), wide("中"), narrow(">"))),
			listOf(row(narrow("A"), wide("中"))),
			width = 4,
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("A中 ")
	}

	@Test fun contentShrinkClearsTrailingWideGlyph() {
		val vt = transition2(
			listOf(row(narrow("A"), wide("中"), narrow(">"))),
			listOf(row(narrow("B"), narrow("X"))),
			width = 4,
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("BX  ")
	}

	@Test fun resizeToNarrowerRowClearsFormerWideColumns() {
		val vt = resizeTransition(
			oldWidth = 3, row(narrow("B"), wide("中")),
			newWidth = 2, row(narrow("B"), narrow("X")),
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("BX ")
	}

	@Test fun rightEdgeWideClusterIsNotEmittedBeyondRow() {
		val vt = transition(
			row("<", narrow("A")),
			row("<", wide("中")),
			width = 2,
		)
		assertThat(vt.hasResidue()).isFalse()
		assertThat(vt.displayRow(0)).isEqualTo("< ")
	}

	// ---- cell builders ----

	private fun narrow(text: String) = Cell(text, 1, TextStyle.Empty)

	private fun wide(text: String) = Cell(text, 2, TextStyle.Empty)

	private fun styled(text: String, width: Int, style: TextStyle) = Cell(text, width, style)

	private fun row(vararg tokens: Any): List<Cell> = tokens.map { token ->
		if (token is Cell) token else narrow(token as String)
	}

	private data class Cell(val text: String, val width: Int, val style: TextStyle)

	private fun transitionRows(
		oldRows: List<List<Cell>>,
		newRows: List<List<Cell>>,
		width: Int = oldRows[0].sumOf { it.width },
	): VirtualTerminal {
		val oldHeight = oldRows.size
		val newHeight = newRows.size
		check(oldHeight == newHeight && oldHeight == newRows.size)
		val vt = VirtualTerminal(oldHeight, width)
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, oldHeight).applyRows(oldRows))))
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, oldHeight).applyRows(newRows))))
		return vt
	}

	/** Keeps the surface at a fixed height so a row's content may grow or shrink in place. */
	private fun transition2(
		oldRows: List<List<Cell>>,
		newRows: List<List<Cell>>,
		width: Int,
	): VirtualTerminal {
		val vt = VirtualTerminal(3, width)
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, 3).applyRows(oldRows))))
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, 3).applyRows(newRows))))
		return vt
	}

	/** Changes the surface width, forcing the resize (full redraw) path in the renderer. */
	private fun resizeTransition(
		oldWidth: Int,
		oldRow: List<Cell>,
		newWidth: Int,
		newRow: List<Cell>,
	): VirtualTerminal {
		val vt = VirtualTerminal(1, maxOf(oldWidth, newWidth))
		vt.feed(rendering.render(FakeMosaic(TextSurface(oldWidth, 1).applyRow(0, oldRow))))
		vt.feed(rendering.render(FakeMosaic(TextSurface(newWidth, 1).applyRow(0, newRow))))
		return vt
	}

	private fun TextSurface.applyRows(rows: List<List<Cell>>): TextSurface {
		rows.forEachIndexed { row, cells -> applyRow(row, cells) }
		return this
	}

	private fun transition(
		old: List<Cell>,
		new: List<Cell>,
		width: Int = old.sumOf { it.width },
	): VirtualTerminal {
		val oldWidth = old.sumOf { it.width }
		val newWidth = new.sumOf { it.width }
		check(oldWidth == width && newWidth >= width) {
			"frames must fit the surface width (old=$oldWidth new=$newWidth width=$width)"
		}
		val vt = VirtualTerminal(1, width)
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, 1).applyRow(0, old))))
		vt.feed(rendering.render(FakeMosaic(TextSurface(width, 1).applyRow(0, new))))
		return vt
	}

	private fun TextSurface.applyRow(row: Int, cells: List<Cell>): TextSurface {
		var col = 0
		for (cell in cells) {
			if (col >= width) break
			val pixel = this.cells[row * width + col]
			pixel.text = cell.text
			pixel.width = cell.width
			pixel.isPlaceholder = false
			pixel.textStyle = cell.style
			for (offset in 1 until cell.width) {
				if (col + offset < width) {
					this.cells[row * width + col + offset].markAsContinuation()
				}
			}
			col += cell.width
		}
		return this
	}

	private class FakeMosaic(
		private val surface: TextSurface,
	) : Mosaic {
		override fun setContent(content: @Composable () -> Unit) = Unit
		override fun draw(): TextCanvas = surface
		override fun static(): String? = null
		override fun dumpNodes(): String = ""
		override suspend fun awaitComplete() = Unit
		override fun cancel() = Unit
	}
}
