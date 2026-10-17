@file:JvmName("Text")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.text.AnnotatedStringTextLayout
import com.jakewharton.mosaic.text.StringTextLayout
import com.jakewharton.mosaic.text.TextLayout
import com.jakewharton.mosaic.text.getLocalRawSpanStyles
import com.jakewharton.mosaic.ui.unit.Constraints
import kotlin.jvm.JvmName
import kotlin.math.max

@Composable
@MosaicComposable
public fun Text(
	value: String,
	modifier: Modifier = Modifier,
	softWrap: Boolean = false,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	textAlign: Alignment.Horizontal = Alignment.Start,
	scroll: Int = 0,
	color: Color = Color.Unspecified,
	background: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
	underlineColor: Color = Color.Unspecified,
) {
	val layout = remember { StringTextLayout() }
	layout.value = value

	Layout(
		debugInfo = {
			"""Text("$value")"""
		},
		measurePolicy = {
			val alignmentWidth = it.alignmentWidthOrNone
			layout.measure(
				maxWidth = if (softWrap) it.maxWidth else Constraints.Infinity,
				maxLines = maxLines,
				overflow = overflow,
				alignmentWidth = alignmentWidth,
				scroll = scroll,
			)
			layout(layout.alignedBoxWidth(textAlign), layout.height)
		},
		modifier = modifier.drawBehind {
			val measuredScope = this as? TextCanvasDrawScope
			layout.lineMeasures.forEachIndexed { row, measure ->
				val line = layout.lines[row]
				val column = layout.alignedColumn(textAlign) { measure.totalWidth }
				if (measuredScope != null) {
					measuredScope.drawMeasuredText(row, column, line, measure, null, color, background, textStyle, underlineStyle, underlineColor)
				} else {
					drawText(row, column, line, color, background, textStyle, underlineStyle, underlineColor)
				}
			}
		},
	)
}

@Composable
@MosaicComposable
public fun Text(
	value: AnnotatedString,
	modifier: Modifier = Modifier,
	softWrap: Boolean = false,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	textAlign: Alignment.Horizontal = Alignment.Start,
	scroll: Int = 0,
	color: Color = Color.Unspecified,
	background: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
	underlineColor: Color = Color.Unspecified,
) {
	val layout = remember { AnnotatedStringTextLayout() }
	layout.value = value

	Layout(
		debugInfo = {
			"""Text("$value")"""
		},
		measurePolicy = {
			val alignmentWidth = it.alignmentWidthOrNone
			layout.measure(
				maxWidth = if (softWrap) it.maxWidth else Constraints.Infinity,
				maxLines = maxLines,
				overflow = overflow,
				alignmentWidth = alignmentWidth,
				scroll = scroll,
			)
			layout(layout.alignedBoxWidth(textAlign), layout.height)
		},
		modifier = modifier.drawBehind {
			val measuredScope = this as? TextCanvasDrawScope
			layout.lineMeasures.forEachIndexed { row, measure ->
				val line = layout.lines[row]
				val column = layout.alignedColumn(textAlign) { measure.totalWidth }
				if (measuredScope != null) {
					measuredScope.drawMeasuredText(row, column, line.text, measure, { start, end ->
						line.getLocalRawSpanStyles(start, end)
					}, color, background, textStyle, underlineStyle, underlineColor)
				} else {
					drawText(row, column, line, color, background, textStyle, underlineStyle, underlineColor)
				}
			}
		},
	)
}

private val Constraints.alignmentWidthOrNone: Int
	get() = if (maxWidth == Constraints.Infinity) Constraints.Infinity else maxWidth

/**
 * The width of the box to place this text in: the aligned width when a bounded width is available
 * and non-start alignment is requested, otherwise the text's own width so default behavior is
 * unchanged.
 */
private fun TextLayout<*>.alignedBoxWidth(textAlign: Alignment.Horizontal): Int {
	if (textAlign == Alignment.Start || alignmentWidth == Constraints.Infinity) {
		return width
	}
	return max(width, alignmentWidth)
}

/** The column at which to draw a line of [lineWidth] columns within this text's box. */
private fun TextLayout<*>.alignedColumn(textAlign: Alignment.Horizontal, lineWidth: () -> Int): Int {
	if (textAlign == Alignment.Start || alignmentWidth == Constraints.Infinity) {
		return 0
	}
	return textAlign.align(lineWidth(), alignedBoxWidth(textAlign)).coerceAtLeast(0)
}
