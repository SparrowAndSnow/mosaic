// 
package com.jakewharton.mosaic.text.unicodewidth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the packed-Int width state machine (`widthInStr`/`lookupWidth` and the CJK
 * variants) to the pre-refactor, object-based implementation. The oracle below is the original
 * `Pair<Int, WidthInfo>` machine extracted verbatim from git history (renamed `*Ref`), reused
 * only here as the regression reference; production code now runs the allocation-free packed
 * version. Every reachable state is replayed against the full Basic Multilingual Plane plus the
 * astral ranges the state machine cares about (emoji, regional indicators, tags, variation
 * selectors), and a string corpus is cross-checked end to end.
 */
class PackedWidthEquivalenceTest {

	@Test fun everyStateMatchesPackedMachineOnBmp() {
		for (state in refStates) {
			for (cp in 0..0xFFFF) {
				assertEquals(refWidthInStrPacked(cp, state), widthInStr(cp, state), "non-CJK cp=0x" + cp.toString(16))
				assertEquals(refWidthInStrCjkPacked(cp, state), widthInStrCjk(cp, state), "CJK cp=0x" + cp.toString(16))
			}
		}
	}

	@Test fun everyStateMatchesPackedMachineOnAstralRanges() {
		val ranges = listOf(
			0x10000..0x10FFFF step 991, // broad astral sweep
			0x1F000..0x1FFFF step 1,    // emoji blocks
			0x1F1E6..0x1F1FF,           // regional indicators
			0xE0020..0xE007F,           // tag characters
			0xE0100..0xE01EF,           // variation selectors supplement
		)
		for (state in refStates) {
			for (range in ranges) for (cp in range) {
				assertEquals(refWidthInStrPacked(cp, state), widthInStr(cp, state), "non-CJK cp=0x" + cp.toString(16))
				assertEquals(refWidthInStrCjkPacked(cp, state), widthInStrCjk(cp, state), "CJK cp=0x" + cp.toString(16))
			}
		}
	}

	@Test fun stringWidthsMatchReference() {
		for (text in corpus) {
			assertEquals(strWidthRef(text), strWidth(text), "non-CJK: " + text)
			assertEquals(strWidthCjkRef(text), strWidthCjk(text), "CJK: " + text)
		}
	}

	private val corpus = listOf(
		"",
		"ABC",
		" hello\tworld ",
		"A\u4E2DB",
		"e\u0301",
		"\u0301",
		"\uD83D\uDE00",
		"\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
		"\uD83C\uDDE8\uD83C\uDDF3 \uD83C\uDDFA\uD83C\uDDF8",
		"#\uFE0F\u20E3",
		"1\uFE0F\u20E3",
		"\u00A9\uFE0F",
		"\u2600\uFE0E",
		"\u2600\uFE0F",
		"\u1100\u1161\u11A8",
		"\uAC00",
		"\u0915\u094D\u0915",
		"\u0915\u094D\u200D\u0937",
		"\u0644\u0627",
		"\u05D0\u05DC",
		"\u17D2\u17D7",
		"\u0C1A\u17D2",
		"\uA4F8\uA4F9",
		"\u10C03\u10C32",
		"\u0023\uFE0F\u20E3",
		"\u0031\uFE0F\u20E3",
		"\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC77\uDB40\uDC6C\uDB40\uDC73\uDB40\uDC7F",
		"\uD83D\uDC69\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68",
		"\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC66",
		"\uD83E\uDD1D\u200D\uD83E\uDD1D",
		"\uD83C\uDDFA\uD83C\uDDF8",
		"\u2744\uFE0F",
		"\uD83D\uDC4D\uD83C\uDFFC",
		"\uFF9E",
		"\uFF9F",
		"\u3099",
		"\u0E33",
		"\u0EB2\u0ECC",
	)

