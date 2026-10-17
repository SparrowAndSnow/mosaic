package com.jakewharton.mosaic

import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.TextStyle.Companion.Bold
import com.jakewharton.mosaic.ui.TextStyle.Companion.Dim
import com.jakewharton.mosaic.ui.TextStyle.Companion.Invert
import com.jakewharton.mosaic.ui.TextStyle.Companion.Italic
import com.jakewharton.mosaic.ui.TextStyle.Companion.Strikethrough
import com.jakewharton.mosaic.ui.UnderlineStyle
import com.jakewharton.mosaic.ui.isEmptyTextStyle
import com.jakewharton.mosaic.ui.isNotEmptyTextStyle
import com.jakewharton.mosaic.ui.isSpecifiedColor
import com.jakewharton.mosaic.ui.isSpecifiedUnderlineStyle
import com.jakewharton.mosaic.ui.isUnspecifiedColor
import com.jakewharton.mosaic.ui.isUnspecifiedUnderlineStyle

private val blankPixel = TextPixel()

public interface TextCanvas {
	public val height: Int
	public val width: Int

	// TODO Hey! These don't go here...
	public fun render(ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean): String
	public fun appendRowTo(appendable: Appendable, row: Int, ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean)
}

internal class TextSurface(
	override val width: Int,
	override val height: Int,
) : TextCanvas {
	var translationX = 0
	var translationY = 0

	internal val cells = Array(width * height) { TextPixel() }

	operator fun get(row: Int, column: Int): TextPixel {
		val x = translationX + column
		val y = row + translationY
		check(x in 0 until width)
		check(y in 0 until height)
		return cells[y * width + x]
	}

	/**
	 * Resets every cell to its blank default so this surface can be reused for the next frame's
	 * draw without allocating a fresh cell array. Size changes allocate a new surface instead.
	 */
	internal fun reset() {
		val cells = cells
		var i = 0
		while (i < cells.size) {
			cells[i].reset()
			i++
		}
		translationX = 0
		translationY = 0
	}

	override fun appendRowTo(appendable: Appendable, row: Int, ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean) {
		val rowStart = row * width
		var to = rowStart + width
		while (to > rowStart) {
			val lastIndex = to - 1
			if (cells[lastIndex].isEmpty()) {
				to = lastIndex
			} else {
				break
			}
		}
		appendRowRange(appendable, rowStart, to, rowStart + width, ansiLevel, supportsKittyUnderlines)
	}

	/**
	 * Appends the cells at absolute indices [from, to) to [appendable]. Unlike [appendRowTo], the
	 * range is rendered verbatim: empty cells in the middle of the range are emitted as spaces so
	 * previously-drawn content at those columns is overwritten, and styles are emitted relative to
	 * the default state so a slice can be patched onto a row independently of the surrounding cells.
	 */
	internal fun appendRowRange(
		appendable: Appendable,
		from: Int,
		to: Int,
		rowEnd: Int,
		ansiLevel: AnsiLevel,
		supportsKittyUnderlines: Boolean,
	) {
		// Reused heap allocation for building ANSI attributes inside the loop.
		val attributes = mutableListOf<String>()

		var lastPixel = blankPixel
		var columnIndex = from
		while (columnIndex < to) {
			val pixel = cells[columnIndex]

			if (pixel.isPlaceholder) {
				columnIndex++
				continue
			}

			if (ansiLevel != AnsiLevel.NONE) {
				if (pixel.foreground != lastPixel.foreground) {
					attributes.addColor(
						pixel.foreground,
						ansiLevel,
						ansiFgColorSelector,
						ansiFgColorReset,
						ansiFgColorOffset,
					)
				}
				if (pixel.background != lastPixel.background) {
					attributes.addColor(
						pixel.background,
						ansiLevel,
						ansiBgColorSelector,
						ansiBgColorReset,
						ansiBgColorOffset,
					)
				}

				fun maybeToggleStyle(style: TextStyle, on: String, off: String) {
					if (style in pixel.textStyle) {
						if (style !in lastPixel.textStyle) {
							attributes += on
						}
					} else if (style in lastPixel.textStyle) {
						attributes += off
					}
				}
				if (pixel.textStyle != lastPixel.textStyle) {
					maybeToggleStyle(Bold, "1", "22")
					maybeToggleStyle(Dim, "2", "22")
					maybeToggleStyle(Italic, "3", "23")
					maybeToggleStyle(Invert, "7", "27")
					maybeToggleStyle(Strikethrough, "9", "29")
				}
				if (pixel.underlineStyle != lastPixel.underlineStyle) {
					attributes += when (pixel.underlineStyle) {
						UnderlineStyle.Unspecified, UnderlineStyle.None -> "24"
						UnderlineStyle.Double if (supportsKittyUnderlines) -> "4:2"
						UnderlineStyle.Curly if (supportsKittyUnderlines) -> "4:3"
						UnderlineStyle.Dotted if (supportsKittyUnderlines) -> "4:4"
						UnderlineStyle.Dashed if (supportsKittyUnderlines) -> "4:5"
						else -> "4"
					}
				}
				if (pixel.underlineColor != lastPixel.underlineColor) {
					attributes.addColor(
						pixel.underlineColor,
						ansiLevel,
						ansiUnderlineColorSelector,
						ansiUnderlineColorReset,
						ansiUnderlineColorOffset,
					)
				}
				if (attributes.isNotEmpty()) {
					appendable.append(CSI)
					attributes.forEachIndexed { index, element ->
						if (index > 0) {
							appendable.append(ansiSeparator)
						}
						appendable.append(element)
					}
					appendable.append(ansiClosingCharacter)
					attributes.clear() // This list is reused!
				}
			}

			// A wide cluster must never run past its row's right edge: the extra columns would make
			// the terminal wrap. Write styled blanks for the columns that fit instead, so the row
			// can never scroll and any reserved cells are cleared.
			if (pixel.width > 1 && columnIndex + pixel.width > rowEnd) {
				repeat(rowEnd - columnIndex) { appendable.append(' ') }
				columnIndex = rowEnd
				lastPixel = pixel
				continue
			}

			appendable.append(pixel.text)
			lastPixel = pixel
			columnIndex++
		}

		if (
			ansiLevel != AnsiLevel.NONE &&
			(
				lastPixel.background.isSpecifiedColor ||
					lastPixel.foreground.isSpecifiedColor ||
					lastPixel.textStyle.isNotEmptyTextStyle ||
					lastPixel.underlineColor.isSpecifiedColor ||
					lastPixel.underlineStyle.isSpecifiedUnderlineStyle
				)
		) {
			appendable.append(ansiReset)
			appendable.append(ansiClosingCharacter)
		}
	}

	/**
	 * Copies every cell's field values into [dest] in place. [dest] must be pre-sized to this
	 * surface's cell count, and its elements must already exist so no allocation is required.
	 */
	internal fun copyCellsTo(dest: Array<TextPixel>) {
		require(dest.size == cells.size)
		for (i in cells.indices) {
			dest[i].copyFrom(cells[i])
		}
	}

	private fun MutableList<String>.addColor(
		color: Color,
		ansiLevel: AnsiLevel,
		select: Int,
		reset: Int,
		offset: Int,
	) {
		if (color.isUnspecifiedColor) {
			add(reset.toString())
			return
		}
		when (ansiLevel) {
			AnsiLevel.NONE -> add(reset.toString())

			AnsiLevel.ANSI16 -> {
				val ansi16Code = color.toAnsi16Code()
				if (ansi16Code == ansiFgColorReset || ansi16Code == ansiBgColorReset) {
					add(reset.toString())
				} else {
					add((ansi16Code + offset).toString())
				}
			}

			AnsiLevel.ANSI256 -> {
				add(select.toString())
				add(ansiSelectorColor256)
				add(color.toAnsi256Code().toString())
			}

			AnsiLevel.TRUECOLOR -> {
				add(select.toString())
				add(ansiSelectorColorRgb)
				add(color.redInt.toString())
				add(color.greenInt.toString())
				add(color.blueInt.toString())
			}
		}
	}

	override fun render(ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean): String = buildString {
		if (height > 0) {
			for (rowIndex in 0 until height) {
				appendRowTo(this, rowIndex, ansiLevel, supportsKittyUnderlines)
				append("\n")
			}
			// Remove trailing newline.
			setLength(length - 1)
		}
	}
}

