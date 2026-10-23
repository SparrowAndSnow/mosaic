@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Layout
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import com.jakewharton.mosaic.ui.unit.constrainHeight
import com.jakewharton.mosaic.ui.unit.constrainWidth
import com.jakewharton.mosaic.ui.unit.offset

private const val CornerTopLeft = '┌'
private const val CornerTopRight = '┐'
private const val CornerBottomLeft = '└'
private const val CornerBottomRight = '┘'
private const val HorizontalEdge = '─'
private const val VerticalEdge = '│'

/**
 * Draws a box-drawing border [width] cells thick around the wrapped content. The content is
 * inset by [width] on every side (the border occupies the outer ring), matching Compose's border
 * semantics of insetting content within the modifier's bounds. A zero [width] renders no border.
 *
 * @param width thickness of the border in cells.
 * @param color color of the border; [Color.Unspecified] uses the terminal default foreground.
 */
@Stable
public fun Modifier.border(
	width: Int = 1,
	color: Color = Color.Unspecified,
): Modifier = this.then(BorderModifier(width, color))

private class BorderModifier(
	private val width: Int,
	private val color: Color,
) : LayoutModifier,
	DrawModifier {

	init {
		require(width >= 0) { "Border width must be non-negative" }
	}

	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val total = width * 2
		val placeable = measurable.measure(constraints.offset(-total, -total))
		val boxWidth = constraints.constrainWidth(placeable.width + total)
		val boxHeight = constraints.constrainHeight(placeable.height + total)
		return layout(boxWidth, boxHeight) {
			placeable.place(width, width)
		}
	}

	override fun ContentDrawScope.draw() {
		// The modifier's own [width] (the border thickness) is shadowed by DrawScope's node
		// `width`, so read the thickness explicitly; boxW/boxH are the laid-out node bounds.
		val b = this@BorderModifier.width
		val boxW = this.width
		val boxH = this.height
		if (b > 0 && boxW > 0 && boxH > 0) {
			// Corners, then the edge runs that span the full box (thick borders fill several rows
			// or columns of the edge character between the corners).
			drawRect(CornerTopLeft, foreground = color, topLeft = IntOffset(0, 0), size = IntSize(1, 1))
			drawRect(CornerTopRight, foreground = color, topLeft = IntOffset(boxW - 1, 0), size = IntSize(1, 1))
			drawRect(CornerBottomLeft, foreground = color, topLeft = IntOffset(0, boxH - 1), size = IntSize(1, 1))
			drawRect(CornerBottomRight, foreground = color, topLeft = IntOffset(boxW - 1, boxH - 1), size = IntSize(1, 1))
			val run = boxW - 2
			if (run > 0) {
				drawRect(HorizontalEdge, foreground = color, topLeft = IntOffset(1, 0), size = IntSize(run, b))
				drawRect(HorizontalEdge, foreground = color, topLeft = IntOffset(1, boxH - b), size = IntSize(run, b))
			}
			val span = boxH - 2
			if (span > 0) {
				drawRect(VerticalEdge, foreground = color, topLeft = IntOffset(0, 1), size = IntSize(b, span))
				drawRect(VerticalEdge, foreground = color, topLeft = IntOffset(boxW - b, 1), size = IntSize(b, span))
			}
		}
		drawContent()
	}

	override fun equals(other: Any?): Boolean {
		return other is BorderModifier && other.width == width && other.color == color
	}

	override fun hashCode(): Int = 31 * width + color.hashCode()

	override fun toString(): String = "Border(width=$width, color=$color)"
}

/**
 * A thin horizontal rule spanning the full available width. The rule is indented from each side by
 * [startIndent] and [endIndent] cells and is [thickness] cells tall, matching Compose's divider
 * semantics (a colored bar the width of its container). When the incoming width is unbounded the
 * rule collapses to zero width; give the containing layout a bounded width (for example via
 * [Modifier.fillMaxWidth] on an ancestor [com.jakewharton.mosaic.ui.Column]).
 *
 * @param thickness height of the divider in cells.
 * @param color color of the divider; [Color.Unspecified] uses the terminal default foreground.
 * @param startIndent number of cells to skip on the leading edge.
 * @param endIndent number of cells to skip on the trailing edge.
 * @param modifier modifiers to apply to the divider layout.
 */