	private val refStates: IntArray = intArrayOf(
		RefWidthInfo.DEFAULT.raw,
		RefWidthInfo.LINE_FEED.raw,
		RefWidthInfo.EMOJI_MODIFIER.raw,
		RefWidthInfo.REGIONAL_INDICATOR.raw,
		RefWidthInfo.SEVERAL_REGIONAL_INDICATOR.raw,
		RefWidthInfo.EMOJI_PRESENTATION.raw,
		RefWidthInfo.ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.VS16_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.KEYCAP_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.REGIONAL_INDICATOR_ZWJ_PRESENTATION.raw,
		RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION.raw,
		RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION.raw,
		RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_D1_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_D2_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_D3_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.TAG_A6_END_ZWJ_EMOJI_PRESENTATION.raw,
		RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E.raw,
		RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI.raw,
		RefWidthInfo.VARIATION_SELECTOR_1_OR_2.raw,
		RefWidthInfo.VARIATION_SELECTOR_15.raw,
		RefWidthInfo.VARIATION_SELECTOR_16.raw,
		RefWidthInfo.JOINING_GROUP_ALEF.raw,
		RefWidthInfo.COMBINING_LONG_SOLIDUS_OVERLAY.raw,
		RefWidthInfo.SOLIDUS_OVERLAY_ALEF.raw,
		RefWidthInfo.HEBREW_LETTER_LAMED.raw,
		RefWidthInfo.ZWJ_HEBREW_LETTER_LAMED.raw,
		RefWidthInfo.BUGINESE_LETTER_YA.raw,
		RefWidthInfo.ZWJ_BUGINESE_LETTER_YA.raw,
		RefWidthInfo.BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA.raw,
		RefWidthInfo.TIFINAGH_CONSONANT.raw,
		RefWidthInfo.ZWJ_TIFINAGH_CONSONANT.raw,
		RefWidthInfo.TIFINAGH_JOINER_CONSONANT.raw,
		RefWidthInfo.LISU_TONE_LETTER_MYA_NA_JEU.raw,
		RefWidthInfo.OLD_TURKIC_LETTER_ORKHON_I.raw,
		RefWidthInfo.ZWJ_OLD_TURKIC_LETTER_ORKHON_I.raw,
		RefWidthInfo.KHMER_COENG_ELIGIBLE_LETTER.raw,
	)
}

