package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawTextClusterTest {

	private fun draw(text: String, width: Int = 4, height: Int = 1): TextSurface {
		val canvas = TextSurface(width, height)
		val scope = TextCanvasDrawScope(canvas, width, height)
		scope.drawText(0, 0, text)
		return canvas
	}

	@Test fun asciiUsesOneCellPerChar() {
		val canvas = draw("AB")
		assertEquals("A", canvas[0, 0].text)
		assertEquals("B", canvas[0, 1].text)
		assertEquals("AB", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun zwjEmojiFamilyDrawsIntoOneCell() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨👩👧
		val canvas = draw(family)
		val head = canvas[0, 0]
		assertEquals(family, head.text)
		assertEquals(2, head.width)
		assertFalse(head.isPlaceholder)
		assertTrue(canvas[0, 1].isPlaceholder)
		// The family must be emitted once and occupy exactly two columns.
		assertEquals(family, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun combiningMarkFoldsIntoBaseCell() {
		val canvas = draw("e\u0301")
		val head = canvas[0, 0]
		assertEquals("e\u0301", head.text)
		assertEquals(1, head.width)
		assertFalse(canvas[0, 1].isPlaceholder)
		assertEquals("e\u0301", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun flagPairDrawsIntoOneCell() {
		val flag = "\uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
		val canvas = draw(flag)
		val head = canvas[0, 0]
		assertEquals(flag, head.text)
		assertEquals(2, head.width)
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals(flag, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun keycapSequenceDrawsIntoOneCell() {
		val keycap = "#\uFE0F\u20E3"
		val canvas = draw(keycap)
		val head = canvas[0, 0]
		assertEquals(keycap, head.text)
		assertEquals(2, head.width)
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals(keycap, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun hangulJamoFoldsIntoOneWideCell() {
		val jamo = "\u1100\u1161"
		val canvas = draw(jamo)
		val head = canvas[0, 0]
		assertEquals(jamo, head.text)
		assertEquals(2, head.width)
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals(jamo, canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun mixedLineAdvancesColumnsByClusterWidth() {
		val canvas = draw("A中B", width = 4)
		assertEquals("A", canvas[0, 0].text)
		assertEquals("中", canvas[0, 1].text)
		assertEquals(2, canvas[0, 1].width)
		assertTrue(canvas[0, 2].isPlaceholder)
		assertEquals("B", canvas[0, 3].text)
		assertEquals("A中B", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun wideClusterIsFollowedByNextCharAtCorrectColumn() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC67" // 👨👧
		val canvas = draw(family + "x", width = 4)
		assertEquals(family, canvas[0, 0].text)
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals("x", canvas[0, 2].text)
		assertEquals(family + "x", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun spanStyleAppliesToWholeClusterCell() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨👩👧
		val canvas = TextSurface(4, 1)
		val scope = TextCanvasDrawScope(canvas, 4, 1)
		scope.drawText(0, 0, AnnotatedString(family, SpanStyle(color = Color.Red)))

		assertEquals(Color.Red, canvas[0, 0].foreground)
	}
}
