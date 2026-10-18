package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.TextSurface
import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.ui.TextOverflow
import com.jakewharton.mosaic.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLayoutWidthTest {

	private fun measuredWidth(text: String): Int {
		val layout = StringTextLayout()
		layout.value = text
		layout.measure()
		return layout.width
	}

	@Test fun asciiAndCjk() {
		assertEquals(3, measuredWidth("ABC"))
		assertEquals(4, measuredWidth("A中B"))
		assertEquals(2, measuredWidth("中"))
	}

	@Test fun combiningMarksAreZeroWidth() {
		assertEquals(1, measuredWidth("e\u0301"))
		assertEquals(0, measuredWidth("\u200B"))
	}

	@Test fun zeroWidthJoinerEmojiIsTwoColumns() {
		assertEquals(2, measuredWidth("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"))
		assertEquals(2, measuredWidth("\uD83D\uDC68\u200D\uD83D\uDC67"))
	}

	@Test fun flagsAreTwoColumns() {
		assertEquals(2, measuredWidth("\uD83C\uDDE8\uD83C\uDDF3")) // 🇨🇳
		assertEquals(1, measuredWidth("\uD83C\uDDE8")) // single RI
	}

	@Test fun keycapSequenceIsTwoColumns() {
		// Fully-qualified keycap (with VS16) is 2 columns; the bare U+20E3 form is 1 in UAX #11.
		assertEquals(2, measuredWidth("#\uFE0F\u20E3"))
		assertEquals(1, measuredWidth("#\u20E3"))
	}

	@Test fun vs16MakesEmojiTwoColumnsVs15KeepsOne() {
		assertEquals(1, measuredWidth("©"))
		assertEquals(2, measuredWidth("©\uFE0F")) // VS16
		assertEquals(1, measuredWidth("\u2600\uFE0E")) // VS15 text presentation
		assertEquals(2, measuredWidth("\u2600\uFE0F")) // VS16 emoji presentation
	}

	@Test fun hangulSyllablesAndJamoFold() {
		assertEquals(2, measuredWidth("각"))
		assertEquals(2, measuredWidth("\u1100\u1161")) // L+V jamo
	}

	// Measurement and drawing must never disagree about how many columns a cluster occupies or
	// where it starts: the measure pass segments each laid-out line into (cluster, width) pairs,
	// and the draw pass consumes exactly that segmentation. Drawing uses the measured line on a
	// fresh canvas and the painted per-column content is matched column-by-column against the
	// segmentation: each cluster's head cell must hold the cluster text at the column measurement
	// placed it, wide clusters must reserve placeholder continuation cells, and the right edge of
	// the last contentful cell must equal the measured line width.
	private fun assertMeasureMatchesDraw(
		text: String,
		maxWidth: Int = Constraints.Infinity,
		maxLines: Int = Int.MAX_VALUE,
		overflow: TextOverflow = TextOverflow.Clip,
		scroll: Int = 0,
		label: String = text,
	) {
		val layout = StringTextLayout()
		layout.value = text
		layout.measure(
			maxWidth = maxWidth,
			maxLines = maxLines,
			overflow = overflow,
			alignmentWidth = Constraints.Infinity,
			scroll = scroll,
		)
		assertColumnsMatchLayout(
			lines = layout.lines,
			measures = layout.lineMeasures,
			label = label,
		)
		// Layout width is the widest laid-out line, which must also be the widest painted row.
		val widest = layout.lineMeasures.maxOfOrNull { it.totalWidth } ?: 0
		assertEquals(widest, layout.width, "$label: layout.width vs widest line")
	}

	@Test fun asciiAndCjkMeasureMatchesDraw() {
		assertMeasureMatchesDraw("AB中CD")
		assertMeasureMatchesDraw("A中B")
		assertMeasureMatchesDraw("中")
	}

	@Test fun wideAndCombiningContentMeasureMatchesDraw() {
		assertMeasureMatchesDraw("\u4E2De\u0301\uD83D\uDE00\uD83C\uDDEF\uD83C\uDDF5\u00B7ab")
		assertMeasureMatchesDraw("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 X")
		assertMeasureMatchesDraw("#\uFE0F\u20E3 end")
		assertMeasureMatchesDraw("\u00A9\uFE0F txt") // VS16 keeps the emoji at 2 columns
	}

	@Test fun interiorSpacesMatchAcrossMeasureAndDraw() {
		// A line containing but not ending in spaces: the spaces are real clusters and must land
		// on their measured columns even though the renderer treats an all-space cell as empty.
		assertMeasureMatchesDraw("a b 中\tc")
		assertMeasureMatchesDraw("ab 中 def")
	}

	@Test fun wrappedLinesMeasureMatchesDraw() {
		// Soft wrap must not split a cluster; every produced line's measure must match painting.
		assertMeasureMatchesDraw("ABCDEFG\u4E2DABCD", maxWidth = 5)
		assertMeasureMatchesDraw("\uD83C\uDDE8\uD83C\uDDF3\uD83C\uDDE8\uD83C\uDDF3\uD83C\uDDE8\uD83C\uDDF3", maxWidth = 4)
		assertMeasureMatchesDraw("e\u0301e\u0301e\u0301e\u0301", maxWidth = 2)
	}

	@Test fun overflowClipAndEllipsisMeasureMatchesDraw() {
		assertMeasureMatchesDraw("ABCDEF\u4E2D\uD83D\uDE00", maxWidth = 5, maxLines = 2, overflow = TextOverflow.Clip)
		assertMeasureMatchesDraw("ABCDEF\u4E2D\uD83D\uDE00", maxWidth = 5, maxLines = 2, overflow = TextOverflow.Ellipsis)
		assertMeasureMatchesDraw("超级长的中文内容", maxWidth = 4, maxLines = 2, overflow = TextOverflow.Ellipsis)
	}

	@Test fun scrollWindowMeasureMatchesDraw() {
		assertMeasureMatchesDraw("l0\nl1\nl2\nl3\nl4", maxLines = 2, scroll = 2)
	}

	@Test fun annotatedStringLayoutMeasureMatchesDraw() {
		val layout = AnnotatedStringTextLayout()
		layout.value = AnnotatedString("\u4E2De\u0301\uD83D\uDE00 X")
		layout.measure()
		assertColumnsMatchLayout(
			lines = layout.lines.map { it.text },
			measures = layout.lineMeasures,
			label = "annotated",
		)
	}

	private fun assertColumnsMatchLayout(
		lines: List<String>,
		measures: List<LineMeasure>,
		label: String,
	) {
		val widest = measures.maxOfOrNull { it.totalWidth } ?: 0
		val paintWidth = widest + 4 // A little slack so nothing clamps against the right edge.
		val canvas = TextSurface(paintWidth, lines.size.coerceAtLeast(1))
		val scope = TextCanvasDrawScope(canvas, paintWidth, canvas.height)
		lines.forEachIndexed { row, line ->
			scope.drawMeasuredText(row, 0, line, measures[row], null)
		}

		lines.forEachIndexed { row, line ->
			val measure = measures[row]
			val perColumn = arrayOfNulls<String>(measure.totalWidth)
			var column = 0
			for (i in measure.clusterStarts.indices) {
				val start = measure.clusterStarts[i]
				val end = if (i + 1 < measure.clusterStarts.size) {
					measure.clusterStarts[i + 1]
				} else {
					line.length
				}
				val clusterWidth = measure.clusterWidths[i]
				// A zero-width cluster writes and overwrites its own (non-advancing) column at draw
				// time, so writing it over the same slot mirrors painting order exactly.
				perColumn[column] = line.substring(start, end)
				column += clusterWidth
			}

			for (c in perColumn.indices) {
				val expected = perColumn[c]
				val painted = canvas[row, c]
				if (expected == null) {
					assertTrue(painted.isPlaceholder, "$label row $row col $c: expected continuation placeholder")
				} else {
					assertEquals(expected, painted.text, "$label row $row col $c: cluster text")
				}
			}

			// Right edge: the last contentful cell (non-empty, non-continuation) spans to its width.
			// Rows may carry trailing spaces whose cells are treated as empty by the renderer, so
			// only contentful cells define the visible width; every case above avoids trailing spaces.
			var rightEdge = 0
			for (c in measure.totalWidth - 1 downTo 0) {
				val painted = canvas[row, c]
				if (!painted.isEmpty() && !painted.isPlaceholder) {
					rightEdge = c + painted.width
					break
				}
			}
			assertEquals(measure.totalWidth, rightEdge, "$label row $row: painted right edge vs measured width")
		}
	}
}