// {
// The remaining lines are the verbatim object-based UAX #11 machine used as the reference.
// {
internal data class RefWidthInfo(
    val raw: Int,
) {
    /** Whether this width mode is ligature transparent (has 5th MSB set). */
    fun isLigatureTransparent(): Boolean =
        (raw and 0b0000_1000_0000_0000) == 0b0000_1000_0000_0000

    /** Sets 6th MSB. */
    fun setZwjBit(): RefWidthInfo = RefWidthInfo(raw or 0b0000_0100_0000_0000)

    /** Has top bit set. */
    fun isEmojiPresentation(): Boolean =
        (raw and VARIATION_SELECTOR_16.raw) == VARIATION_SELECTOR_16.raw

    fun isZwjEmojiPresentation(): Boolean =
        (raw and 0b1011_0000_0000_0000) == 0b1001_0000_0000_0000

    /** Set top bit. */
    fun setEmojiPresentation(): RefWidthInfo =
        if ((raw and LIGATURE_TRANSPARENT_MASK_REF) == LIGATURE_TRANSPARENT_MASK_REF ||
            (raw and 0b1001_0000_0000_0000) == 0b0001_0000_0000_0000
        ) {
            RefWidthInfo(
                raw or (
                    VARIATION_SELECTOR_16.raw
                        and VARIATION_SELECTOR_15.raw.inv()
                        and VARIATION_SELECTOR_1_OR_2.raw.inv()
                ),
            )
        } else {
            VARIATION_SELECTOR_16
        }

    /** Clear top bit. */
    fun unsetEmojiPresentation(): RefWidthInfo =
        if ((raw and LIGATURE_TRANSPARENT_MASK_REF) == LIGATURE_TRANSPARENT_MASK_REF) {
            RefWidthInfo(raw and VARIATION_SELECTOR_16.raw.inv() and 0xFFFF)
        } else {
            DEFAULT
        }

    /** Has 2nd bit set. */
    fun isTextPresentation(): Boolean =
        (raw and VARIATION_SELECTOR_15.raw) == VARIATION_SELECTOR_15.raw

    /** Set 2nd bit. */
    fun setTextPresentation(): RefWidthInfo =
        if ((raw and LIGATURE_TRANSPARENT_MASK_REF) == LIGATURE_TRANSPARENT_MASK_REF) {
            RefWidthInfo(
                raw or (
                    VARIATION_SELECTOR_15.raw
                        and VARIATION_SELECTOR_16.raw.inv()
                        and VARIATION_SELECTOR_1_OR_2.raw.inv()
                ),
            )
        } else {
            RefWidthInfo(VARIATION_SELECTOR_15.raw)
        }

    /** Clear 2nd bit. */
    fun unsetTextPresentation(): RefWidthInfo =
        RefWidthInfo(raw and VARIATION_SELECTOR_15.raw.inv() and 0xFFFF)

    /** Has 7th bit set. */
    fun isVs12(): Boolean =
        (raw and VARIATION_SELECTOR_1_OR_2.raw) == VARIATION_SELECTOR_1_OR_2.raw

    /** Set 7th bit. */
    fun setVs12(): RefWidthInfo =
        if ((raw and LIGATURE_TRANSPARENT_MASK_REF) == LIGATURE_TRANSPARENT_MASK_REF) {
            RefWidthInfo(
                raw or (
                    VARIATION_SELECTOR_1_OR_2.raw
                        and VARIATION_SELECTOR_15.raw.inv()
                        and VARIATION_SELECTOR_16.raw.inv()
                ),
            )
        } else {
            RefWidthInfo(VARIATION_SELECTOR_1_OR_2.raw)
        }

    /** Clear 7th bit. */
    fun unsetVs12(): RefWidthInfo =
        RefWidthInfo(raw and VARIATION_SELECTOR_1_OR_2.raw.inv() and 0xFFFF)

    companion object {
        /** No special handling necessary. */
        val DEFAULT = RefWidthInfo(0)
        val LINE_FEED = RefWidthInfo(0b0000000000000001)
        val EMOJI_MODIFIER = RefWidthInfo(0b0000000000000010)
        val REGIONAL_INDICATOR = RefWidthInfo(0b0000000000000011)
        val SEVERAL_REGIONAL_INDICATOR = RefWidthInfo(0b0000000000000100)
        val EMOJI_PRESENTATION = RefWidthInfo(0b0000000000000101)
        val ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0001000000000110)
        val VS16_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b1001000000000110)
        val KEYCAP_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0001000000000111)
        val VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b1001000000000111)
        val REGIONAL_INDICATOR_ZWJ_PRESENTATION = RefWidthInfo(0b0000000000001001)
        val EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION = RefWidthInfo(0b0000000000001010)
        val ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION = RefWidthInfo(0b0000000000001011)
        val TAG_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000010000)
        val TAG_D1_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000010001)
        val TAG_D2_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000010010)
        val TAG_D3_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000010011)
        val TAG_A1_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011001)
        val TAG_A2_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011010)
        val TAG_A3_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011011)
        val TAG_A4_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011100)
        val TAG_A5_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011101)
        val TAG_A6_END_ZWJ_EMOJI_PRESENTATION = RefWidthInfo(0b0000000000011110)
        val KIRAT_RAI_VOWEL_SIGN_E = RefWidthInfo(0b0000000000100000)
        val KIRAT_RAI_VOWEL_SIGN_AI = RefWidthInfo(0b0000000000100001)
        val VARIATION_SELECTOR_1_OR_2 = RefWidthInfo(0b0000_0010_0000_0000)
        val VARIATION_SELECTOR_15 = RefWidthInfo(0b0100_0000_0000_0000)
        val VARIATION_SELECTOR_16 = RefWidthInfo(0b1000_0000_0000_0000)
        val JOINING_GROUP_ALEF = RefWidthInfo(0b0011000011111111)
        val COMBINING_LONG_SOLIDUS_OVERLAY = RefWidthInfo(0b0011110011111111)
        val SOLIDUS_OVERLAY_ALEF = RefWidthInfo(0b0011100011111111)
        val HEBREW_LETTER_LAMED = RefWidthInfo(0b0011100000000000)
        val ZWJ_HEBREW_LETTER_LAMED = RefWidthInfo(0b0011110000000000)
        val BUGINESE_LETTER_YA = RefWidthInfo(0b0011100000000001)
        val ZWJ_BUGINESE_LETTER_YA = RefWidthInfo(0b0011110000000001)
        val BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA = RefWidthInfo(0b0011110000000010)
        val TIFINAGH_CONSONANT = RefWidthInfo(0b0011100000000011)
        val ZWJ_TIFINAGH_CONSONANT = RefWidthInfo(0b0011110000000011)
        val TIFINAGH_JOINER_CONSONANT = RefWidthInfo(0b0011110000000100)
        val LISU_TONE_LETTER_MYA_NA_JEU = RefWidthInfo(0b0011110000000101)
        val OLD_TURKIC_LETTER_ORKHON_I = RefWidthInfo(0b0011100000000110)
        val ZWJ_OLD_TURKIC_LETTER_ORKHON_I = RefWidthInfo(0b0011110000000110)
        val KHMER_COENG_ELIGIBLE_LETTER = RefWidthInfo(0b0011110000000111)
    }
}