@Composable
public fun HorizontalDivider(
	thickness: Int = 1,
	color: Color = Color.Unspecified,
	startIndent: Int = 0,
	endIndent: Int = 0,
	modifier: Modifier = Modifier,
) {
	require(thickness >= 0) { "HorizontalDivider thickness must be non-negative" }
	require(startIndent >= 0) { "startIndent must be non-negative" }
	require(endIndent >= 0) { "endIndent must be non-negative" }
	val policy = remember(thickness) { HorizontalDividerMeasurePolicy(thickness) }
	Layout(
		content = EmptyDividerContent,
		modifier = modifier.drawBehind {
			val drawWidth = width - startIndent - endIndent
			val drawHeight = thickness.coerceIn(0, height)
			if (drawWidth > 0 && drawHeight > 0) {
				drawRect(
					HorizontalEdge,
					foreground = color,
					topLeft = IntOffset(startIndent, 0),
					size = IntSize(drawWidth, drawHeight),
				)
			}
		},
		debugInfo = { "HorizontalDivider(thickness=$thickness, color=$color, startIndent=$startIndent, endIndent=$endIndent)" },
		measurePolicy = policy,
	)
}

/**
 * A thin vertical rule spanning the full available height. The rule is indented from each end by
 * [startIndent] and [endIndent] cells and is [thickness] cells wide, matching Compose's divider
 * semantics (a colored bar the height of its container). When the incoming height is unbounded the
 * rule collapses to zero height; give the containing layout a bounded height (for example a
 * [com.jakewharton.mosaic.ui.Row] with a fixed [Modifier.height]).
 *
 * @param thickness width of the divider in cells.
 * @param color color of the divider; [Color.Unspecified] uses the terminal default foreground.
 * @param startIndent number of cells to skip at the top edge.
 * @param endIndent number of cells to skip at the bottom edge.
 * @param modifier modifiers to apply to the divider layout.
 */
@Composable
public fun VerticalDivider(
	thickness: Int = 1,
	color: Color = Color.Unspecified,
	startIndent: Int = 0,
	endIndent: Int = 0,
	modifier: Modifier = Modifier,
) {
	require(thickness >= 0) { "VerticalDivider thickness must be non-negative" }
	require(startIndent >= 0) { "startIndent must be non-negative" }
	require(endIndent >= 0) { "endIndent must be non-negative" }
	val policy = remember(thickness) { VerticalDividerMeasurePolicy(thickness) }
	Layout(
		content = EmptyDividerContent,
		modifier = modifier.drawBehind {
			val drawWidth = thickness.coerceIn(0, width)
			val drawHeight = height - startIndent - endIndent
			if (drawWidth > 0 && drawHeight > 0) {
				drawRect(
					VerticalEdge,
					foreground = color,
					topLeft = IntOffset(0, startIndent),
					size = IntSize(drawWidth, drawHeight),
				)
			}
		},
		debugInfo = { "VerticalDivider(thickness=$thickness, color=$color, startIndent=$startIndent, endIndent=$endIndent)" },
		measurePolicy = policy,
	)
}

private val EmptyDividerContent: @Composable () -> Unit = {}

private class HorizontalDividerMeasurePolicy(
	private val thickness: Int,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
		val height = thickness.coerceIn(constraints.minHeight, constraints.maxHeight)
		return layout(width, height) {}
	}
}

private class VerticalDividerMeasurePolicy(
	private val thickness: Int,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = thickness.coerceIn(constraints.minWidth, constraints.maxWidth)
		val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
		return layout(width, height) {}
	}
}
