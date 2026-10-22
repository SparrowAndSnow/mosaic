@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.OverlayLayerModifier
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize

/**
 * A layer drawn above the base content of an [OverlayHost].
 *
 * By default the popup is non-modal: the layer measures to its [content] size and is placed inside
 * the base area at [alignment] plus [offset] (cell offset after alignment). Later popups in the
 * overlay slot draw and hit-test above earlier ones. Mouse events inside the popup are consumed:
 * content handlers run first, and anything the content does not handle is swallowed by the layer
 * instead of falling through to the base. Mouse events outside the popup pass through to layers
 * below and finally to the base. Key and paste events are offered to the popup first and fall
 * through to the base when unhandled, so non-modal popups do not steal application shortcuts.
 *
 * With [modal] = true the layer fills the base area and becomes an input barrier: unhandled key,
 * paste, and mouse events stop at the popup, and the base loses focus/Tab traversal. [content] is
 * still placed at [alignment] + [offset] inside the base area. [backdrop] controls what is painted
 * behind the content; [OverlayBackdrop.None] leaves the base visible and [OverlayBackdrop.Solid]
 * erases it.
 *
 * [onDismissRequest], when non-null, is invoked by Escape after the content has had a chance to
 * consume it. Non-modal popups do not move focus; a focusable inside the popup needs an
 * application [FocusRequester] or a click.
 *
 * Must be composed inside the overlay slot of [OverlayHost]; as a direct child of that slot it is
 * one layer.
 *
 * @param alignment Alignment of the content inside the base area. For a non-modal popup this also
 *   positions the layer itself.
 * @param offset Additional cell offset applied after [alignment]. May be negative.
 * @param modifier Modifier applied to the layer.
 * @param modal Whether the popup fills the base and gates input.
 * @param backdrop What is painted behind the content in modal mode.
 * @param onDismissRequest Invoked when Escape is not consumed by the content, or null to ignore it.
 * @param content Popup content, stacked vertically in a [Column].
 */
@Suppress("ktlint:compose:param-order-check") // Signature follows the overlay design.
@Composable
public fun Popup(
	alignment: Alignment = Alignment.TopStart,
	offset: IntOffset = IntOffset.Zero,
	modifier: Modifier = Modifier,
	modal: Boolean = false,
	backdrop: OverlayBackdrop = OverlayBackdrop.None,
	onDismissRequest: (() -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val measurePolicy = remember(modal, alignment, offset) {
		PopupMeasurePolicy(modal, alignment, offset)
	}
	Layout(
		content = {
			if (modal) {
				Box(
					Modifier
						.fillMaxSize()
						.overlayBackdrop(backdrop),
				)
			}
			Box(
				Modifier
					.onMouseEvent { true }
					.clickable { },
			) {
				Column {
					content()
				}
			}
		},
		modifier = Modifier
			// The layer consumes any mouse event that reaches it after its content declined it, so
			// clicks inside the popup cannot fall through to the base.
			.onMouseEvent { true }
			// The cross-node pointer pass is independent of mouse dispatch. A no-op click target on
			// the layer makes that pass select the popup over content behind it, including presses
			// on non-interactive popup cells and (in modal mode) outside the popup content.
			.clickable { }
			.then(modifier)
			.then(
				if (onDismissRequest != null) {
					Modifier.onKeyEvent { event: KeyEvent ->
						if (event.key == "Escape") {
							onDismissRequest()
							true
						} else {
							false
						}
					}
				} else {
					Modifier
				},
			)
			.then(OverlayLayerModifier(alignment, offset, modal = modal, fillParent = modal)),
		debugInfo = { "Popup(alignment=$alignment, offset=$offset, modal=$modal, backdrop=$backdrop)" },
		measurePolicy = measurePolicy,
	)
}

/**
 * Measures a non-modal popup to its content size, or a modal popup to the base area while placing
 * the content at its requested alignment plus offset.
 */
private class PopupMeasurePolicy(
	private val modal: Boolean,
	private val alignment: Alignment,
	private val offset: IntOffset,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
		if (!modal) {
			val content = measurables.singleOrNull()?.measure(contentConstraints)
			return layout(content?.width ?: 0, content?.height ?: 0) {
				content?.let { placeable: Placeable -> placeable.place(0, 0) }
			}
		}

		val width = constraints.minWidth
		val height = constraints.minHeight
		val backdrop = measurables.getOrNull(0)
		val content = measurables.getOrNull(1)
		val backdropPlaceable = backdrop?.measure(Constraints.fixed(width, height))
		val contentPlaceable = content?.measure(contentConstraints)
		return layout(width, height) {
			backdropPlaceable?.let { placeable: Placeable -> placeable.place(0, 0) }
			if (contentPlaceable != null) {
				val position = alignment.align(
					IntSize(contentPlaceable.width, contentPlaceable.height),
					IntSize(width, height),
				)
				contentPlaceable.place(position.x + offset.x, position.y + offset.y)
			}
		}
	}
}
