// Vendored from unicode-width-kotlin (KotlinMania). port-lint: source lib.rs
// Copyright 2012-2025 The Rust Project Developers. See the COPYRIGHT
// file at the top-level directory of this distribution and at
// http://rust-lang.org/COPYRIGHT.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
package com.jakewharton.mosaic.text.unicodewidth

internal interface Sealed

/**
 * Methods for determining displayed width of Unicode characters.
 */
public interface UnicodeWidthChar {
    /**
     * Returns the character's displayed width in columns, or `null` if the
     * character is a control character.
     */
    public fun width(): Int?

    /**
     * Returns the character's displayed width in columns for CJK contexts, or
     * `null` if the character is a control character.
     */
    public fun widthCjk(): Int?
}

/**
 * Returns the code point's displayed width in columns, or `null` if the
 * code point is a control character.
 *
 * Translated from upstream `UnicodeWidthChar::width` (`impl UnicodeWidthChar for char`
 * in `src/lib.rs`). Rust's `char` is a Unicode scalar value (a 21-bit code point in the
 * range U+0000..=U+10FFFF, excluding surrogates); Kotlin's `Char` is a 16-bit UTF-16
 * code unit and cannot represent supplementary plane code points on its own. The
 * receiver is therefore [Int], interpreted as a Unicode scalar value, so callers can
 * pass any code point in the same range Rust accepts.
 *
 * This function treats characters in the Ambiguous category according
 * to [Unicode Standard Annex #11](http://www.unicode.org/reports/tr11/)
 * as 1 column wide. This is consistent with the recommendations for non-CJK
 * contexts, or when the context cannot be reliably determined.
 */
public fun Int.unicodeWidth(): Int? = singleCharWidth(this)

/**
 * Returns the code point's displayed width in columns, or `null` if the
 * code point is a control character.
 *
 * This package-level form delegates to [unicodeWidth] for the table-backed
 * code point lookup.
 */
public fun width(codePoint: Int): Int? = codePoint.unicodeWidth()

/**
 * Returns the code point's displayed width in columns, or `null` if the
 * code point is a control character.
 *
 * The receiver is [Int], interpreted as a Unicode scalar value, for the same
 * reason described on [unicodeWidth].
 *
 * This function treats characters in the Ambiguous category according
 * to [Unicode Standard Annex #11](http://www.unicode.org/reports/tr11/)
 * as 2 columns wide. This is consistent with the recommendations for
 * CJK contexts.
 */
public fun Int.unicodeWidthCjk(): Int? = singleCharWidthCjk(this)

/**
 * Returns the code point's displayed width in columns for CJK contexts, or
 * `null` if the code point is a control character.
 *
 * This package-level form delegates to [unicodeWidthCjk] for the table-backed
 * CJK lookup.
 */
public fun widthCjk(codePoint: Int): Int? = codePoint.unicodeWidthCjk()
