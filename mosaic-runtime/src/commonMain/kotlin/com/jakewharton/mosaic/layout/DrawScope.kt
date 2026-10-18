/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE")

package com.jakewharton.mosaic.layout

import com.jakewharton.mosaic.TextPixel
import com.jakewharton.mosaic.TextSurface
import com.jakewharton.mosaic.UnspecifiedCodePoint
import com.jakewharton.mosaic.isUnspecifiedCodePoint
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.LineMeasure
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.getLocalRawSpanStyles
import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.grapheme.graphemeClusterWidth
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.UnderlineStyle
import com.jakewharton.mosaic.ui.isSpecifiedColor
import com.jakewharton.mosaic.ui.isSpecifiedTextStyle
import com.jakewharton.mosaic.ui.isSpecifiedUnderlineStyle
import com.jakewharton.mosaic.ui.isUnspecifiedColor
import com.jakewharton.mosaic.ui.isUnspecifiedTextStyle
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import de.cketti.codepoints.appendCodePoint
import kotlin.math.max

public interface DrawScope {
	public val width: Int
	public val height: Int

	private val size: IntSize get() = IntSize(width, height)

	/**
	 * Draws a rectangle with the given offset and size. If no offset from the top left is provided,
	 * it is drawn starting from the origin of the current translation. If no size is provided,
	 * the size of the current environment is used.
	 *
	 * @param char Char to be applied to the rectangle
	 * @param foreground Foreground color to be applied to the rectangle
	 * @param background Background color to be applied to the rectangle
	 * @param textStyle Text style color to be applied to the rectangle
	 * @param topLeft Offset from the local origin of 0, 0 relative to the current translation
	 * @param size Dimensions of the rectangle to draw
	 * @param drawStyle Whether or not the rectangle is stroked or filled in
	 */
	public fun drawRect(
		char: Char,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		topLeft: IntOffset = IntOffset.Zero,
		size: IntSize = this.size.offsetSize(topLeft),
		drawStyle: DrawStyle = DrawStyle.Fill,
	)

	/**
	 * Draws a rectangle with the given offset and size. If no offset from the top left is provided,
	 * it is drawn starting from the origin of the current translation. If no size is provided,
	 * the size of the current environment is used.
	 *
	 * @param codePoint Code point to be applied to the rectangle
	 * @param foreground Foreground color to be applied to the rectangle
	 * @param background Background color to be applied to the rectangle
	 * @param textStyle Text style color to be applied to the rectangle
	 * @param topLeft Offset from the local origin of 0, 0 relative to the current translation
	 * @param size Dimensions of the rectangle to draw
	 * @param drawStyle Whether or not the rectangle is stroked or filled in
	 */
	public fun drawRect(
		codePoint: Int = UnspecifiedCodePoint,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		topLeft: IntOffset = IntOffset.Zero,
		size: IntSize = this.size.offsetSize(topLeft),
		drawStyle: DrawStyle = DrawStyle.Fill,
	)

	public fun drawText(
		row: Int,
		column: Int,
		string: String,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
		underlineColor: Color = Color.Unspecified,
	)

	public fun drawText(
		row: Int,
		column: Int,
		string: AnnotatedString,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
		underlineColor: Color = Color.Unspecified,
	)

	/**
	 * Helper method to offset the provided size with the offset in box width and height
	 */
	private fun IntSize.offsetSize(offset: IntOffset): IntSize = IntSize(this.width - offset.x, this.height - offset.y)
}

