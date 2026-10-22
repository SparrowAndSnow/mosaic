@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset

/**
 * A non-modal layer drawn above the base content of an [OverlayHost].
 *
 * The layer measures to its [content] size and is placed inside the base area at [alignment] plus
 * [offset], where the offset is expressed in terminal cells and applies after alignment. Later
 * popups in the overlay slot draw and hit-test above earlier ones.
 *
 * Mouse events inside the popup are consumed: content handlers run first, and anything the content
 * does not handle is swallowed by the layer instead of falling through to the base. Mouse events
 * outside the popup pass through to layers below and finally to the base. The popup is not modal:
 * keyboard and paste dispatch are unchanged (they follow the normal tree order) and the popup does
 * not take or restore focus. Backdrops, Escape dismissal, and modal input gating are deferred to a
 * later phase.
 *
 * Must be composed inside the overlay slot of [OverlayHost]; as a direct child of that slot it is
 * one layer.
 *
 * @param alignment Alignment of the popup inside the base area.
 * @param offset Additional cell offset applied after [alignment]. May be negative.
 * @param modifier Modifier applied to the popup layer.
 * @param content Popup content, stacked vertically in a [Column].
 */
@Suppress("ktlint:compose:param-order-check") // Signature follows the overlay design.
@Composable
public fun Popup(
	alignment: Alignment = Alignment.TopStart,
	offset: IntOffset = IntOffset.Zero,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Layout(
		content = {
			Column {
				content()
			}
		},
		modifier = Modifier
			// The layer consumes any mouse event that reaches it after its content declined it, so
			// clicks inside the popup cannot fall through to the base.
			.onMouseEvent { true }
			// The cross-node pointer pass (hover selection, click capture) is independent of mouse
			// dispatch. A no-op click target on the layer makes that pass select the popup over any
			// clickable content behind it, including for presses on non-interactive popup cells.
			.clickable { }
			.then(modifier)
			.then(OverlayLayerModifier(alignment, offset)),
		debugInfo = { "Popup(alignment=$alignment, offset=$offset)" },
		measurePolicy = PopupMeasurePolicy,
	)
}

/**
 * Measures the popup to its content size. The overlay slot aligns and places the layer.
 */
private object PopupMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
		val placeables = arrayOfNulls<Placeable>(measurables.size)
		var width = 0
		var height = 0
		measurables.forEachIndexed { index, measurable ->
			val placeable = measurable.measure(contentConstraints)
			placeables[index] = placeable
			if (placeable.width > width) width = placeable.width
			if (placeable.height > height) height = placeable.height
		}
		return layout(width, height) {
			placeables.forEach { placeable ->
				placeable as Placeable
				placeable.place(0, 0)
			}
		}
	}
}
