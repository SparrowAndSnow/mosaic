package com.jakewharton.mosaic.text

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