internal fun lookupWidthRef(c: Int): Pair<Int, RefWidthInfo> {
    val cp = c

    val t1Offset = WIDTH_ROOT[cp shr 13]

    // Each sub-table in WIDTH_MIDDLE is 7 bits, and each stored entry is a byte,
    // so each sub-table is 128 bytes in size.
    // (Sub-tables are selected using the computed offset from the previous table.)
    val t2Offset = WIDTH_MIDDLE[t1Offset][(cp shr 7) and 0x3F]

    // Each sub-table in WIDTH_LEAVES is 6 bits, but each stored entry is 2 bits.
    // This is accomplished by packing four stored entries into one byte.
    // So each sub-table is 2**(7-2) == 32 bytes in size.
    // Since this is the last table, each entry represents an encoded width.
    val packedWidths = WIDTH_LEAVES[t2Offset][(cp shr 2) and 0x1F]

    // Extract the packed width
    val width = (packedWidths shr (2 * (cp and 0b11))) and 0b11

    return if (width < 3) {
        Pair(width, RefWidthInfo.DEFAULT)
    } else {
        when (c) {
            0xA -> Pair(1, RefWidthInfo.LINE_FEED)
            0x5DC -> Pair(1, RefWidthInfo.HEBREW_LETTER_LAMED)
            in 0x622..0x882 -> Pair(1, RefWidthInfo.JOINING_GROUP_ALEF)
            in 0x1780..0x17AF -> Pair(1, RefWidthInfo.KHMER_COENG_ELIGIBLE_LETTER)
            0x17D8 -> Pair(3, RefWidthInfo.DEFAULT)
            0x1A10 -> Pair(1, RefWidthInfo.BUGINESE_LETTER_YA)
            in 0x2D31..0x2D6F -> Pair(1, RefWidthInfo.TIFINAGH_CONSONANT)
            in 0xA4FC..0xA4FD -> Pair(1, RefWidthInfo.LISU_TONE_LETTER_MYA_NA_JEU)
            0xFE01 -> Pair(0, RefWidthInfo.VARIATION_SELECTOR_1_OR_2)
            0xFE0E -> Pair(0, RefWidthInfo.VARIATION_SELECTOR_15)
            0xFE0F -> Pair(0, RefWidthInfo.VARIATION_SELECTOR_16)
            0x10C03 -> Pair(1, RefWidthInfo.OLD_TURKIC_LETTER_ORKHON_I)
            0x16D67 -> Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E)
            0x16D68 -> Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI)
            in 0x1F1E6..0x1F1FF -> Pair(1, RefWidthInfo.REGIONAL_INDICATOR)
            in 0x1F3FB..0x1F3FF -> Pair(2, RefWidthInfo.EMOJI_MODIFIER)
            else -> Pair(2, RefWidthInfo.EMOJI_PRESENTATION)
        }
    }
}

