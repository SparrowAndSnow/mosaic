package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.TextOverflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLayoutWrapTest {

	private fun wrap(text: String, maxWidth: Int): List<String> {
		val layout = StringTextLayout()
		layout.value = text
		layout.measure(maxWidth)
		return layout.lines
	}

	@Test fun asciiWrapsAtMaxWidth() {
		val layout = StringTextLayout()
		layout.value = "abcdef"
		layout.measure(3)

		assertEquals(listOf("abc", "def"), layout.lines)
		assertEquals(3, layout.width)
		assertEquals(2, layout.height)
	}

	@Test fun singleLineWhenItFits() {
		assertEquals(listOf("abc"), wrap("abc", 4))
		assertEquals(listOf("abc"), wrap("abc", 100))
	}

	@Test fun unboundedMeasurementKeepsOneLinePerParagraph() {
		val layout = StringTextLayout()
		layout.value = "中A中B中C"
		layout.measure()

		assertEquals(listOf("中A中B中C"), layout.lines)
		assertEquals(9, layout.width)
		assertEquals(1, layout.height)
	}

	@Test fun emptyParagraphsArePreserved() {
		assertEquals(listOf("a", "", "b"), wrap("a\n\nb", 1))
		assertEquals(listOf("", "a", ""), wrap("\na\n", 1))
	}

	@Test fun cjkWrapsAtCharacterBoundary() {
		assertEquals(listOf("中A", "中B", "中C"), wrap("中A中B中C", 4))
	}

	@Test fun zwjEmojiFamilyIsNeverSplit() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
		assertEquals(listOf(family, "X"), wrap("${family}X", 2))
	}

	@Test fun combiningMarkStaysWithBase() {
		assertEquals(listOf("e\u0301", "x"), wrap("e\u0301x", 1))
	}

	@Test fun flagPairIsNeverSplit() {
		assertEquals(listOf("\uD83C\uDDE8\uD83C\uDDF3", "A"), wrap("\uD83C\uDDE8\uD83C\uDDF3A", 2))
	}

	@Test fun oversizedClusterTakesItsOwnLine() {
		assertEquals(listOf("中", "A"), wrap("中A", 1))
	}

	@Test fun annotatedStringWrapKeepsWholeSpans() {
		val annotated = buildAnnotatedString {
			withStyle(SpanStyle(color = Color.Red)) {
				append("abc")
			}
			append("def")
		}
		val layout = AnnotatedStringTextLayout()
		layout.value = annotated
		layout.measure(3)

		assertEquals(2, layout.height)
		assertEquals("abc", layout.lines[0].text)
		assertTrue(layout.lines[0].spanStylesOrNull?.isNotEmpty() == true)

		assertEquals("def", layout.lines[1].text)
		assertTrue(layout.lines[1].spanStylesOrNull.isNullOrEmpty())
	}

	@Test fun maxLinesClipsRemainingLines() {
		val layout = StringTextLayout()
		layout.value = "a\nb\nc\nd"
		layout.measure(maxLines = 2, overflow = TextOverflow.Clip)

		assertEquals(listOf("a", "b"), layout.lines)
		assertEquals(2, layout.height)
	}

	@Test fun ellipsisMarksOverflowingLastLine() {
		val layout = StringTextLayout()
		layout.value = "中A中B中C"
		layout.measure(maxWidth = 4, maxLines = 2, overflow = TextOverflow.Ellipsis)

		assertEquals(listOf("中A", "中B\u2026"), layout.lines)
		assertEquals(4, layout.width)
		assertEquals(2, layout.height)
	}

	@Test fun ellipsisKeepsOverflowingClusterWhole() {
		val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
		val layout = StringTextLayout()
		layout.value = "${family}Z\nab"
		layout.measure(maxWidth = 3, maxLines = 1, overflow = TextOverflow.Ellipsis)

		assertEquals(listOf("${family}\u2026"), layout.lines)
		assertEquals(3, layout.width)
		assertEquals(1, layout.height)
	}

	@Test fun ellipsisDropsWholeClusterWhenItCannotFit() {
		val layout = StringTextLayout()
		layout.value = "中\nab"
		layout.measure(maxWidth = 2, maxLines = 1, overflow = TextOverflow.Ellipsis)

		assertEquals(listOf("\u2026"), layout.lines)
		assertEquals(1, layout.width)
		assertEquals(1, layout.height)
	}

	@Test fun ellipsisWithUnboundedWidthClipsLines() {
		val layout = StringTextLayout()
		layout.value = "a\nb\nc"
		layout.measure(maxLines = 2, overflow = TextOverflow.Ellipsis)

		assertEquals(listOf("a", "b"), layout.lines)
		assertEquals(2, layout.height)
	}
}