internal class TextPixel(
	initialText: String = " ",
) {
	/**
	 * The extended grapheme cluster occupying this cell's column: everything rendered at this
	 * cell's horizontal position (a base character with its combining marks, a ZWJ emoji family,
	 * a flag, a keycap, ...). A single cell therefore holds an entire cluster rather than one
	 * code point. Empty/duplicate clusters use the lone space so renderers emit something.
	 */
	var text: String = initialText

	/**
	 * The number of terminal columns this cluster occupies: `1` for ordinary cells, `2` or more
	 * for wide clusters, and `0` for zero-width clusters such as isolated combining marks.
	 */
	var width: Int = 1

	/** Whether this cell is covered by the continuation of a wider cluster to its immediate left. */
	var isPlaceholder: Boolean = false

	var background: Color = Color.Unspecified
	var foreground: Color = Color.Unspecified
	var textStyle: TextStyle = TextStyle.Empty
	var underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified
	var underlineColor: Color = Color.Unspecified

	/** Marks this cell as covered by the continuation of a wider cluster drawn to its left. */
	fun markAsContinuation() {
		text = ""
		width = 0
		isPlaceholder = true
	}

	/** Resets this cell back to its default blank state so it can be reused by the next frame. */
	internal fun reset() {
		text = " "
		width = 1
		isPlaceholder = false
		background = Color.Unspecified
		foreground = Color.Unspecified
		textStyle = TextStyle.Empty
		underlineStyle = UnderlineStyle.Unspecified
		underlineColor = Color.Unspecified
	}

	/**
	 * Copies every field of [other] (text, width, placeholder state, and the style fields) into this
	 * cell in place so retained buffers can be updated without allocating new objects.
	 */
	internal fun copyFrom(other: TextPixel) {
		text = other.text
		width = other.width
		isPlaceholder = other.isPlaceholder
		background = other.background
		foreground = other.foreground
		textStyle = other.textStyle
		underlineStyle = other.underlineStyle
		underlineColor = other.underlineColor
	}

	fun isEmpty(): Boolean {
		return (text.isEmpty() || text == " ") &&
			background.isUnspecifiedColor &&
			foreground.isUnspecifiedColor &&
			textStyle.isEmptyTextStyle &&
			underlineStyle.isUnspecifiedUnderlineStyle &&
			underlineColor.isUnspecifiedColor
	}

	override fun toString() = buildString {
		append("TextPixel(\"")
		append(text)
		append("\"")
		if (isPlaceholder) {
			append(" placeholder")
		} else if (width != 1) {
			append(" w=$width")
		}
		if (background.isSpecifiedColor) {
			append(" bg=")
			append(background)
		}
		if (foreground.isSpecifiedColor) {
			append(" fg=")
			append(foreground)
		}
		// TODO style
		append(')')
	}
}
