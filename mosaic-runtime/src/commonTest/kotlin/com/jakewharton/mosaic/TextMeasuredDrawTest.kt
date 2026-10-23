package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.getLocalRawSpanStyles
import com.jakewharton.mosaic.text.measureClusters
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The layout pass precomputes per-line grapheme-cluster metadata ([measureClusters]) and [Text]
 * draws through the measured fast path. This test drives both paths on identical content and
 * asserts the resulting surfaces are byte-for-byte identical for cells and ANSI output.
 */
class TextMeasuredDrawTest {

	private data class Painted(
		val cells: List<String>,
		val surface: TextSurface,
		val ansi: String,
	)

	private fun paintMeasured(text: String, width: Int, height: Int): Painted {
		val canvas = TextSurface(width, height)
		val scope = TextCanvasDrawScope(canvas, width, height)
		scope.drawMeasuredText(0, 0, text, text.measureClusters(), null)
		return paintSnapshot(canvas, width, height)
	}

	private fun paintStreamed(text: String, width: Int, height: Int): Painted {
		val canvas = TextSurface(width, height)
		val scope = TextCanvasDrawScope(canvas, width, height)
		scope.drawText(0, 0, text)
		return paintSnapshot(canvas, width, height)
	}

	private fun paintSnapshot(canvas: TextSurface, width: Int, height: Int): Painted {
		val cells = buildList {
			for (c in 0 until width) {
				add(canvas[0, c].text)
			}
		}
		val ansi = canvas.render(com.jakewharton.mosaic.terminal.AnsiLevel.TRUECOLOR, false)
		return Painted(cells, canvas, ansi)
	}

	private fun assertEquivalent(expected: Painted, actual: Painted, label: String) {
		assertEquals(expected.cells, actual.cells, "$label: per-cell text mismatch")
		assertEquals(expected.ansi, actual.ansi, "$label: ANSI output mismatch")
	}

	@Test fun plainAsciiMatchesStreaming() {
		val text = "row 05 value ➜ nothing"
		assertEquivalent(paintStreamed(text, 40, 1), paintMeasured(text, 40, 1), "ascii")
	}

	@Test fun wideCombiningAndSurrogateContentMatches() {
		val text = "中e\u0301\uD83D\uDE00\uD83C\uDDEF\uD83C\uDDF5·ab"
		assertEquivalent(paintStreamed(text, 30, 1), paintMeasured(text, 30, 1), "wide/combining")
	}

	@Test fun emptyAndWhitespaceEdgesMatch() {
		for (text in listOf("", "   ", "   x", "x   ", "\t")) {
			assertEquivalent(
				paintStreamed(text, 10, 1),
				paintMeasured(text, 10, 1),
				"edge<${text.codePointsLabel()}>",
			)
		}
	}

	@Test fun animatedStyleSpansMatchStreaming() {
		val annotated = AnnotatedString(
			"AB中e\u0301",
			spanStyle = SpanStyle(color = Color.Red, background = Color.Blue),
		)
		val streamed = TextSurface(12, 1)
		TextCanvasDrawScope(streamed, 12, 1).drawText(0, 0, annotated)
		val measured = TextSurface(12, 1)
		TextCanvasDrawScope(measured, 12, 1).drawMeasuredText(
			0,
			0,
			annotated.text,
			annotated.text.measureClusters(),
			{ start, end -> annotated.getLocalRawSpanStyles(start, end) },
		)
		assertEquivalent(
			paintSnapshot(streamed, 12, 1),
			paintSnapshot(measured, 12, 1),
			"animated spans",
		)
	}

	@Test fun wideClusterContinuationCellsMatchStreaming() {
		val text = "中文A\uD83D\uDE00"
		assertEquivalent(paintStreamed(text, 12, 1), paintMeasured(text, 12, 1), "continuations")
	}

	private fun String.codePointsLabel(): String = buildString {
		var i = 0
		while (i < length) {
			val unit = this[i].code
			val low = if (unit in 0xD800..0xDBFF && i + 1 < length) this[i + 1].code else -1
			val cp = if (low in 0xDC00..0xDFFF) {
				i++
				((unit - 0xD800) shl 10) + (low - 0xDC00) + 0x10000
			} else {
				unit
			}
			if (cp < 0x20 || cp > 0x7E) {
				append("U+")
				append(cp.toString(16).padStart(4, '0'))
			} else {
				append(cp.toChar())
			}
			i++
		}
	}

	@Test fun streamedTextWiderThanCanvasClipsAtRightEdge() {
		val canvas = TextSurface(4, 1)
		val scope = TextCanvasDrawScope(canvas, 4, 1)
		scope.drawText(0, 0, "中中中")
		val cells = (0 until 4).map { canvas[0, it].text }
		assertEquals(listOf("中", "", "中", ""), cells, "right edge clip keeps in-bounds heads only")
	}

	@Test fun streamedTextBelowCanvasClipsAtBottom() {
		val canvas = TextSurface(4, 1)
		val scope = TextCanvasDrawScope(canvas, 4, 1)
		scope.drawText(1, 0, "abc")
		val cells = (0 until 4).map { canvas[0, it].text }
		assertEquals(listOf(" ", " ", " ", " "), cells, "off-canvas row writes nothing")
	}
}
