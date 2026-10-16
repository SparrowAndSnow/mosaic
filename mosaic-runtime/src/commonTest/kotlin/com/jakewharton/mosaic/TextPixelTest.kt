package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.ui.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextPixelTest {

	@Test fun defaultPixelIsAnEmptySpace() {
		val pixel = TextPixel()
		assertEquals(" ", pixel.text)
		assertEquals(1, pixel.width)
		assertFalse(pixel.isPlaceholder)
		assertTrue(pixel.isEmpty())
	}

	@Test fun styledPixelIsNotEmpty() {
		val pixel = TextPixel()
		pixel.foreground = Color.Red
		assertFalse(pixel.isEmpty())
	}

	@Test fun continuationMarking() {
		val pixel = TextPixel()
		pixel.markAsContinuation()
		assertEquals("", pixel.text)
		assertEquals(0, pixel.width)
		assertTrue(pixel.isPlaceholder)
		assertTrue(pixel.isEmpty())
	}

	@Test fun wideClusterRendersOnceAndSkipsPlaceholder() {
		val canvas = TextSurface(width = 4, height = 1)
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨👩👧
		val head = canvas[0, 0]
		head.text = family
		head.width = 2
		canvas[0, 1].markAsContinuation()
		canvas[0, 2].text = "A"

		assertEquals(family + "A", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun wideCjkSkipsPlaceholderBeforeNextChar() {
		val canvas = TextSurface(width = 3, height = 1)
		val head = canvas[0, 0]
		head.text = "中"
		head.width = 2
		canvas[0, 1].markAsContinuation()
		canvas[0, 2].text = "B"

		// The placeholder cell must not emit a character (which would double width the row).
		assertEquals("中B", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun wideClusterAtRowEndTrimsPlaceholder() {
		val canvas = TextSurface(width = 3, height = 1)
		val head = canvas[0, 0]
		head.text = "中"
		head.width = 2
		canvas[0, 1].markAsContinuation()

		assertEquals("中", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun zeroWidthClusterRendersItsText() {
		val canvas = TextSurface(width = 3, height = 1)
		val combining = canvas[0, 0]
		combining.text = "\u0301"
		combining.width = 0
		canvas[0, 1].text = "A"

		assertEquals("\u0301A", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun ansiRenderingWrapsClusterOnce() {
		val canvas = TextSurface(width = 3, height = 1)
		val head = canvas[0, 0]
		head.text = "中"
		head.width = 2
		head.foreground = Color.Red
		canvas[0, 1].markAsContinuation()

		assertEquals("\u001B[91m中\u001B[0m", canvas.render(AnsiLevel.ANSI16, true))
	}

	@Test fun fillBackgroundRendersSpaces() {
		val canvas = TextSurface(width = 2, height = 1)
		val scope = TextCanvasDrawScope(canvas, 2, 1)
		scope.drawRect(char = ' ', background = Color.Blue)

		assertEquals("\u001B[104m  \u001B[0m", canvas.render(AnsiLevel.ANSI16, true))
	}

	@Test fun drawScopeDrawTextWritesClusterTextAndWidth() {
		val canvas = TextSurface(width = 3, height = 1)
		val scope = TextCanvasDrawScope(canvas, 3, 1)
		scope.drawText(0, 0, "中")

		val head = canvas[0, 0]
		assertEquals("中", head.text)
		assertEquals(2, head.width)
		assertFalse(head.isPlaceholder)
		assertTrue(canvas[0, 1].isPlaceholder)
		assertEquals("中", canvas.render(AnsiLevel.NONE, true))
	}

	@Test fun drawScopeDrawTextWritesAstralEmojiAsSingleCluster() {
		val canvas = TextSurface(width = 3, height = 1)
		val scope = TextCanvasDrawScope(canvas, 3, 1)
		scope.drawText(0, 0, "\uD83D\uDE00") // 😀

		val head = canvas[0, 0]
		assertEquals("\uD83D\uDE00", head.text)
		assertEquals(2, head.width)
		assertTrue(canvas[0, 1].isPlaceholder)
	}

	@Test fun overwritingPlaceholderRestoresCarrier() {
		val canvas = TextSurface(width = 4, height = 1)
		canvas[0, 0].apply {
			text = "中"
			width = 2
		}
		canvas[0, 1].markAsContinuation()

		val scope = TextCanvasDrawScope(canvas, 4, 1)
		scope.drawText(0, 1, "X")

		val cell = canvas[0, 1]
		assertEquals("X", cell.text)
		assertEquals(1, cell.width)
		assertFalse(cell.isPlaceholder)
	}
}