internal fun widthInStrRef(c: Int, nextInfoIn: RefWidthInfo): Pair<Int, RefWidthInfo> {
    var nextInfo = nextInfoIn
    if (nextInfo.isEmojiPresentation()) {
        if (startsEmojiPresentationSeq(c)) {
            val width = if (nextInfo.isZwjEmojiPresentation()) 0 else 2
            return Pair(width, RefWidthInfo.EMOJI_PRESENTATION)
        } else {
            nextInfo = nextInfo.unsetEmojiPresentation()
        }
    }
    if (c <= 0xA0) {
        return when (c) {
            0xA -> Pair(1, RefWidthInfo.LINE_FEED)
            0xD -> if (nextInfo == RefWidthInfo.LINE_FEED) Pair(0, RefWidthInfo.DEFAULT) else Pair(1, RefWidthInfo.DEFAULT)
            else -> Pair(1, RefWidthInfo.DEFAULT)
        }
    } else {
        // Fast path
        if (nextInfo != RefWidthInfo.DEFAULT) {
            if (c == 0xFE0F) {
                return Pair(0, nextInfo.setEmojiPresentation())
            }
            if (c == 0xFE01) {
                return Pair(0, nextInfo.setVs12())
            }
            if (c == 0xFE0E) {
                return Pair(0, nextInfo.setTextPresentation())
            }
            if (nextInfo.isTextPresentation()) {
                if (startsNonIdeographicTextPresentationSeq(c)) {
                    return Pair(1, RefWidthInfo.DEFAULT)
                } else {
                    nextInfo = nextInfo.unsetTextPresentation()
                }
            } else if (nextInfo.isVs12()) {
                if (c == 0x2018 || c == 0x2019 || c == 0x201C || c == 0x201D) {
                    return Pair(2, RefWidthInfo.DEFAULT)
                } else {
                    nextInfo = nextInfo.unsetVs12()
                }
            }
            if (nextInfo.isLigatureTransparent()) {
                if (c == 0x200D) {
                    return Pair(0, nextInfo.setZwjBit())
                } else if (isLigatureTransparent(c)) {
                    return Pair(0, nextInfo)
                }
            }

            // Arabic Lam-Alef ligature
            if (nextInfo == RefWidthInfo.JOINING_GROUP_ALEF &&
                (c == 0x644 || c in 0x6B5..0x6B8 || c == 0x76A || c == 0x8A6 || c == 0x8C7)
            ) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.JOINING_GROUP_ALEF && isTransparentZeroWidth(c)) {
                return Pair(0, RefWidthInfo.JOINING_GROUP_ALEF)
            }

            // Hebrew Alef-ZWJ-Lamed ligature
            if (nextInfo == RefWidthInfo.ZWJ_HEBREW_LETTER_LAMED && c == 0x05D0) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Khmer coeng signs
            if (nextInfo == RefWidthInfo.KHMER_COENG_ELIGIBLE_LETTER && c == 0x17D2) {
                return Pair(-1, RefWidthInfo.DEFAULT)
            }

            // Buginese <a, -i> ZWJ ya ligature
            if (nextInfo == RefWidthInfo.ZWJ_BUGINESE_LETTER_YA && c == 0x1A17) {
                return Pair(0, RefWidthInfo.BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA)
            }
            if (nextInfo == RefWidthInfo.BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA && c == 0x1A15) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Tifinagh bi-consonants
            if ((nextInfo == RefWidthInfo.TIFINAGH_CONSONANT || nextInfo == RefWidthInfo.ZWJ_TIFINAGH_CONSONANT) &&
                c == 0x2D7F
            ) {
                return Pair(1, RefWidthInfo.TIFINAGH_JOINER_CONSONANT)
            }
            if (nextInfo == RefWidthInfo.ZWJ_TIFINAGH_CONSONANT && (c in 0x2D31..0x2D65 || c == 0x2D6F)) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.TIFINAGH_JOINER_CONSONANT && (c in 0x2D31..0x2D65 || c == 0x2D6F)) {
                return Pair(-1, RefWidthInfo.DEFAULT)
            }

            // Lisu tone letter combinations
            if (nextInfo == RefWidthInfo.LISU_TONE_LETTER_MYA_NA_JEU && c in 0xA4F8..0xA4FB) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Old Turkic ligature
            if (nextInfo == RefWidthInfo.ZWJ_OLD_TURKIC_LETTER_ORKHON_I && c == 0x10C32) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            // Emoji modifier
            if (nextInfo == RefWidthInfo.EMOJI_MODIFIER && isEmojiModifierBase(c)) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }

            // Regional indicator
            if ((nextInfo == RefWidthInfo.REGIONAL_INDICATOR || nextInfo == RefWidthInfo.SEVERAL_REGIONAL_INDICATOR) &&
                c in 0x1F1E6..0x1F1FF
            ) {
                return Pair(1, RefWidthInfo.SEVERAL_REGIONAL_INDICATOR)
            }

            // ZWJ emoji
            if ((
                    nextInfo == RefWidthInfo.EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.SEVERAL_REGIONAL_INDICATOR ||
                        nextInfo == RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.EMOJI_MODIFIER
                ) &&
                c == 0x200D
            ) {
                return Pair(0, RefWidthInfo.ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c == 0x20E3) {
                return Pair(0, RefWidthInfo.KEYCAP_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.VS16_ZWJ_EMOJI_PRESENTATION && startsEmojiPresentationSeq(c)) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION &&
                (c in 0x30..0x39 || c == 0x23 || c == 0x2A)
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c in 0x1F1E6..0x1F1FF) {
                return Pair(1, RefWidthInfo.REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION
                ) &&
                c in 0x1F1E6..0x1F1FF
            ) {
                return Pair(-1, RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION && c in 0x1F1E6..0x1F1FF) {
                return Pair(3, RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c in 0x1F3FB..0x1F3FF) {
                return Pair(0, RefWidthInfo.EMOJI_MODIFIER)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c == 0xE007F) {
                return Pair(0, RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A6_END_ZWJ_EMOJI_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION
                ) &&
                c in 0xE0030..0xE0039
            ) {
                return Pair(0, RefWidthInfo.TAG_D1_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_D1_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0030..0xE0039) {
                return Pair(0, RefWidthInfo.TAG_D2_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_D2_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0030..0xE0039) {
                return Pair(0, RefWidthInfo.TAG_D3_END_ZWJ_EMOJI_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A6_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_D3_END_ZWJ_EMOJI_PRESENTATION
                ) &&
                c == 0x1F3F4
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION &&
                lookupWidthRef(c).second == RefWidthInfo.EMOJI_PRESENTATION
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }

            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D63) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D67) {
                return Pair(0, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D68) {
                return Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D69) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI && c == 0x16D63) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Fallback
        }

        val ret = lookupWidthRef(c)
        return Pair(ret.first, ret.second)
    }
}