internal open class TextCanvasDrawScope(
	private val canvas: TextSurface,
	override val width: Int,
	override val height: Int,
) : DrawScope {
	override fun drawRect(
		char: Char,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		topLeft: IntOffset,
		size: IntSize,
		drawStyle: DrawStyle,
	) {
		drawRect(char.code, foreground, background, textStyle, topLeft, size, drawStyle)
	}

	override fun drawRect(
		codePoint: Int,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		topLeft: IntOffset,
		size: IntSize,
		drawStyle: DrawStyle,
	) {
		if (codePoint.isUnspecifiedCodePoint &&
			foreground.isUnspecifiedColor &&
			background.isUnspecifiedColor && (
				textStyle.isUnspecifiedTextStyle ||
					size.width <= 0 ||
					size.height <= 0 ||
					topLeft.x >= width ||
					topLeft.y >= height ||
					topLeft.x + size.width < 0 ||
					topLeft.y + size.height < 0
				)
		) {
			// exit: rectangle with the specified parameters cannot be seen
			return
		}

		val text = if (codePoint.isUnspecifiedCodePoint) " " else codePoint.toCodePointText()
		when (drawStyle) {
			DrawStyle.Fill -> drawSolidRect(text, foreground, background, textStyle, topLeft, size)

			is DrawStyle.Stroke -> {
				val strokeWidth = max(1, drawStyle.width)
				if (strokeWidth * 2 >= size.width || strokeWidth * 2 >= size.height) {
					// fast path: stroke width is large, it turns out a full fill
					drawSolidRect(text, foreground, background, textStyle, topLeft, size)
					return
				}

				// left line
				drawSolidRect(
					text,
					foreground,
					background,
					textStyle,
					topLeft,
					IntSize(strokeWidth, size.height),
				)
				// top line
				drawSolidRect(
					text,
					foreground,
					background,
					textStyle,
					IntOffset(topLeft.x + strokeWidth, topLeft.y),
					IntSize(size.width - strokeWidth * 2, strokeWidth),
				)
				// right line
				drawSolidRect(
					text,
					foreground,
					background,
					textStyle,
					IntOffset(topLeft.x + size.width - strokeWidth, topLeft.y),
					IntSize(strokeWidth, size.height),
				)
				// bottom line
				drawSolidRect(
					text,
					foreground,
					background,
					textStyle,
					IntOffset(topLeft.x + strokeWidth, topLeft.y + size.height - strokeWidth),
					IntSize(size.width - strokeWidth * 2, strokeWidth),
				)
			}
		}
	}

	private inline fun drawSolidRect(
		text: String,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		topLeft: IntOffset,
		size: IntSize,
	) {
		// The scope's own bounds may sit off the screen canvas (a fixed-size parent with content
		// overhanging it), so clip in local coordinates against the canvas edges, translated back
		// from this node's placement, exactly as drawText's cluster writer does. Clamping to the
		// scope size alone still faults once the node is placed past the canvas boundary.
		val xStart = topLeft.x.coerceAtLeast(-canvas.translationX).coerceAtLeast(0)
		val xEnd = (topLeft.x + size.width).coerceAtMost(canvas.width - canvas.translationX).coerceAtMost(width)
		val yStart = topLeft.y.coerceAtLeast(-canvas.translationY).coerceAtLeast(0)
		val yEnd = (topLeft.y + size.height).coerceAtMost(canvas.height - canvas.translationY).coerceAtMost(height)
		for (y in yStart until yEnd) {
			for (x in xStart until xEnd) {
				drawTextPixel(x, y, text, foreground, background, textStyle)
			}
		}
	}

	override fun drawText(
		row: Int,
		column: Int,
		string: String,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		underlineStyle: UnderlineStyle,
		underlineColor: Color,
	) {
		drawText(row, column, string, foreground, background, textStyle, underlineStyle, underlineColor, null)
	}

	override fun drawText(
		row: Int,
		column: Int,
		string: AnnotatedString,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		underlineStyle: UnderlineStyle,
		underlineColor: Color,
	) {
		drawText(
			row, column, string.text, foreground, background, textStyle, underlineStyle, underlineColor,
			spanStylesProvider = { start, end ->
				string.getLocalRawSpanStyles(start, end)
			},
		)
	}

	internal fun drawMeasuredText(
		row: Int,
		column: Int,
		string: String,
		measure: LineMeasure,
		spanStylesProvider: ((start: Int, end: Int) -> List<SpanStyle>)?,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
		underlineColor: Color = Color.Unspecified,
	) {
		drawText(row, column, string, foreground, background, textStyle, underlineStyle, underlineColor, spanStylesProvider, measure)
	}

	private fun drawText(
		row: Int,
		column: Int,
		text: String,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		underlineStyle: UnderlineStyle,
		underlineColor: Color,
		spanStylesProvider: ((start: Int, end: Int) -> List<SpanStyle>)?,
		measure: LineMeasure? = null,
	) {
		var characterColumn = column
		fun writeCluster(
			clusterStart: Int,
			clusterEnd: Int,
			clusterWidth: Int,
		) {
			// Measure and draw at extended-grapheme-cluster granularity so a synthesized cluster
			// (ZWJ emoji family, keycap, flag, Hangul jamo run) is written as one unit into its
			// head cell, with only its continuation cells reserved. This mirrors measurement, so
			// layout and drawing can never disagree about where a cluster starts or how wide it is.
			val w = clusterWidth

			// Zero-width clusters (control characters, and format codepoints like ZWSP/BOM) occupy
			// no columns: skip them entirely so their bytes never reach a cell or the ANSI stream.
			if (w <= 0) return

			// The scope's own [width]/[height] are this node's bounds, but the underlying canvas is
			// the whole screen translated by this node's placement. A cluster that lands outside the
			// canvas (content overhanging a fixed-size layout) must be skipped, not written: the
			// surface getter is strict. Continuation cells past the right edge are guarded below.
			val canvasWidth = canvas.width
			val canvasHeight = canvas.height
			val canvasRow = canvas.translationY + row
			val canvasColumn = canvas.translationX + characterColumn

			// A cluster is drawn only when every cell of it lands within the canvas and, when a
			// clip is active, inside that clip. Whatever the outcome the character column must
			// still advance by [w], otherwise clusters that follow a clipped-away cluster would all
			// pile up on the same column and the rest of the line goes blank.
			val fullyVisible = canvasColumn >= 0 && canvasRow >= 0 &&
				canvasColumn + w <= canvasWidth && canvasRow < canvasHeight &&
				(!canvas.clipActive ||
					(canvasColumn >= canvas.clipLeft && canvasColumn + w <= canvas.clipRight &&
						canvasRow >= canvas.clipTop && canvasRow < canvas.clipBottom))
			if (fullyVisible) {
				// The surface getter expects this scope's own (local) coordinates and applies the
				// node placement translation itself, so write with [row]/[characterColumn], not the
				// absolute positions computed for the bounds check above.
				val character = canvas[row, characterColumn]
				character.updateTextPixel(text, clusterStart, clusterEnd, w, foreground, background, textStyle, underlineStyle, underlineColor)
				spanStylesProvider?.invoke(clusterStart, clusterEnd)?.forEach {
					character.updateTextPixel(character.textSource, character.textStart, character.textEnd, character.width, it.color, it.background, it.textStyle, it.underlineStyle, it.underlineColor)
				}

				// Mark continuation cells for wide clusters (e.g. CJK, fullwidth emoji). The head
				// is fully visible so its continuations are too (rect clip window).
				if (w > 1) {
					for (offset in 1 until w) {
						canvas[row, characterColumn + offset].markAsContinuation()
					}
				}
			}

			characterColumn += w
		}
		if (measure != null) {
			val starts = measure.clusterStarts
			for (i in starts.indices) {
				val start = starts[i]
				val endValue = if (i + 1 < starts.size) starts[i + 1] else text.length
				writeCluster(start, endValue, measure.clusterWidths[i])
			}
		} else {
			text.forEachGraphemeClusterRange { start, end ->
				writeCluster(start, end, text.graphemeClusterWidth(start, end))
			}
		}
	}

	private inline fun drawTextPixel(
		x: Int,
		y: Int,
		text: String,
		foreground: Color = Color.Unspecified,
		background: Color = Color.Unspecified,
		textStyle: TextStyle = TextStyle.Unspecified,
		underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
		underlineColor: Color = Color.Unspecified,
	) {
		if (canvas.clipActive) {
			val canvasColumn = canvas.translationX + x
			val canvasRow = canvas.translationY + y
			if (canvasColumn < canvas.clipLeft || canvasColumn >= canvas.clipRight ||
				canvasRow < canvas.clipTop || canvasRow >= canvas.clipBottom
			) {
				return
			}
		}
		canvas[y, x].updateTextPixel(text, 0, text.length, 1, foreground, background, textStyle, underlineStyle, underlineColor)
	}

	private inline fun TextPixel.updateTextPixel(
		textSource: String,
		textStart: Int,
		textEnd: Int,
		width: Int,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		underlineStyle: UnderlineStyle,
		underlineColor: Color,
	) {
		this.textSource = textSource
		this.textStart = textStart
		this.textEnd = textEnd
		this.width = width
		isPlaceholder = false
		if (foreground.isSpecifiedColor) {
			this.foreground = foreground
		}
		if (background.isSpecifiedColor) {
			this.background = background
		}
		if (textStyle.isSpecifiedTextStyle) {
			this.textStyle = textStyle
		}
		if (underlineStyle.isSpecifiedUnderlineStyle) {
			this.underlineStyle = underlineStyle
		}
		if (underlineColor.isSpecifiedColor) {
			this.underlineColor = underlineColor
		}
	}
	private fun Int.toCodePointText(): String = buildString {
		appendCodePoint(this@toCodePointText)
	}
}
