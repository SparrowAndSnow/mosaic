package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Locks in the "source string + interval" cell model: [TextPixel] stores each cluster as a
 * `[textStart, textEnd)` slice into [TextPixel.textSource] so the draw hot path never materializes
 * a substring per cluster, while the [TextPixel.text] property still reads/writes whole strings.
 */
class CellSliceTest {

	private fun draw(text: String, width: Int = 4, height: Int = 1): TextSurface {
		val canvas = TextSurface(width, height)
		val scope = TextCanvasDrawScope(canvas, width, height)
		scope.drawText(0, 0, text)
		return canvas
	}

	@Test fun drawStoresClusterAsSliceIntoSourceString() {
		val text = "AB中"
		val canvas = draw(text, width = 4, height = 1)

		val headA = canvas[0, 0]
		assertSame(text, headA.textSource, "cell must reference the source line, not a copy")
		assertEquals(0, headA.textStart)
		assertEquals(1, headA.textEnd)
		assertEquals("A", headA.text)
		assertEquals(1, headA.width)

		val headB = canvas[0, 1]
		assertSame(text, headB.textSource)
		assertEquals(1, headB.textStart)
		assertEquals(2, headB.textEnd)
		assertEquals("B", headB.text)

		val headCjk = canvas[0, 2]
		assertSame(text, headCjk.textSource)
		assertEquals(2, headCjk.textStart)
		assertEquals(3, headCjk.textEnd)
		assertEquals("中", headCjk.text)
		assertEquals(2, headCjk.width)
		assertFalse(headCjk.isPlaceholder)

		// The continuation cell of the wide cluster is a zero-length slice, not a sharing cell.
		val continuation = canvas[0, 3]
		assertTrue(continuation.isPlaceholder)
		assertEquals(0, continuation.textEnd - continuation.textStart)
		assertEquals("", continuation.text)

		assertEquals(text, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun combiningClusterKeepsMultiCharSlice() {
		val canvas = draw("e\u0301x", width = 3, height = 1)
		val head = canvas[0, 0]
		assertEquals("e\u0301", head.text)
		assertEquals(2, head.textEnd - head.textStart)
		assertEquals("e\u0301x", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun spanStyleOverrideKeepsSameSlice() {
		val text = "AB中"
		val canvas = TextSurface(4, 1)
		val scope = TextCanvasDrawScope(canvas, 4, 1)
		scope.drawText(0, 0, AnnotatedString(text, SpanStyle(color = Color.Red)))

		val headCjk = canvas[0, 2]
		assertSame(text, headCjk.textSource, "span-style override must redraw the same slice")
		assertEquals("中", headCjk.text)
		assertEquals(Color.Red, headCjk.foreground)
		assertEquals(text, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun sameContentFromDifferentSourcesComparesEqual() {
		val first = draw("abcdef", width = 6, height = 1)
		// Compiled literals are interned on the JVM, so build a distinct (non-interned) instance.
		val second = draw(buildString { append("abc"); append("def") }, width = 6, height = 1)

		for (column in 0 until 6) {
			val a = first[0, column]
			val b = second[0, column]
			assertEquals(a.text, b.text)
			assertNotSame(a.textSource, b.textSource)
			// Slice equality (used by the cell diff) is content-based, never source-identity.
			assertTrue(sliceTextEqual(a, b))
		}
	}

	@Test fun copyFromPreservesSliceAndCopyIsIndependent() {
		val canvas = draw("AB中", width = 4, height = 1)
		val retained = Array(4) { TextPixel() }
		canvas.copyCellsTo(retained)

		assertSame(canvas[0, 2].textSource, retained[2].textSource)
		assertEquals("中", retained[2].text)
		assertEquals(2, retained[2].width)

		// Mutating the copy must not affect the surface (fields are copied, not aliased).
		retained[2].text = "X"
		assertEquals("中", canvas[0, 2].text)
		assertEquals("X", retained[2].text)
	}

	@Test fun resetRestoresBlankSlice() {
		val canvas = draw("AB中", width = 4, height = 1)
		canvas.reset()

		for (column in 0 until canvas.width) {
			val cell = canvas[0, column]
			assertEquals(1, cell.textEnd - cell.textStart)
			assertEquals(" ", cell.text)
			assertFalse(cell.isPlaceholder)
		}
		// The renderer trims trailing blank cells, so an all-blank surface renders empty.
		assertEquals("", canvas.render(AnsiLevel.NONE, true))
	}

	private fun sliceTextEqual(a: TextPixel, b: TextPixel): Boolean {
		val length = a.textEnd - a.textStart
		if (length != b.textEnd - b.textStart) {
			return false
		}
		var i = 0
		while (i < length) {
			if (a.textSource[a.textStart + i] != b.textSource[b.textStart + i]) {
				return false
			}
			i++
		}
		return true
	}
}