internal fun lookupWidthCjkRef(c: Int): Pair<Int, RefWidthInfo> {
    val cp = c

    val t1Offset = WIDTH_ROOT_CJK[cp shr 13]

    val t2Offset = WIDTH_MIDDLE[t1Offset][(cp shr 7) and 0x3F]

    val packedWidths = WIDTH_LEAVES[t2Offset][(cp shr 2) and 0x1F]

    val width = (packedWidths shr (2 * (cp and 0b11))) and 0b11

    return if (width < 3) {
        Pair(width, RefWidthInfo.DEFAULT)
    } else {
        when (c) {
            0xA -> Pair(1, RefWidthInfo.LINE_FEED)
            0x338 -> Pair(0, RefWidthInfo.COMBINING_LONG_SOLIDUS_OVERLAY)
            0x5DC -> Pair(1, RefWidthInfo.HEBREW_LETTER_LAMED)
            in 0x622..0x882 -> Pair(1, RefWidthInfo.JOINING_GROUP_ALEF)
            in 0x1780..0x17AF -> Pair(1, RefWidthInfo.KHMER_COENG_ELIGIBLE_LETTER)
            0x17D8 -> Pair(3, RefWidthInfo.DEFAULT)
            0x1A10 -> Pair(1, RefWidthInfo.BUGINESE_LETTER_YA)
            in 0x2D31..0x2D6F -> Pair(1, RefWidthInfo.TIFINAGH_CONSONANT)
            in 0xA4FC..0xA4FD -> Pair(1, RefWidthInfo.LISU_TONE_LETTER_MYA_NA_JEU)
            in 0xFE00..0xFE02 -> Pair(0, RefWidthInfo.VARIATION_SELECTOR_1_OR_2)
            0xFE0F -> Pair(0, RefWidthInfo.VARIATION_SELECTOR_16)
            0x10C03 -> Pair(1, RefWidthInfo.OLD_TURKIC_LETTER_ORKHON_I)
            0x16D67 -> Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E)
            0x16D68 -> Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI)
            in 0x1F1E6..0x1F1FF -> Pair(1, RefWidthInfo.REGIONAL_INDICATOR)
            in 0x1F3FB..0x1F3FF -> Pair(2, RefWidthInfo.EMOJI_MODIFIER)
            else -> Pair(2, RefWidthInfo.EMOJI_PRESENTATION)
        }
    }
}

