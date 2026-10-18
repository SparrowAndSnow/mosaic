package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Stable
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize

/**
 * Invokes [onPositioned] after this content has been measured and placed, reporting its top-left
 * corner relative to the composition root (global/screen coordinates) and its measured size.
 *
 * The callback runs during the placement pass and fires only when the reported position or size
 * changes relative to the previous placement of this node, so a layout that does not move is not
 * reported again on every frame. This is the idiomatic way for a node to learn where it landed
 * (for example to anchor a popup, a cursor, or a tooltip to an element) without rewriting the
 * layout of its parent.
 *
 * @param onPositioned Called with the node's top-left position in root coordinates and its size,
 *   whenever either changes after a placement.
 */
@Stable
public fun Modifier.onGloballyPositioned(
	onPositioned: (position: IntOffset, size: IntSize) -> Unit,
): Modifier = this.then(OnGloballyPositionedModifier(onPositioned))

private class OnGloballyPositionedModifier(
	private val onPositioned: (position: IntOffset, size: IntSize) -> Unit,
) : LayoutModifier {
	// Last reported placement, used to fire only on an actual change. Starts impossible so the
	// first placement always reports. The modifier instance persists across frames that do not
	// recompose, so an unmoved layout does not re-invoke the callback on every frame.
	private var lastX = Int.MIN_VALUE
	private var lastY = Int.MIN_VALUE
	private var lastWidth = Int.MIN_VALUE
	private var lastHeight = Int.MIN_VALUE

	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val placeable = measurable.measure(constraints)
		return layout(placeable.width, placeable.height) {
			// Placement runs top-down through the cascade, so by the time this block executes the
			// scope's x/y already include every ancestor placement: they are screen coordinates.
			val width = placeable.width
			val height = placeable.height
			if (x != lastX || y != lastY || width != lastWidth || height != lastHeight) {
				lastX = x
				lastY = y
				lastWidth = width
				lastHeight = height
				onPositioned(IntOffset(x, y), IntSize(width, height))
			}
		}
	}

	override fun toString(): String = "OnGloballyPositioned()"
}
