// Unicode character width computation (UAX #11).
// Width data is generated from the Unicode Character Database; see scripts/unicode.py.
package com.jakewharton.mosaic.text.unicodewidth

/**
 * Methods for determining displayed width of Unicode strings.
 */
public interface UnicodeWidthStr {
	/**
	 * Returns the string's displayed width in columns.
	 */
	public fun width(): Int

	/**
	 * Returns the string's displayed width in columns for CJK contexts.
	 */
	public fun widthCjk(): Int
}

/**
 * Returns the string's displayed width in columns.
 *
 * Translated from upstream `UnicodeWidthStr::width` (`impl UnicodeWidthStr for str`
 * in `src/lib.rs`). Rust's `&str` is a UTF-8 byte slice; Kotlin's [String] is a
 * sequence of UTF-16 code units. The width algorithm operates on Unicode scalar
 * values, so the implementation iterates code points (decoding surrogate pairs)
 * regardless of the underlying string encoding.
 *
 * This function treats characters in the Ambiguous category according
 * to [Unicode Standard Annex #11](http://www.unicode.org/reports/tr11/)
 * as 1 column wide. This is consistent with the recommendations for
 * non-CJK contexts, or when the context cannot be reliably determined.
 */
public fun String.unicodeWidth(): Int = strWidth(this)

/**
 * Returns the string's displayed width in columns.
 *
 * This package-level form delegates to [unicodeWidth] for the table-backed
 * string lookup.
 */
public fun width(text: String): Int = text.unicodeWidth()

/**
 * Returns the string's displayed width in columns.
 *
 * See [unicodeWidth] for notes on the receiver type.
 *
 * This function treats characters in the Ambiguous category according
 * to [Unicode Standard Annex #11](http://www.unicode.org/reports/tr11/)
 * as 2 column wide. This is consistent with the recommendations for
 * CJK contexts.
 */
public fun String.unicodeWidthCjk(): Int = strWidthCjk(this)

/**
 * Returns the string's displayed width in columns for CJK contexts.
 *
 * This package-level form delegates to [unicodeWidthCjk] for the table-backed
 * CJK string lookup.
 */
public fun widthCjk(text: String): Int = text.unicodeWidthCjk()