internal fun widthInStrCjkRef(c: Int, nextInfoIn: RefWidthInfo): Pair<Int, RefWidthInfo> {
    var nextInfo = nextInfoIn
    if (nextInfo.isEmojiPresentation()) {
        if (startsEmojiPresentationSeq(c)) {
            val width = if (nextInfo.isZwjEmojiPresentation()) 0 else 2
            return Pair(width, RefWidthInfo.EMOJI_PRESENTATION)
        } else {
            nextInfo = nextInfo.unsetEmojiPresentation()
        }
    }
    if ((
            nextInfo == RefWidthInfo.COMBINING_LONG_SOLIDUS_OVERLAY ||
                nextInfo == RefWidthInfo.SOLIDUS_OVERLAY_ALEF
        ) &&
        (c == '<'.code || c == '='.code || c == '>'.code)
    ) {
        return Pair(2, RefWidthInfo.DEFAULT)
    }
    if (c <= 0xA0) {
        return when (c) {
            0xA -> Pair(1, RefWidthInfo.LINE_FEED)
            0xD -> if (nextInfo == RefWidthInfo.LINE_FEED) Pair(0, RefWidthInfo.DEFAULT) else Pair(1, RefWidthInfo.DEFAULT)
            else -> Pair(1, RefWidthInfo.DEFAULT)
        }
    } else {
        // Fast path
        if (nextInfo != RefWidthInfo.DEFAULT) {
            if (c == 0xFE0F) {
                return Pair(0, nextInfo.setEmojiPresentation())
            }
            if (c == 0xFE00) {
                return Pair(0, nextInfo.setVs12())
            }
            if (nextInfo.isVs12()) {
                if (c == 0x2018 || c == 0x2019 || c == 0x201C || c == 0x201D) {
                    return Pair(1, RefWidthInfo.DEFAULT)
                } else {
                    nextInfo = nextInfo.unsetVs12()
                }
            }
            if (nextInfo.isLigatureTransparent()) {
                if (c == 0x200D) {
                    return Pair(0, nextInfo.setZwjBit())
                } else if (isLigatureTransparent(c)) {
                    return Pair(0, nextInfo)
                }
            }

            if (nextInfo == RefWidthInfo.COMBINING_LONG_SOLIDUS_OVERLAY && isSolidusTransparent(c)) {
                return Pair(
                    lookupWidthCjkRef(c).first,
                    RefWidthInfo.COMBINING_LONG_SOLIDUS_OVERLAY,
                )
            }
            if (nextInfo == RefWidthInfo.JOINING_GROUP_ALEF && c == 0x0338) {
                return Pair(0, RefWidthInfo.SOLIDUS_OVERLAY_ALEF)
            }
            // Arabic Lam-Alef ligature
            if ((
                    nextInfo == RefWidthInfo.JOINING_GROUP_ALEF ||
                        nextInfo == RefWidthInfo.SOLIDUS_OVERLAY_ALEF
                ) &&
                (c == 0x644 || c in 0x6B5..0x6B8 || c == 0x76A || c == 0x8A6 || c == 0x8C7)
            ) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.JOINING_GROUP_ALEF && isTransparentZeroWidth(c)) {
                return Pair(0, RefWidthInfo.JOINING_GROUP_ALEF)
            }

            // Hebrew Alef-ZWJ-Lamed ligature
            if (nextInfo == RefWidthInfo.ZWJ_HEBREW_LETTER_LAMED && c == 0x05D0) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Khmer coeng signs
            if (nextInfo == RefWidthInfo.KHMER_COENG_ELIGIBLE_LETTER && c == 0x17D2) {
                return Pair(-1, RefWidthInfo.DEFAULT)
            }

            // Buginese <a, -i> ZWJ ya ligature
            if (nextInfo == RefWidthInfo.ZWJ_BUGINESE_LETTER_YA && c == 0x1A17) {
                return Pair(0, RefWidthInfo.BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA)
            }
            if (nextInfo == RefWidthInfo.BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA && c == 0x1A15) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Tifinagh bi-consonants
            if ((nextInfo == RefWidthInfo.TIFINAGH_CONSONANT || nextInfo == RefWidthInfo.ZWJ_TIFINAGH_CONSONANT) &&
                c == 0x2D7F
            ) {
                return Pair(1, RefWidthInfo.TIFINAGH_JOINER_CONSONANT)
            }
            if (nextInfo == RefWidthInfo.ZWJ_TIFINAGH_CONSONANT && (c in 0x2D31..0x2D65 || c == 0x2D6F)) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.TIFINAGH_JOINER_CONSONANT && (c in 0x2D31..0x2D65 || c == 0x2D6F)) {
                return Pair(-1, RefWidthInfo.DEFAULT)
            }

            // Lisu tone letter combinations
            if (nextInfo == RefWidthInfo.LISU_TONE_LETTER_MYA_NA_JEU && c in 0xA4F8..0xA4FB) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Old Turkic ligature
            if (nextInfo == RefWidthInfo.ZWJ_OLD_TURKIC_LETTER_ORKHON_I && c == 0x10C32) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            // Emoji modifier
            if (nextInfo == RefWidthInfo.EMOJI_MODIFIER && isEmojiModifierBase(c)) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }

            // Regional indicator
            if ((nextInfo == RefWidthInfo.REGIONAL_INDICATOR || nextInfo == RefWidthInfo.SEVERAL_REGIONAL_INDICATOR) &&
                c in 0x1F1E6..0x1F1FF
            ) {
                return Pair(1, RefWidthInfo.SEVERAL_REGIONAL_INDICATOR)
            }

            // ZWJ emoji
            if ((
                    nextInfo == RefWidthInfo.EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.SEVERAL_REGIONAL_INDICATOR ||
                        nextInfo == RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.EMOJI_MODIFIER
                ) &&
                c == 0x200D
            ) {
                return Pair(0, RefWidthInfo.ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c == 0x20E3) {
                return Pair(0, RefWidthInfo.KEYCAP_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.VS16_ZWJ_EMOJI_PRESENTATION && startsEmojiPresentationSeq(c)) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION &&
                (c in 0x30..0x39 || c == 0x23 || c == 0x2A)
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c in 0x1F1E6..0x1F1FF) {
                return Pair(1, RefWidthInfo.REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.REGIONAL_INDICATOR_ZWJ_PRESENTATION ||
                        nextInfo == RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION
                ) &&
                c in 0x1F1E6..0x1F1FF
            ) {
                return Pair(-1, RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION && c in 0x1F1E6..0x1F1FF) {
                return Pair(3, RefWidthInfo.ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c in 0x1F3FB..0x1F3FF) {
                return Pair(0, RefWidthInfo.EMOJI_MODIFIER)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION && c == 0xE007F) {
                return Pair(0, RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0061..0xE007A) {
                return Pair(0, RefWidthInfo.TAG_A6_END_ZWJ_EMOJI_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.TAG_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A1_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A2_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION
                ) &&
                c in 0xE0030..0xE0039
            ) {
                return Pair(0, RefWidthInfo.TAG_D1_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_D1_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0030..0xE0039) {
                return Pair(0, RefWidthInfo.TAG_D2_END_ZWJ_EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.TAG_D2_END_ZWJ_EMOJI_PRESENTATION && c in 0xE0030..0xE0039) {
                return Pair(0, RefWidthInfo.TAG_D3_END_ZWJ_EMOJI_PRESENTATION)
            }
            if ((
                    nextInfo == RefWidthInfo.TAG_A3_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A4_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A5_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_A6_END_ZWJ_EMOJI_PRESENTATION ||
                        nextInfo == RefWidthInfo.TAG_D3_END_ZWJ_EMOJI_PRESENTATION
                ) &&
                c == 0x1F3F4
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }
            if (nextInfo == RefWidthInfo.ZWJ_EMOJI_PRESENTATION &&
                lookupWidthCjkRef(c).second == RefWidthInfo.EMOJI_PRESENTATION
            ) {
                return Pair(0, RefWidthInfo.EMOJI_PRESENTATION)
            }

            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D63) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D67) {
                return Pair(0, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D68) {
                return Pair(1, RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_E && c == 0x16D69) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }
            if (nextInfo == RefWidthInfo.KIRAT_RAI_VOWEL_SIGN_AI && c == 0x16D63) {
                return Pair(0, RefWidthInfo.DEFAULT)
            }

            // Fallback
        }

        val ret = lookupWidthCjkRef(c)
        return Pair(ret.first, ret.second)
    }
}


internal fun strWidthRef(s: String): Int {
    var sum = 0
    var nextInfo = RefWidthInfo.DEFAULT
    var i = s.length
    while (i > 0) {
        i--
        val low = s[i]
        val cp: Int =
            if (low.isLowSurrogate() && i > 0 && s[i - 1].isHighSurrogate()) {
                i--
                ((s[i].code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
            } else {
                low.code
            }
        val (add, info) = widthInStrRef(cp, nextInfo)
        sum += add
        nextInfo = info
    }
    return sum
}

internal fun strWidthCjkRef(s: String): Int {
    var sum = 0
    var nextInfo = RefWidthInfo.DEFAULT
    var i = s.length
    while (i > 0) {
        i--
        val low = s[i]
        val cp: Int =
            if (low.isLowSurrogate() && i > 0 && s[i - 1].isHighSurrogate()) {
                i--
                ((s[i].code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
            } else {
                low.code
            }
        val (add, info) = widthInStrCjkRef(cp, nextInfo)
        sum += add
        nextInfo = info
    }
    return sum
}



internal fun refWidthInStrPacked(cp: Int, stateRaw: Int): Int {
    val (w, info) = widthInStrRef(cp, RefWidthInfo(stateRaw))
    return (info.raw shl 4) or (w and 0xF)
}
internal fun refWidthInStrCjkPacked(cp: Int, stateRaw: Int): Int {
    val (w, info) = widthInStrCjkRef(cp, RefWidthInfo(stateRaw))
    return (info.raw shl 4) or (w and 0xF)
}


private const val LIGATURE_TRANSPARENT_MASK_REF: Int = 0b0010_0000_0000_0000
